package org.prorefactor.proparse.antlr4;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.prorefactor.core.ABLNodeType;
import org.prorefactor.core.IConstants;
import org.prorefactor.core.JPNode;
import org.prorefactor.core.nodetypes.BlockNode;
import org.prorefactor.core.nodetypes.FieldRefNode;
import org.prorefactor.core.nodetypes.RecordNameNode;
import org.prorefactor.core.schema.IField;
import org.prorefactor.core.schema.IIndex;
import org.prorefactor.core.schema.ITable;
import org.prorefactor.core.schema.Index;
import org.prorefactor.proparse.ParserSupport;
import org.prorefactor.proparse.antlr4.Proparse.*;
import org.prorefactor.refactor.RefactorSession;
import org.prorefactor.treeparser.Block;
import org.prorefactor.treeparser.BufferScope;
import org.prorefactor.treeparser.ContextQualifier;
import org.prorefactor.treeparser.DataType;
import org.prorefactor.treeparser.FieldLookupResult;
import org.prorefactor.treeparser.Primative;
import org.prorefactor.treeparser.SymbolFactory;
import org.prorefactor.treeparser.TableNameResolution;
import org.prorefactor.treeparser.TreeParserRootSymbolScope;
import org.prorefactor.treeparser.TreeParserSymbolScope;
import org.prorefactor.treeparser.symbols.Event;
import org.prorefactor.treeparser.symbols.FieldBuffer;
import org.prorefactor.treeparser.symbols.ISymbol;
import org.prorefactor.treeparser.symbols.Routine;
import org.prorefactor.treeparser.symbols.Symbol;
import org.prorefactor.treeparser.symbols.TableBuffer;
import org.prorefactor.treeparser.symbols.Variable;
import org.prorefactor.treeparser.symbols.widgets.Browse;
import org.prorefactor.treeparser01.FrameStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeParser extends ProparseBaseListener {
  private static final Logger LOG = LoggerFactory.getLogger(TreeParser.class);

  private final ParserSupport support;
  private final RefactorSession refSession;
  private final TreeParserRootSymbolScope rootScope;

  private Block currentBlock;
  private TreeParserSymbolScope currentScope;
  private Routine currentRoutine;
  private Routine rootRoutine;
  /**
   * The symbol last, or currently being, defined. Needed when we have complex syntax like DEFINE id ... LIKE, where we
   * want to track the LIKE but it's not in the same grammar production as the DEFINE.
   */
  private ISymbol currSymbol;

  private TableBuffer lastTableReferenced;
  private TableBuffer prevTableReferenced;
  private FrameStack frameStack = new FrameStack();

  private TableBuffer currDefTable;
  private Index currDefIndex;
  // LIKE tables management for index copy
  private boolean currDefTableUseIndex = false;
  private ITable currDefTableLike = null;

  // This tree parser's stack. I think it is best to keep the stack
  // in the tree parser grammar for visibility sake, rather than hide
  // it in the support class. If we move grammar and actions around
  // within this .g, the effect on the stack should be highly visible.
  // Deque implementation has to support null elements
  private Deque<Symbol> stack = new LinkedList<>();

  /*
   * Note that blockStack is *only* valid for determining the current block - the stack itself cannot be used for
   * determining a block's parent, buffer scopes, etc. That logic is found within the Block class. Conversely, we cannot
   * use Block.parent to find the current block when we close out a block. That is because a scope's root block parent
   * is always the program block, but a programmer may code a scope into a non-root block... which we need to make
   * current again once done inside the scope.
   */
  private List<Block> blockStack = new ArrayList<>();
  private Map<String, TreeParserSymbolScope> funcForwards = new HashMap<>();
  private ParseTreeProperty<ContextQualifier> contextQualifiers = new ParseTreeProperty<>();
  private ParseTreeProperty<TableNameResolution> nameResolution = new ParseTreeProperty<>();

  // Temporary work-around
  private boolean inDefineEvent = false;

  public TreeParser(ParserSupport support, RefactorSession session) {
    this.support = support;
    
    this.refSession = session;
    this.rootScope = new TreeParserRootSymbolScope(refSession);

    currentScope = rootScope;

  }

  private Block popBlock() {
    blockStack.remove(blockStack.size() - 1);
    return blockStack.get(blockStack.size() - 1);
  }

  private Block pushBlock(Block block) {
    blockStack.add(block);
    return block;
  }

  /** Action to take at various RECORD_NAME nodes. */
  private void recordNameNode(RecordNameNode recordNode, ContextQualifier contextQualifier) {
    LOG.trace("Entering recordNameNode {} {}", recordNode, contextQualifier);

    recordNode.attrSet(IConstants.CONTEXT_QUALIFIER, contextQualifier.toString());
    TableBuffer buffer = null;
    switch (contextQualifier) {
      case INIT:
      case INITWEAK:
      case REF:
      case REFUP:
      case UPDATING:
      case BUFFERSYMBOL:
        buffer = currentScope.getBufferSymbol(recordNode.getText());
        break;
      case SYMBOL:
        buffer = currentScope.lookupTableOrBufferSymbol(recordNode.getText());
        break;
      case TEMPTABLESYMBOL:
        buffer = currentScope.lookupTempTable(recordNode.getText());
        break;
      case SCHEMATABLESYMBOL:
        ITable table = refSession.getSchema().lookupTable(recordNode.getText());
        if (table != null)
          buffer = currentScope.getUnnamedBuffer(table);
        break;
      default:
        assert false;
    }
    recordNodeSymbol(recordNode, buffer); // Does checks, sets attributes.
    recordNode.setTableBuffer(buffer);
    switch (contextQualifier) {
      case INIT:
      case REF:
      case REFUP:
      case UPDATING:
        recordNode.setBufferScope(currentBlock.getBufferForReference(buffer));
        break;
      case INITWEAK:
        recordNode.setBufferScope(currentBlock.addWeakBufferScope(buffer));
        break;
      default:
        break;
    }
    buffer.noteReference(contextQualifier);
  }

  /** For a RECORD_NAME node, do checks and assignments for the TableBuffer. */
  private void recordNodeSymbol(RecordNameNode node, TableBuffer buffer) {
    String nodeText = node.getText();
    if (buffer == null) {
      throw new RuntimeException("Could not resolve table '" + nodeText + "'" + "" + node.getFileIndex() + node.getLine()+ node.getColumn());
    }
    ITable table = buffer.getTable();
    prevTableReferenced = lastTableReferenced;
    lastTableReferenced = buffer;

    // For an unnamed buffer, determine if it's abbreviated.
    // Note that named buffers, temp and work table names cannot be abbreviated.
    if (buffer.isDefault() && table.getStoretype() == IConstants.ST_DBTABLE) {
      String[] nameParts = nodeText.split("\\.");
      int tableNameLen = nameParts[nameParts.length - 1].length();
      if (table.getName().length() > tableNameLen)
        node.attrSet(IConstants.ABBREVIATED, 1);
    }
  }

  @Override
  public void enterProgram(ProgramContext ctx) {
    LOG.info("enterProgram");

    JPNode rootAST = support.getNode(ctx);
    BlockNode blockNode = (BlockNode) rootAST;
    
    currentBlock = (Block) pushBlock(new Block(rootScope, blockNode));
    rootScope.setRootBlock(currentBlock);
    blockNode.setBlock(currentBlock);
    // FIXME unit.setRootScope(rootScope);
    
    Routine r = new Routine("", rootScope, rootScope);
    r.setProgressType(ABLNodeType.PROGRAM_ROOT);
    r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);

    rootScope.add(r);
    currentRoutine = r;
    rootRoutine = r;
  }

  @Override
  public void exitProgram(ProgramContext ctx) {
    LOG.info("exitProgram");
  }

  @Override
  public void enterBlock_for(Block_forContext ctx) {
    // TODO To be verified...
    for (RecordContext record : ctx.record()) {
      RecordNameNode node = (RecordNameNode) support.getNode(record);
      recordNameNode(node, ContextQualifier.BUFFERSYMBOL);
      currentBlock.addStrongBufferScope(node);
    }
  }

  @Override
  public void enterBlock_opt_iterator(Block_opt_iteratorContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.REFUP);
  }

  @Override
  public void enterBlock_preselect(Block_preselectContext ctx) {
    contextQualifiers.put(ctx.for_record_spec(), ContextQualifier.INITWEAK);
  }
  
  @Override
  public void enterPseudfn(PseudfnContext ctx) {
    if ((ctx.PUTBITS() != null) || (ctx.PUTBYTE() != null) /* and so on */) {
      contextQualifiers.put(ctx.funargs().expression(0), ContextQualifier.UPDATING);
      // A compléter
    }
  }

  @Override
  public void enterFunargs(FunargsContext ctx) {
    for (ExpressionContext exp : ctx.expression()) {
      noteReference(support.getNode(ctx), contextQualifiers.get(exp));
    }
  }

  @Override
  public void enterRecordfunc(RecordfuncContext ctx) {
    recordNameNode((RecordNameNode) support.getNode(ctx.record()), ContextQualifier.REF);
  }

  @Override
  public void enterParameterBufferRecord(ParameterBufferRecordContext ctx) {
    recordNameNode((RecordNameNode) support.getNode(ctx.record()), ContextQualifier.INIT);
    // TODO Check that not used anymore
    // paramProgressType(BUFFER);
    // action.paramSymbol(#bt);
    // action.paramEnd();
  }

  @Override
  public void enterParameterOther(ParameterOtherContext ctx) {
    // TODO Auto-generated method stub
    super.enterParameterOther(ctx);
  }

  @Override
  public void enterParameter_dataset_options(Parameter_dataset_optionsContext ctx) {
    // TODO Check that not used anymore
  }

  @Override
  public void enterFilenameorvalue(FilenameorvalueContext ctx) {
    if (ctx.valueexpression() != null) {
      // TODO Check that not used anymore
    } else if (ctx.filename() != null) {
      // TODO Check that not used anymore
    }
  }

  @Override
  public void enterExprt2Field(Exprt2FieldContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.REF);
  }

  @Override
  public void enterWidattrExprt2(WidattrExprt2Context ctx) {
    widattr(support.getNode(ctx.exprt2()), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterWidattrWidName(WidattrWidNameContext ctx) {
    widattr(support.getNode(ctx.widname()), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterGwidget(GwidgetContext ctx) {
    if (ctx.inuic() != null) {
      if (ctx.inuic().FRAME() != null) {
        frameRef(support.getNode(ctx.inuic().widgetname()));
      } else if (ctx.inuic().BROWSE() != null) {
        browseRef(support.getNode(ctx.inuic().widgetname()));
      }
    }
    super.enterGwidget(ctx);
  }

  @Override
  public void enterS_widget(S_widgetContext ctx) {
    if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.REF);
    }
  }

  @Override
  public void enterWidname(WidnameContext ctx) {
    if (ctx.FRAME() != null) {
      frameRef(support.getNode(ctx.identifier()));
    } else if (ctx.BROWSE() != null) {
      browseRef(support.getNode(ctx.identifier()));
    } else if (ctx.BUFFER() != null) {
      bufferRef(support.getNode(ctx.identifier()));
    } else if (ctx.FIELD() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.REF);
    }
  }

  @Override
  public void enterAggregate_opt(Aggregate_optContext ctx) {
    addToSymbolScope(defineVariable(support.getNode(ctx.accum_what()), support.getNode(ctx.accum_what()), DataType.DECIMAL));
    // TODO Ou integer depending on type
  }

  @Override
  public void enterAssignment_list(Assignment_listContext ctx) {
    if (ctx.record() != null) {
      contextQualifiers.put(ctx.record(), ContextQualifier.UPDATING);
    }
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterAssign_equal(Assign_equalContext ctx) {
    if (ctx.widattr() != null) {
      contextQualifiers.put(ctx.widattr(), ContextQualifier.UPDATING);
    } else if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterReferencepoint(ReferencepointContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
  }

  @Override
  public void enterBuffercomparestate(BuffercomparestateContext ctx) {
    contextQualifiers.put(ctx.record(0), ContextQualifier.REF);
    
    if ((ctx.except_using_fields() != null) && (ctx.except_using_fields().field() != null)) {
      ContextQualifier qual = ctx.except_using_fields().USING() == null ? ContextQualifier.SYMBOL : ContextQualifier.REF;
      for (FieldContext field : ctx.except_using_fields().field()) {
        contextQualifiers.put(field, qual);
      }
    }

    contextQualifiers.put(ctx.record(1), ContextQualifier.REF);
  }

  @Override
  public void enterBuffercompare_save(Buffercompare_saveContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterBuffercopystate(BuffercopystateContext ctx) {
    contextQualifiers.put(ctx.record(0), ContextQualifier.REF);
    
    if ((ctx.except_using_fields() != null) && (ctx.except_using_fields().field() != null)) {
      ContextQualifier qual = ctx.except_using_fields().USING() == null ? ContextQualifier.SYMBOL : ContextQualifier.REF;
      for (FieldContext field : ctx.except_using_fields().field()) {
        contextQualifiers.put(field, qual);
      }
    }

    contextQualifiers.put(ctx.record(1), ContextQualifier.UPDATING);
  }

  @Override
  public void enterChoosestate(ChoosestateContext ctx) {
    frameInitializingStatement(support.getNode(ctx));
  }

  @Override
  public void enterChoose_field(Choose_fieldContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    frameStack.formItem(support.getNode(ctx.field()));
  }

  @Override
  public void enterChoose_opt(Choose_optContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void exitChoosestate(ChoosestateContext ctx) {
    frameStack.statementEnd();
  }
  
  @Override
  public void enterClassstate(ClassstateContext ctx) {
    rootScope.setClassName(ctx.tn.getText());
    rootScope.setTypeInfo(refSession.getTypeInfo(ctx.tn.getText()));
    // ABSTRACT, SERIALIZABLE and FINAL are read from rcode
  }
  
  @Override
  public void enterInterfacestate(InterfacestateContext ctx) {
    rootScope.setClassName(ctx.name.getText());
    rootScope.setTypeInfo(refSession.getTypeInfo(ctx.name.getText()));
    rootScope.setInterface(true);
  }

  @Override
  public void enterClearstate(ClearstateContext ctx) {
    if (ctx.frame_widgetname() != null) {
      frameStack.simpleFrameInitStatement(support.getNode(ctx), support.getNode(ctx.frame_widgetname().widgetname()),
          currentBlock);
    }
  }

  @Override
  public void enterCatchstate(CatchstateContext ctx) {
    scopeAdd(support.getNode(ctx));
    addToSymbolScope(defineVariable(support.getNode(ctx.ID()), support.getNode(ctx.ID())));
    defAs(support.getNode(ctx.AS()));
  }

  @Override
  public void exitCatchstate(CatchstateContext ctx) {
    scopeClose(support.getNode(ctx.CATCH()));
  }
  
  @Override
  public void enterClosestored_field(Closestored_fieldContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.REF);
  }
  
  @Override
  public void enterClosestored_where(Closestored_whereContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.REF);
  }
  
  @Override
  public void enterColorstate(ColorstateContext ctx) {
    frameInitializingStatement(support.getNode(ctx));
    for (Field_form_itemContext item : ctx.field_form_item()) {
      contextQualifiers.put(item, ContextQualifier.SYMBOL);
      frameStack.formItem(support.getNode(item));
    }
  }
  
  @Override
  public void exitColorstate(ColorstateContext ctx) {
    frameStack.statementEnd();
  }
  
  @Override
  public void enterColumnformat_opt(Columnformat_optContext ctx) {
    if ((ctx.LEXAT() != null) && ( ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
      frameStack.lexAt(support.getNode(ctx.field()));
    }
  }
  
  @Override
  public void enterConstructorstate(ConstructorstateContext ctx) {
    /*
     * Since 'structors don't have a name, we don't add them to any sort of map in the parent scope.
     */
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope.getParentScope();
    scopeAdd(blockNode);

    // 'structors don't have names, so use empty string.
    Routine r = new Routine("", definingScope, currentScope);
    r.setProgressType(blockNode.getNodeType());
    r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);
    currentRoutine = r;
  }

  @Override
  public void exitConstructorstate(ConstructorstateContext ctx) {
    scopeClose(support.getNode(ctx));
    currentRoutine = rootRoutine;
  }

  @Override
  public void enterCopylobstate(CopylobstateContext ctx) {
    // TODO Identify expression, then ...
    // TODO noteReference(#ex, ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatestate(CreatestateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreate_whatever_state(Create_whatever_stateContext ctx) {
    contextQualifiers.put(ctx.exprt(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatebrowsestate(CreatebrowsestateContext ctx) {
    contextQualifiers.put(ctx.exprt(), ContextQualifier.UPDATING);
  }
  
  @Override
  public void enterCreatebufferstate(CreatebufferstateContext ctx) {
    contextQualifiers.put(ctx.exprt(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatequerystate(CreatequerystateContext ctx) {
    contextQualifiers.put(ctx.exprt(), ContextQualifier.UPDATING);
    }
  
  @Override
  public void enterCreateserverstate(CreateserverstateContext ctx) {
    contextQualifiers.put(ctx.exprt(), ContextQualifier.UPDATING);
    }
  
  @Override
  public void enterCreateserversocketstate(CreateserversocketstateContext ctx) {
    contextQualifiers.put(ctx.exprt(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatetemptablestate(CreatetemptablestateContext ctx) {
    contextQualifiers.put(ctx.exprt(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatewidgetstate(CreatewidgetstateContext ctx) {
    // TODO Verifier sur tous les createXX que ça fonctionne avec un exprt
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterDdegetstate(DdegetstateContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    }
  
  @Override
  public void enterDdeinitiatestate(DdeinitiatestateContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }
  
  @Override
  public void enterDderequeststate(DderequeststateContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterDefinebrowsestate(DefinebrowsestateContext ctx) {
    stack.push(defineBrowse(support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }

  @Override
  public void enterDef_browse_display(Def_browse_displayContext ctx) {
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterDef_browse_display_items_or_record(Def_browse_display_items_or_recordContext ctx) {
    if (ctx.recordAsFormItem() != null) {
      contextQualifiers.put(ctx.recordAsFormItem(), ContextQualifier.INIT);
      frameStack.formItem(support.getNode(ctx.recordAsFormItem()));
    }
  }

  @Override
  public void enterDef_browse_enable(Def_browse_enableContext ctx) {
    // TODO Vérifier
    if ((ctx.all_except_fields() != null) && (ctx.all_except_fields().except_fields() != null)) {
      for (FieldContext fld : ctx.all_except_fields().except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterDef_browse_enable_item(Def_browse_enable_itemContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
    frameStack.formItem(support.getNode(ctx.field()));
  }

  @Override
  public void enterDefinebuttonstate(DefinebuttonstateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.BUTTON, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }

  @Override
  public void enterButton_opt(Button_optContext ctx) {
    if (ctx.like_field() != null) {
      contextQualifiers.put(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefinebuttonstate(DefinebuttonstateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinedatasetstate(DefinedatasetstateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.DATASET, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
    for (RecordContext record : ctx.record()) {
      contextQualifiers.put(record, ContextQualifier.INIT);
    }
  }

  @Override
  public void exitDefinedatasetstate(DefinedatasetstateContext ctx) {
    addToSymbolScope(stack.pop());
  }
  
  @Override
  public void enterData_relation(Data_relationContext ctx) {
    for (RecordContext record : ctx.record()) {
      contextQualifiers.put(record, ContextQualifier.INIT);
    }
  }

  @Override
  public void enterParent_id_relation(Parent_id_relationContext ctx) {
    for (RecordContext record : ctx.record()) {
      contextQualifiers.put(record, ContextQualifier.INIT);
    }
    for (FieldContext fld : ctx.field()) {
      contextQualifiers.put(fld, ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void enterField_mapping_phrase(Field_mapping_phraseContext ctx) {
    for (int zz = 0; zz < ctx.field().size(); zz += 2) {
      contextQualifiers.put(ctx.field().get(zz), ContextQualifier.SYMBOL);
      // TODO fld1 and fld2
      contextQualifiers.put(ctx.field().get(zz + 1), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void enterDefinedatasourcestate(DefinedatasourcestateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.DATASOURCE, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }

  @Override
  public void exitDefinedatasourcestate(DefinedatasourcestateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterSource_buffer_phrase(Source_buffer_phraseContext ctx) {
    recordNameNode((RecordNameNode) support.getNode(ctx.record()), ContextQualifier.INIT);
    if (ctx.field() != null) {
      for (FieldContext fld : ctx.field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterDefineeventstate(DefineeventstateContext ctx) {
    this.inDefineEvent = true;
    stack.push(defineEvent(support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }

  @Override
  public void exitDefineeventstate(DefineeventstateContext ctx) {
    this.inDefineEvent = false;
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefineframestate(DefineframestateContext ctx) {
    frameStack.nodeOfDefineFrame(support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier()), currentScope);
    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.SYMBOL);

    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitDefineframestate(DefineframestateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterDefineimagestate(DefineimagestateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.IMAGE, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }
  
  @Override
  public void enterDefineimage_opt(Defineimage_optContext ctx) {
    if (ctx.like_field() != null) {
      contextQualifiers.put(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefineimagestate(DefineimagestateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinemenustate(DefinemenustateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.MENU, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }

  @Override
  public void exitDefinemenustate(DefinemenustateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinepropertystate(DefinepropertystateContext ctx) {
    stack.push(defineVariable(support.getNode(ctx.DEFINE()), support.getNode(ctx.n)));
    defAs(support.getNode(ctx.AS()));
    
  }
  
  @Override
  public void exitDefinepropertystate(DefinepropertystateContext ctx) {
    // TODO Vérifier le moment où le pop est effectué, ce n'est pas exactement le exit
    addToSymbolScope(stack.pop()); 
  }

  @Override
  public void enterDefineproperty_accessor(Defineproperty_accessorContext ctx) {
    // TODO Probably only if ctx.code_block != null
    propGetSetBegin(support.getNode(((DefinepropertystateContext) ctx.getParent()).PROPERTY()));
  }

  @Override
  public void exitDefineproperty_accessor(Defineproperty_accessorContext ctx) {
    // TODO Probably only if ctx.code_block != null
    propGetSetEnd(support.getNode(((DefinepropertystateContext) ctx.getParent()).PROPERTY()));
  }

  @Override
  public void enterDefinequerystate(DefinequerystateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.QUERY, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
    for (RecordContext record : ctx.record()) {
      contextQualifiers.put(record, ContextQualifier.INIT);
    }
  }
  
  @Override
  public void exitDefinequerystate(DefinequerystateContext ctx) {
    addToSymbolScope(stack.pop()); 
  }
  
  @Override
  public void enterDefinerectanglestate(DefinerectanglestateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.RECTANGLE, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }

  @Override
  public void enterRectangle_opt(Rectangle_optContext ctx) {
    if (ctx.like_field() != null) {
      contextQualifiers.put(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefinerectanglestate(DefinerectanglestateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void exitDefinestreamstate(DefinestreamstateContext ctx) {
    addToSymbolScope(defineSymbol(ABLNodeType.STREAM, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }

  @Override
  public void enterDefinesubmenustate(DefinesubmenustateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.SUBMENU, support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier())));
  }

  @Override
  public void exitDefinesubmenustate(DefinesubmenustateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinetemptablestate(DefinetemptablestateContext ctx) {
    defineTempTable(support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier()));
    // TODO Only in case of before-table
    // defineBuffer(support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier()), support.getNode(ctx.identifier()), false);
  }

  @Override
  public void exitDefinetemptablestate(DefinetemptablestateContext ctx) {
    postDefineTempTable(support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier()));
  }
  
  @Override
  public void enterDef_table_like(Def_table_likeContext ctx) {
    defineTableLike(support.getNode(ctx.record()));
    // Change method...
    // TODO action.defineUseIndex(#rec, #id);
  }

  @Override
  public void enterDef_table_field(Def_table_fieldContext ctx) {
    stack.push(defineTableFieldInitialize(support.getNode(ctx.identifier())));
  }

  @Override
  public void exitDef_table_field(Def_table_fieldContext ctx) {
    defineTableFieldFinalize(stack.pop());
  }

  @Override
  public void enterDef_table_index(Def_table_indexContext ctx) {
    defineIndexInitialize(support.getNode(ctx.identifier(0)), ctx.UNIQUE() != null, ctx.PRIMARY() != null, false);
    for (int zz = 1; zz < ctx.identifier().size(); zz++) {
      defineIndexField(support.getNode(ctx.identifier(zz)));
    }
  }

  @Override
  public void enterDefineworktablestate(DefineworktablestateContext ctx) {
    defineWorktable(support.getNode(ctx.DEFINE()), support.getNode(ctx.identifier()));
  }

  @Override
  public void enterDefinevariablestate(DefinevariablestateContext ctx) {
    stack.push(defineVariable(support.getNode(ctx.DEFINE()), support.getNode(ctx.n)));
    // TODO Vérifier que les modificateurs sont bien là
  }

  @Override
  public void exitDefinevariablestate(DefinevariablestateContext ctx) {
    addToSymbolScope(stack.pop()); 
  }

  @Override
  public void enterDeletestate(DeletestateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterDestructorstate(DestructorstateContext ctx) {
    /*
     * Since 'structors don't have a name, we don't add them to any sort of map in the parent scope.
     */
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope.getParentScope();
    scopeAdd(blockNode);

    // 'structors don't have names, so use empty string.
    Routine r = new Routine("", definingScope, currentScope);
    r.setProgressType(blockNode.getNodeType());
    r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);
    currentRoutine = r;
  }

  @Override
  public void exitDestructorstate(DestructorstateContext ctx) {
    scopeClose(support.getNode(ctx));
    currentRoutine = rootRoutine;
  }

  @Override
  public void enterDisablestate(DisablestateContext ctx) {
    frameEnablingStatement(support.getNode(ctx));
    for (Form_itemContext form : ctx.form_item()) { // TODO Vérifier NPE
      contextQualifiers.put(form, ContextQualifier.SYMBOL);
    }
    // TODO Vérifier
    if ((ctx.all_except_fields() != null) && (ctx.all_except_fields().except_fields() != null)) {
      for (FieldContext fld : ctx.all_except_fields().except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitDisablestate(DisablestateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterDisabletriggersstate(DisabletriggersstateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.SYMBOL);
  }

  @Override
  public void enterDisplaystate(DisplaystateContext ctx) {
    frameInitializingStatement(support.getNode(ctx));
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitDisplaystate(DisplaystateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterField_equal_dynamic_new(Field_equal_dynamic_newContext ctx) {
    if (ctx.widattr() != null) {
      contextQualifiers.put(ctx.widattr(), ContextQualifier.UPDATING);
    } else if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterDostate(DostateContext ctx) {
    blockBegin(support.getNode(ctx));
    frameBlockCheck(support.getNode(ctx));
    
    // TODO A revoir, il faut que ce soit fait avant d'entrer dans le code_block
    frameStack.statementEnd();
  }

  @Override
  public void exitDostate(DostateContext ctx) {
    blockEnd();
  }

  @Override
  public void enterDownstate(DownstateContext ctx) {
    frameEnablingStatement(support.getNode(ctx));
  }

  @Override
  public void exitDownstate(DownstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterEmptytemptablestate(EmptytemptablestateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.TEMPTABLESYMBOL);
  }

  @Override
  public void enterEnablestate(EnablestateContext ctx) {
    frameEnablingStatement(support.getNode(ctx));

    for (Form_itemContext form : ctx.form_item()) { // TODO Vérifier NPE
      contextQualifiers.put(form, ContextQualifier.SYMBOL);
    }
    // TODO Vérifier
    if ((ctx.all_except_fields() != null) && (ctx.all_except_fields().except_fields() != null)) {
      for (FieldContext fld : ctx.all_except_fields().except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitEnablestate(EnablestateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterExportstate(ExportstateContext ctx) {
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterExtentphrase(ExtentphraseContext ctx) {
    // TODO Warning: action only has to be applied in limited number of cases i.e. rule extentphrase_def_symbol
    defExtent(support.getNode(ctx.EXTENT()));
  }

  @Override
  public void enterFieldoption(FieldoptionContext ctx) {
    if (ctx.AS() != null) {
      defAs(support.getNode(ctx.AS()));
    } else if (ctx.LIKE() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
      defLike(support.getNode(ctx.LIKE()));
    }
  }

  @Override
  public void enterFindstate(FindstateContext ctx) {
    recordNameNode((RecordNameNode) support.getNode(ctx), ContextQualifier.INIT);
  }
  
  @Override
  public void enterForstate(ForstateContext ctx) {
    blockBegin(support.getNode(ctx));
    frameBlockCheck(support.getNode(ctx));

    contextQualifiers.put(ctx.for_record_spec(), ContextQualifier.INITWEAK);
    
    // TODO Compliqué, faire le TP01Support.frameStatementEnd() après le block_colon
    // C'est fait également dans un autre cas, je ne sais plus lequel
  }

  @Override
  public void exitForstate(ForstateContext ctx) {
    blockEnd();
  }

  @Override
  public void enterFor_record_spec(For_record_specContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    for (RecordphraseContext rec : ctx.recordphrase()) {
      recordNameNode((RecordNameNode) support.getNode(rec), qual);
    }
  }

  // TODO Move method to top
  @Override
  public void enterForm_item(Form_itemContext ctx) {
    if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), contextQualifiers.removeFrom(ctx));
      frameStack.formItem(support.getNode(ctx.field()));
    } else if (ctx.recordAsFormItem() != null) {
      contextQualifiers.put(ctx.recordAsFormItem(), contextQualifiers.removeFrom(ctx));
      frameStack.formItem(support.getNode(ctx.recordAsFormItem()));
    }
    // TODO Il reste le cas text_opt (line 1306 de TreeParser01.g)
  }

  // TODO Move method to top
  @Override
  public void enterForm_items_or_record(Form_items_or_recordContext ctx) {
    // FIXME Verifier le cas disablestate et enablestate qui utilise en fait form_items+
    // En fait ça ne doit pas géner
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    for (int kk = 0; kk < ctx.getChildCount(); kk++) {
      contextQualifiers.put(ctx.getChild(kk), qual);
    }
  }

  @Override
  public void enterFormstate(FormstateContext ctx) {
    frameInitializingStatement(support.getNode(ctx));
    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.SYMBOL);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitFormstate(FormstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterFormat_opt(Format_optContext ctx) {
    if ((ctx.LEXAT() != null) && (ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
      frameStack.lexAt(support.getNode(ctx.field()));
    } else if ((ctx.LIKE() != null) && (ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void enterFrame_widgetname(Frame_widgetnameContext ctx) {
    // TODO Double check support.getNode
    frameStack.frameRefNode(support.getNode(ctx.widgetname().identifier()), currentScope);
  }

  @Override
  public void enterFrame_opt(Frame_optContext ctx) {
    if ((ctx.CANCELBUTTON() != null) && (ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
    } else if ((ctx.DEFAULTBUTTON() != null) && (ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
    } 
  }

  @Override
  public void enterFunctionstate(FunctionstateContext ctx) {
    // TODO TP01Support.funcBegin();
    // John: Need some comments here. Why don't I just fetch any
    // function forward scope right away? Why wait until funcDef()?
    // Why bother with a funcForward map specifically, rather than
    // just a funcScope map generally?
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope.getParentScope();
    scopeAdd(blockNode);

    Routine r = new Routine(ctx.id.getText(), definingScope, currentScope);
    r.setProgressType(ABLNodeType.FUNCTION);
    r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);
    definingScope.add(r);
    currentRoutine = r;

    // TODO TP01Support.routineReturnDatatype(functionstate_AST_in);
    
    if (ctx.FORWARDS() != null) {
      // TODO TP01Support.funcForward();
      funcForwards.put(ctx.id.getText(), currentScope);
    } else {
      // TODO TP01Support.funcDef();
      /*
       * If this function definition had a function forward declaration, then we use the block and scope from that
       * declaration, in case it is where the parameters were defined. (You can define the params in the FORWARD, and
       * leave them out at the body.)
       *
       * However, if this statement re-defines the formal args, then we use this statement's scope - because the formal
       * arg names from here will be in effect rather than the names from the FORWARD. (The names don't have to match.)
       */
      if (!currentRoutine.getParameters().isEmpty())
        return;
      TreeParserSymbolScope forwardScope = funcForwards.get(ctx.id.getText());
      if (forwardScope != null) {
        JPNode node = forwardScope.getRootBlock().getNode();
        Routine routine = (Routine) node.getSymbol();
        scopeSwap(forwardScope);

        // Weird (already set at the beginning)
        blockNode.setBlock(currentBlock);
        blockNode.setSymbol(routine);
        routine.setDefOrIdNode(blockNode);
        currentRoutine = routine;
      }

    }
  }

  @Override
  public void exitFunctionstate(FunctionstateContext ctx) {
    scopeClose(support.getNode(ctx));
    currentRoutine = rootRoutine;
  }

  @Override
  public void enterGetkeyvaluestate(GetkeyvaluestateContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterImportstate(ImportstateContext ctx) {
    for (FieldContext fld : ctx.field()) {
      contextQualifiers.put(fld, ContextQualifier.UPDATING);
    }
    if (ctx.var_rec_field() != null) {
      contextQualifiers.put(ctx.var_rec_field(), ContextQualifier.UPDATING);
    }
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterInsertstate(InsertstateContext ctx) {
    frameInitializingStatement(support.getNode(ctx));

    contextQualifiers.put(ctx.record(), ContextQualifier.UPDATING);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitInsertstate(InsertstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterLdbname_opt1(Ldbname_opt1Context ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.BUFFERSYMBOL);
  }

  @Override
  public void enterMessage_opt(Message_optContext ctx) {
    if ((ctx.SET() != null) && (ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    } else if ((ctx.UPDATE() != null) && (ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.REFUP);
    } 
  }

  @Override
  public void enterMethodstate(MethodstateContext ctx) {
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope.getParentScope();
    scopeAdd(blockNode);

    Routine r = new Routine(ctx.id.getText(), definingScope, currentScope);
    r.setProgressType(ABLNodeType.METHOD);
    // r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);
    definingScope.add(r);
    currentRoutine = r;

    // TODO TP01Support.routineReturnDatatype(returnTypeNode);
  }

  @Override
  public void exitMethodstate(MethodstateContext ctx) {
    // TODO TP01Support.methodEnd(#m);
  }

  @Override
  public void enterNextpromptstate(NextpromptstateContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
  }

  @Override
  public void enterOpenquerystate(OpenquerystateContext ctx) {
    contextQualifiers.put(ctx.for_record_spec(), ContextQualifier.INIT);
  }

  @Override
  public void enterProcedurestate(ProcedurestateContext ctx) {
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope;
    scopeAdd(blockNode);

    Routine r = new Routine(ctx.filename().getText(), definingScope, currentScope);
    r.setProgressType(ABLNodeType.PROCEDURE);
    // r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);
    definingScope.add(r);
    currentRoutine = r;
  }

  @Override
  public void exitProcedurestate(ProcedurestateContext ctx) {
    scopeClose(support.getNode(ctx));
    currentRoutine = rootRoutine;
  }

  @Override
  public void enterPromptforstate(PromptforstateContext ctx) {
    // TODO Check node
    frameEnablingStatement(support.getNode(ctx));

    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.SYMBOL);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitPromptforstate(PromptforstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterRawtransferstate(RawtransferstateContext ctx) {
    contextQualifiers.put(ctx.rawtransfer_elem(0), ContextQualifier.REF);
    contextQualifiers.put(ctx.rawtransfer_elem(1), ContextQualifier.UPDATING);
  }

  @Override
  public void enterRawtransfer_elem(Rawtransfer_elemContext ctx) {
    if (ctx.record() != null) {
      contextQualifiers.put(ctx.record(), contextQualifiers.removeFrom(ctx));
    } else if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), contextQualifiers.removeFrom(ctx));
    } else {
      contextQualifiers.put(ctx.var_rec_field(), contextQualifiers.removeFrom(ctx));
      // TODO Il faut que ce soit traité par enterVarRecField
    }
  }

  @Override
  public void enterRecord(RecordContext ctx) {
    recordNameNode((RecordNameNode) support.getNode(ctx.f), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterField(FieldContext ctx) {
    TableNameResolution tnr = nameResolution.removeFrom(ctx);
    if (tnr == null) tnr = TableNameResolution.ANY;
    // XXX Call field() method
  }

  @Override
  public void enterRecord_fields(Record_fieldsContext ctx) {
    if (ctx.field() != null) {
      for (FieldContext fld : ctx.field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterRecord_opt(Record_optContext ctx) {
    if ((ctx.OF() != null) && (ctx.record() != null)) {
      contextQualifiers.put(ctx.record(), ContextQualifier.REF);
    }
    if ((ctx.USING() != null) && (ctx.field() != null)) {
      for (FieldContext field : ctx.field()) {
        contextQualifiers.put(field, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterReleasestate(ReleasestateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.REF);
  }

  @Override
  public void enterRepeatstate(RepeatstateContext ctx) {
    blockBegin(support.getNode(ctx));
    // TODO I think it should be support.getNode().getFirstChild()
    frameBlockCheck(support.getNode(ctx));

    // TODO A revoir, il faut que ce soit fait avant d'entrer dans le code_block
    frameStack.statementEnd();
  }

  @Override
  public void exitRepeatstate(RepeatstateContext ctx) {
    blockEnd();
  }

  @Override
  public void enterRun_set(Run_setContext ctx) {
    if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterScrollstate(ScrollstateContext ctx) {
    // TODO Check support.getNode
    frameInitializingStatement(support.getNode(ctx));
  }

  @Override
  public void exitScrollstate(ScrollstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterSetstate(SetstateContext ctx) {
    // TODO Check support.getNode
    frameInitializingStatement(support.getNode(ctx));

    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.REFUP);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitSetstate(SetstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterSystemdialogcolorstate(SystemdialogcolorstateContext ctx) {
    if (ctx.update_field() != null) {
      contextQualifiers.put(ctx.update_field().field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterSysdiafont_opt(Sysdiafont_optContext ctx) {
    if (ctx.update_field() != null) {
      contextQualifiers.put(ctx.update_field().field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterSystemdialoggetdirstate(SystemdialoggetdirstateContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.REFUP);
  }
 
  @Override
  public void enterSystemdialoggetdir_opt(Systemdialoggetdir_optContext ctx) {
    if (ctx.field() != null) {
      // TODO Check consistency with sys diag get file
      contextQualifiers.put(ctx.field(), ContextQualifier.REFUP);
    }
  }

  @Override
  public void enterSystemdialoggetfilestate(SystemdialoggetfilestateContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.REFUP);
  }

  @Override
  public void enterSysdiagetfile_opt(Sysdiagetfile_optContext ctx) {
    if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterSysdiapri_opt(Sysdiapri_optContext ctx) {
    if (ctx.update_field() != null) {
      contextQualifiers.put(ctx.update_field().field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterTrigger_on(Trigger_onContext ctx) {
    scopeAdd(support.getNode(ctx));
  }

  @Override
  public void exitTrigger_on(Trigger_onContext ctx) {
    scopeClose(support.getNode(ctx));
  }

  @Override
  public void enterUnderlinestate(UnderlinestateContext ctx) {
    // TODO Check support.getNode()
    frameInitializingStatement(support.getNode(ctx));

    for (Field_form_itemContext field : ctx.field_form_item()) {
      contextQualifiers.put(field, ContextQualifier.SYMBOL);
      // TODO Check support.getNode()
      frameStack.formItem(support.getNode(field));
    }
  }

  @Override
  public void exitUnderlinestate(UnderlinestateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterUpstate(UpstateContext ctx) {
    // TODO Check support.getNode()
    frameInitializingStatement(support.getNode(ctx));
  }

  @Override
  public void exitUpstate(UpstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterUpdatestate(UpdatestateContext ctx) {
    // TODO Check support.getNode
    frameEnablingStatement(support.getNode(ctx));
    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.REFUP);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitUpdatestate(UpdatestateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterValidatestate(ValidatestateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.REF);
  }

  @Override
  public void exitViewstate(ViewstateContext ctx) {
    // The VIEW statement grammar uses gwidget, so we have to do some
    // special searching for FRAME to initialize.
    // TODO Check support.getNode
    JPNode headNode = support.getNode(ctx);
    for (JPNode frameNode : headNode.query(ABLNodeType.FRAME)) {
      ABLNodeType parentType = frameNode.getParent().getNodeType();
      if (parentType == ABLNodeType.WIDGET_REF || parentType == ABLNodeType.IN) {
        frameStack.simpleFrameInitStatement(headNode, frameNode.nextNode(), currentBlock);
        return;
      }
    }
  }

  @Override
  public void enterWaitfor_set(Waitfor_setContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }


  
  /** Called at the *end* of the statement that defines the symbol. */
  public void addToSymbolScope(Symbol o) {
    LOG.trace("addToSymbolScope - Adding {} to {}", o, currentScope);
    if (inDefineEvent) return;
    currentScope.add(o);
  }


  /** This is a specialization of frameInitializingStatement, called for ENABLE|UPDATE|PROMPT-FOR. */
  public void frameEnablingStatement(JPNode ast) {
    LOG.trace("Entering frameEnablingStatement {}", ast);

    // Flip this flag before calling nodeOfInitializingStatement.
    frameStack.statementIsEnabler();
    frameStack.nodeOfInitializingStatement(ast, currentBlock);
  }

  public void frameInitializingStatement(JPNode ast) {
    frameStack.nodeOfInitializingStatement(ast, currentBlock);
  }

  public void scopeClose(JPNode scopeRootNode) {
    LOG.trace("Entering scopeClose {}", scopeRootNode);
    currentScope = currentScope.getParentScope();
    blockEnd();
  }

  public void blockEnd() {
    LOG.trace("Entering blockEnd");
    currentBlock = popBlock();
  }

  public void scopeAdd(JPNode anode) {
    LOG.trace("Entering scopeAdd {}", anode);
    BlockNode blockNode = (BlockNode) anode;
    currentScope = currentScope.addScope();
    currentBlock = pushBlock(new Block(currentScope, blockNode));
    currentScope.setRootBlock(currentBlock);
    blockNode.setBlock(currentBlock);
  }

  public void blockBegin(JPNode blockAST) {
    LOG.trace("Entering blockBegin {}", blockAST);
    BlockNode blockNode = (BlockNode) blockAST;
    currentBlock = pushBlock(new Block(currentBlock, blockNode));
    blockNode.setBlock(currentBlock);
  }

  public void frameBlockCheck(JPNode ast) {
    LOG.trace("Entering frameBlockCheck {}", ast);
    frameStack.nodeOfBlock(ast, currentBlock);
  }

  public Variable defineVariable(JPNode defAST, JPNode idAST) {
    return defineVariable(defAST, idAST, false);
  }

  public Variable defineVariable(JPNode defNode, JPNode idNode, boolean parameter) {
    LOG.trace("Entering defineVariable {} {} {}", defNode, idNode, parameter);
    /*
     * Some notes: We need to create the Variable Symbol right away, because further actions in the grammar might need
     * to set attributes on it. We can't add it to the scope yet, because of statements like this: def var xyz like xyz.
     * The tree parser is responsible for calling addToScope at the end of the statement or when it is otherwise safe to
     * do so.
     */
    String name = idNode.getText();
    if (name == null || name.length() == 0) {
      /*
       * Variable Name: There was a subtle bug here when parsing trees extracted from PUB files. In PUB files, the text
       * of keyword nodes are not stored. But in the case of an ACCUMULATE statement -> aggregatephrase ->
       * aggregate_opt, we are defining variable/symbols using the COUNT|MAXIMUM|TOTAL|whatever node. I added a check
       * for empty text from the "id" node.
       */
      name = idNode.getNodeType().name();
    }
    Variable variable = new Variable(name, currentScope, parameter);
    variable.setDefOrIdNode(defNode);
    currSymbol = variable;
    idNode.setLink(IConstants.SYMBOL, variable);
    return variable;
  }

  public Variable defineVariable(JPNode defAST, JPNode idAST, DataType dataType) {
    return defineVariable(defAST, idAST, dataType, false);
  }

  public Variable defineVariable(JPNode defAST, JPNode idAST, DataType dataType, boolean parameter) {
    Variable v = defineVariable(defAST, idAST, parameter);
    v.setDataType(dataType);
    return v;
  }

  public Variable defineVariable(JPNode defAST, JPNode idAST, JPNode likeAST) {
    return defineVariable(defAST, idAST, likeAST, false);
  }

  public Variable defineVariable(JPNode defAST, JPNode idAST, JPNode likeAST, boolean parameter) {
    Variable v = defineVariable(defAST, idAST, parameter);
    FieldRefNode likeRefNode = (FieldRefNode) likeAST;
    v.setDataType(likeRefNode.getDataType());
    v.setClassName(likeRefNode.getClassName());

    return v;
  }

  /** The tree parser calls this at an AS node */
  public void defAs(JPNode asNode) {
    LOG.trace("Entering defAs {}", asNode);
    currSymbol.setAsNode(asNode);
    Primative primative = (Primative) currSymbol;
    JPNode typeNode = asNode.nextNode();
    if (typeNode.getNodeType() == ABLNodeType.CLASS)
      typeNode = typeNode.nextNode();
    if (typeNode.getNodeType() == ABLNodeType.TYPE_NAME) {
      primative.setDataType(DataType.CLASS);
      primative.setClassName(typeNode);
    } else {
      primative.setDataType(DataType.getDataType(typeNode.getType()));
    }
    assert primative.getDataType() != null : "Failed to set datatype at " + asNode.getFileIndex() + " line "
        + asNode.getLine();
  }

  public void defExtent(JPNode extentNode) {
    LOG.trace("Entering defExtent {}", extentNode);
    Primative primative = (Primative) currSymbol;
    JPNode exprNode = extentNode.getFirstChild();
    // If there is no expression node, then it's an "indeterminate extent".
    // If it's not a numeric literal, then we give up.
    if (exprNode == null || exprNode.getNodeType() != ABLNodeType.NUMBER) {
      primative.setExtent(-1);
    } else {
      primative.setExtent(Integer.parseInt(exprNode.getText()));
    }
  }

  /**
   * In the case of a function definition that comes some time after a function forward declaration, we want to use the
   * scope that was created with the forward declaration, because it is the scope that has all of the parameter
   * definitions. We have to do this because the definition itself may have left out the parameter list - it's not
   * required - it just uses the parameter list from the declaration.
   */
  private void scopeSwap(TreeParserSymbolScope scope) {
    currentScope = scope;
    blockEnd(); // pop the unused block from the stack
    currentBlock = pushBlock(scope.getRootBlock());
  }

  public void defLike(JPNode likeNode) {
    LOG.trace("Entering defLike {}", likeNode);
    currSymbol.setLikeNode(likeNode);
    Primative likePrim = (Primative) likeNode.nextNode().getSymbol();
    Primative newPrim = (Primative) currSymbol;
    if (likePrim != null) {
      newPrim.assignAttributesLike(likePrim);
      assert newPrim.getDataType() != null : "Failed to set datatype at " + likeNode.getFileIndex() + " line "
          + likeNode.getLine();
    } else {
      LOG.error("Failed to find LIKE datatype at {} line {}", likeNode.getFileIndex(), likeNode.getLine());
    }
  }

  public Symbol defineSymbol(ABLNodeType symbolType, JPNode defNode, JPNode idNode) {
    LOG.trace("Entering defineSymbol {} - {} - {}", symbolType, defNode, idNode);
    /*
     * Some notes: We need to create the Symbol right away, because further actions in the grammar might need to set
     * attributes on it. We can't add it to the scope yet, because of statements like this: def var xyz like xyz. The
     * tree parser is responsible for calling addToScope at the end of the statement or when it is otherwise safe to do
     * so.
     */
    Symbol symbol = SymbolFactory.create(symbolType, idNode.getText(), currentScope);
    currSymbol = symbol;
    currSymbol.setDefOrIdNode(defNode);
    idNode.setLink(IConstants.SYMBOL, symbol);
    return symbol;
  }

  /** Called at the start of a DEFINE BROWSE statement. */
  public Browse defineBrowse(JPNode defAST, JPNode idAST) {
    LOG.trace("Entering defineBrowse {} - {}", defAST, idAST);
    Browse browse = (Browse) defineSymbol(ABLNodeType.BROWSE, defAST, idAST);
    frameStack.nodeOfDefineBrowse(browse, (JPNode) defAST);
    return browse;
  }

  public Event defineEvent(JPNode defNode, JPNode idNode) {
    LOG.trace("Entering defineEvent {} - {}", defNode, idNode);
    String name = idNode.getText();
    if (name == null || name.length() == 0)
      name = idNode.getNodeType().name();
    Event event = new Event(name, currentScope);
    event.setDefOrIdNode(defNode);
    currSymbol = event;
    idNode.setLink(IConstants.SYMBOL, event);
    return event;
  }

  /**
   * Defining a table field is done in two steps. The first step creates the field and field buffer but does not assign
   * the field to the table yet. The second step assigns the field to the table. We don't want the field assigned to the
   * table until we're done examining the field options, because we don't want the field available for lookup due to
   * situations like this: def temp-table tt1 field DependentCare like DependentCare.
   * 
   * @return The Object that is expected to be passed as an argument to defineTableFieldFinalize.
   * @see #defineTableFieldFinalize(Object)
   */
  public Symbol defineTableFieldInitialize(JPNode idNode) {
    LOG.trace("Entering defineTableFieldInitialize {}", idNode);
    FieldBuffer fieldBuff = rootScope.defineTableFieldDelayedAttach(idNode.getText(), currDefTable);
    currSymbol = fieldBuff;
    fieldBuff.setDefOrIdNode(idNode);
    idNode.setLink(IConstants.SYMBOL, fieldBuff);
    return fieldBuff;
  }

  public void defineTableFieldFinalize(Object obj) {
    LOG.trace("Entering defineTableFieldFinalize {}", obj);
    ((FieldBuffer) obj).getField().setTable(currDefTable.getTable());
  }

  public void defineTableLike(JPNode tableAST) {
    LOG.trace("Entering defineTableLike {}", tableAST);
    // Get table for "LIKE table"
    ITable table = astTableLink(tableAST);
    currDefTableLike = table;
    // For each field in "table", create a field def in currDefTable
    for (IField field : table.getFieldPosOrder()) {
      rootScope.defineTableField(field.getName(), currDefTable).assignAttributesLike(field);
    }
  }

  public void defineUseIndex(JPNode recNode, JPNode idNode) {
    LOG.trace("Entering defineUseIndex {}", idNode);
    ITable table = astTableLink(recNode);
    IIndex idx = table.lookupIndex(idNode.getText());
    currDefTable.getTable().add(new Index(currDefTable.getTable(), idx.getName(), idx.isUnique(), idx.isPrimary()));
    currDefTableUseIndex = true;
  }

  public void defineIndexInitialize(JPNode idNode, boolean unique, boolean primary, boolean word) {
    LOG.trace("Entering defineIndexInitialize {} - {} - {} - {}", idNode, unique, primary, word);
    currDefIndex = new Index(currDefTable.getTable(), idNode.getText(), unique, primary);
    currDefTable.getTable().add(currDefIndex);
  }

  public void defineIndexField(JPNode idNode) {
    LOG.trace("Entering defineIndexField{}", idNode);
    IField fld = currDefTable.getTable().lookupField(idNode.getText());
    if (fld != null)
      currDefIndex.addField(fld);
  }

  public void defineTable(JPNode defNode, JPNode idNode, int storeType) {
    LOG.trace("Entering defineTable {} {} {}", defNode, idNode, storeType);
    TableBuffer buffer = rootScope.defineTable(idNode.getText(), storeType);
    currSymbol = buffer;
    currSymbol.setDefOrIdNode(defNode);
    currDefTable = buffer;
    currDefTableLike = null;
    currDefTableUseIndex = false;
    idNode.setLink(IConstants.SYMBOL, buffer);
  }

  public void postDefineTempTable(JPNode defAST, JPNode idNode) {
    LOG.trace("Entering postDefineTempTable {} {}", defAST, idNode);
    // In case of DEFINE TT LIKE, indexes are copied only if USE-INDEX and INDEX are never used 
    if ((currDefTableLike != null) && !currDefTableUseIndex && currDefTable.getTable().getIndexes().isEmpty()) {
      LOG.trace("Copying all indexes from {}", currDefTableLike.getName());
      for (IIndex idx : currDefTableLike.getIndexes()) {
        Index newIdx = new Index(currDefTable.getTable(), idx.getName(), idx.isUnique(), idx.isPrimary());
        for (IField fld : idx.getFields()) {
          IField ifld = newIdx.getTable().lookupField(fld.getName());
          if (ifld == null) {
            LOG.info("Unable to find field name {} in table {}", fld.getName(), currDefTable.getTable().getName());
          } else {
            newIdx.addField(ifld);
          }
        }
        currDefTable.getTable().add(newIdx);
      }
    }
  }

  public void defineTempTable(JPNode defAST, JPNode idAST) {
    defineTable((JPNode) defAST, (JPNode) idAST, IConstants.ST_TTABLE);
  }
  
  /** Get the Table symbol linked from a RECORD_NAME AST. */
  private ITable astTableLink(JPNode tableAST) {
    LOG.trace("Entering astTableLink {}", tableAST);
    TableBuffer buffer = (TableBuffer) tableAST.getLink(IConstants.SYMBOL);
    assert buffer != null;
    return buffer.getTable();
  }

  /**
   * Define a buffer. If the buffer is initialized at the same time it is defined (as in a buffer parameter), then
   * parameter init should be true.
   */
  public void defineBuffer(JPNode defAST, JPNode idNode, JPNode tableAST, boolean init) {
    LOG.trace("Entering defineBuffer {} {} {} {}", defAST, idNode, tableAST, init);
    ITable table = astTableLink(tableAST);
    TableBuffer bufSymbol = currentScope.defineBuffer(idNode.getText(), table);
    currSymbol = bufSymbol;
    currSymbol.setDefOrIdNode((JPNode) defAST);
    idNode.setLink(IConstants.SYMBOL, bufSymbol);
    if (init) {
      BufferScope bufScope = currentBlock.getBufferForReference(bufSymbol);
      idNode.setLink(IConstants.BUFFERSCOPE, bufScope);
    }
  }

  public void defineWorktable(JPNode defAST, JPNode idAST) {
    defineTable(defAST, idAST, IConstants.ST_WTABLE);
  }

  public void noteReference(JPNode node, ContextQualifier cq) {
    if ((node.getSymbol() != null) && ((cq == ContextQualifier.UPDATING) || (cq == ContextQualifier.REFUP))) {
      node.getSymbol().noteReference(cq);
    }
  }

  public void propGetSetBegin(JPNode propAST) {
    LOG.trace("Entering propGetSetBegin {}", propAST);
    scopeAdd(propAST);
    BlockNode blockNode = (BlockNode) propAST;
    TreeParserSymbolScope definingScope = currentScope.getParentScope();
    Routine r = new Routine(propAST.getText(), definingScope, currentScope);
    r.setProgressType(propAST.getNodeType());
    r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);
    definingScope.add(r);
    currentRoutine = r;
  }

  public void propGetSetEnd(JPNode propAST) {
    LOG.trace("Entering propGetSetEnd {}", propAST);
    scopeClose(propAST);
    currentRoutine = rootRoutine;
  }
  
  public void widattr(JPNode idNode, ContextQualifier cq) {
    LOG.trace("Entering {} mode {}", idNode, cq);
    if (idNode.getNodeType() == ABLNodeType.THISOBJECT) {
      JPNode tok = idNode.getNextSibling();
      if (tok.getNodeType() == ABLNodeType.OBJCOLON) {
        JPNode fld = tok.getNextSibling();
        String name = fld.getText();

        FieldLookupResult result =  currentBlock.lookupField(name, true);
        if (result == null)
          return;

        // Variable
        if (result.variable != null) {
          result.variable.noteReference(cq);
        }
      }
    } else if (idNode.getNodeType() == ABLNodeType.FIELD_REF) {
      // Reference to a static field
      if ((idNode.getFirstChild().getNodeType()) == ABLNodeType.ID && (idNode.getNextSibling() != null) && (idNode.getNextSibling().getNodeType() == ABLNodeType.OBJCOLON)) {
        String clsRef = idNode.getFirstChild().getText();
        String clsName = rootScope.getClassName();
        if ((clsRef != null) && (clsName != null) && (clsRef.indexOf('.') == -1) && (clsName.indexOf('.') != -1))
          clsName = clsName.substring(clsName.indexOf('.') + 1);
        
        if ((clsRef != null) && (clsName != null) && clsRef.equalsIgnoreCase(clsName)) {
          String right = idNode.getNextSibling().getNextSibling().getText();
          
          FieldLookupResult result =  currentBlock.lookupField(right, true);
          if (result == null)
            return;

          // Variable
          if (result.variable != null) {
            result.variable.noteReference(cq);
          }
        }
      }
    }
  }

  public void frameRef(JPNode idAST) {
    frameStack.frameRefNode((JPNode) idAST, currentScope);
  }

  public void browseRef(JPNode idAST) {
    LOG.trace("Entering browseRef {}", idAST);
    frameStack.browseRefNode((JPNode) idAST, currentScope);
  }

  public void bufferRef(JPNode idAST) {
    LOG.trace("Entering bufferRef {}", idAST);
    TableBuffer tableBuffer = currentScope.lookupBuffer(idAST.getText());
    if (tableBuffer != null) {
      tableBuffer.noteReference(ContextQualifier.SYMBOL);
    }
  }

  public void field(JPNode refAST, JPNode idNode, ContextQualifier cq, TableNameResolution resolution) {
    LOG.trace("Entering field {} {} {} {}", refAST, idNode, cq, resolution);
    FieldRefNode refNode = (FieldRefNode) refAST;
    String name = idNode.getText();
    FieldLookupResult result = null;

    refNode.attrSet(IConstants.CONTEXT_QUALIFIER, cq.toString());

    // Check if this is a Field_ref being "inline defined"
    // If so, we define it right now.
    if (refNode.attrGet(IConstants.INLINE_VAR_DEF) == 1)
      addToSymbolScope(defineVariable(idNode, idNode));

    if ((refNode.getParent().getNodeType() == ABLNodeType.USING && refNode.getParent().getParent().getNodeType() == ABLNodeType.RECORD_NAME)
        || (refNode.getFirstChild().getNodeType() == ABLNodeType.INPUT &&
            (refNode.getNextSibling() == null || refNode.getNextSibling().getNodeType() != ABLNodeType.OBJCOLON))) {
      // First condition : there seems to be an implicit INPUT in USING phrases in a record phrase.
      // Second condition :I've seen at least one instance of "INPUT objHandle:attribute" in code,
      // which for some reason compiled clean. As far as I'm aware, the INPUT was
      // meaningless, and the compiler probably should have complained about it.
      // At any rate, the handle:attribute isn't an input field, and we don't want
      // to try to look up the handle using frame field rules.
      // Searching the frames for an existing INPUT field is very different than
      // the usual field/variable lookup rules. It is done based on what is in
      // the referenced FRAME or BROWSE, or what is found in the frames most
      // recently referenced list.
      result = frameStack.inputFieldLookup(refNode, currentScope);
    } else if (resolution == TableNameResolution.ANY) {
      // Lookup the field, with special handling for FIELDS/USING/EXCEPT phrases
      boolean getBufferScope = (cq != ContextQualifier.SYMBOL);
      result = currentBlock.lookupField(name, getBufferScope);
    } else {
      // If we are in a FIELDS phrase, then we know which table the field is from.
      // The field lookup in Table expects an unqualified name.
      String[] parts = name.split("\\.");
      String fieldPart = parts[parts.length - 1];
      TableBuffer ourBuffer = resolution == TableNameResolution.PREVIOUS ? prevTableReferenced : lastTableReferenced;
      IField field = ourBuffer.getTable().lookupField(fieldPart);
      if (field == null) {
        // The OpenEdge compiler seems to ignore invalid tokens in a FIELDS phrase.
        // As a result, some questionable code will fail to parse here if we don't also ignore those here.
        // Sigh. This would be a good lint rule.
        ABLNodeType parentType = refNode.getParent().getNodeType();
        if (parentType == ABLNodeType.FIELDS || parentType == ABLNodeType.EXCEPT)
          return;
        // TODO Throw exception
      }
      FieldBuffer fieldBuffer = ourBuffer.getFieldBuffer(field);
      result = new FieldLookupResult();
      result.field = fieldBuffer;
    }

    // TODO Once we've added static member resolution, we can re-add this test.
    if (result == null)
      return;
    // if (result == null)
    // throw new Error(
    // idNode.getFilename()
    // + ":"
    // + idNode.getLine()
    // + " Unknown field or variable name: " + name
    // );

    if (result.isUnqualified)
      refNode.attrSet(IConstants.UNQUALIFIED_FIELD, IConstants.TRUE);
    if (result.isAbbreviated)
      refNode.attrSet(IConstants.ABBREVIATED, IConstants.TRUE);
    // Variable
    if (result.variable != null) {
      refNode.setSymbol(result.variable);
      refNode.attrSet(IConstants.STORETYPE, IConstants.ST_VAR);
      result.variable.noteReference(cq);
    }
    // FieldLevelWidget
    if (result.fieldLevelWidget != null) {
      refNode.setSymbol(result.fieldLevelWidget);
      refNode.attrSet(IConstants.STORETYPE, IConstants.ST_VAR);
      result.fieldLevelWidget.noteReference(cq);
    }
    // Buffer attributes
    if (result.bufferScope != null) {
      refNode.setBufferScope(result.bufferScope);
    }
    // Table field
    if (result.field != null) {
      refNode.setSymbol(result.field);
      refNode.attrSet(IConstants.STORETYPE, result.field.getField().getTable().getStoretype());
      result.field.noteReference(cq);
      if (result.field.getBuffer() != null) {
        result.field.getBuffer().noteReference(cq);
      }
    }
    // Event
    if (result.event != null) {
      refNode.setSymbol(result.event);
      refNode.attrSet(IConstants.STORETYPE, IConstants.ST_VAR);
      result.event.noteReference(cq);
    }

  } // field()

}
