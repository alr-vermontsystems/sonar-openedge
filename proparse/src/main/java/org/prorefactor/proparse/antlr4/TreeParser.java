package org.prorefactor.proparse.antlr4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.prorefactor.core.ABLNodeType;
import org.prorefactor.core.IConstants;
import org.prorefactor.core.JPNode;
import org.prorefactor.core.nodetypes.BlockNode;
import org.prorefactor.core.nodetypes.RecordNameNode;
import org.prorefactor.core.schema.ITable;
import org.prorefactor.proparse.ParserSupport;
import org.prorefactor.proparse.antlr4.Proparse.*;
import org.prorefactor.refactor.RefactorSession;
import org.prorefactor.treeparser.Block;
import org.prorefactor.treeparser.ContextQualifier;
import org.prorefactor.treeparser.Primative;
import org.prorefactor.treeparser.TreeParserRootSymbolScope;
import org.prorefactor.treeparser.TreeParserSymbolScope;
import org.prorefactor.treeparser.symbols.ISymbol;
import org.prorefactor.treeparser.symbols.Routine;
import org.prorefactor.treeparser.symbols.TableBuffer;
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
    // TODO r.setDefOrIdNode(blockNode);
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
      // TP01Support.noteReference(support.getNode(ctx), contextQualifiers.get(exp));
    }
  }

  @Override
  public void enterRecordfunc(RecordfuncContext ctx) {
    // TP01Support.recordNameNode(support.getNode(ctx.record()), ContextQualifier.REF);
  }

  @Override
  public void enterParameterBufferRecord(ParameterBufferRecordContext ctx) {
    // action.paramForCall(parameter_AST_in);

    // TP01Support.recordNameNode(support.getNode(ctx.record()), ContextQualifier.INIT);
    //  action.paramProgressType(BUFFER);
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
    // TODO TP01Support.paramBind();
  }

  @Override
  public void enterFilenameorvalue(FilenameorvalueContext ctx) {
    if (ctx.valueexpression() != null) {
      // TODO TP01Support.fnvExpression(#exp);
    } else if (ctx.filename() != null) {
      // TODO TP01Support.fnvFilename(#fn);
    }
  }

  // XXX

  
  @Override
  public void enterAggregate_opt(Aggregate_optContext ctx) {
    // TODO action.addToSymbolScope(action.defineVariable(#id1, #id1, DECIMAL));
    // Ou integer depending on type
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
    // TODO action.frameInitializingStatement(#head);
  }

  @Override
  public void enterChoose_field(Choose_fieldContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    // TODO action.formItem(#fi);
  }

  @Override
  public void enterChoose_opt(Choose_optContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void exitChoosestate(ChoosestateContext ctx) {
    // TODO action.frameStatementEnd();
  }
  
  @Override
  public void enterClassstate(ClassstateContext ctx) {
    // TODO action.classState(#c, #abstractKw, #finalKw, #serializableKw);
  }
  
  @Override
  public void enterInterfacestate(InterfacestateContext ctx) {
    // TODO action.interfaceState(#i);
  }

  @Override
  public void exitClearstate(ClearstateContext ctx) {
    // TODO action.clearState(#c);
  }

  @Override
  public void enterCatchstate(CatchstateContext ctx) {
    // TODO action.scopeAdd(#b);
    // TODO action.addToSymbolScope(action.defineVariable(#id1, #id1));
    // TODO action.defAs(#as);
  }

  @Override
  public void exitCatchstate(CatchstateContext ctx) {
    // TODO action.scopeClose(#b);
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
    // TODO action.frameInitializingStatement(#head);
    for (Field_form_itemContext item : ctx.field_form_item()) {
      contextQualifiers.put(item, ContextQualifier.SYMBOL);
    }
    // TODO action.formItem(#fi);
  }
  
  @Override
  public void exitColorstate(ColorstateContext ctx) {
    // TODO action.frameStatementEnd();
  }
  
  @Override
  public void enterColumnformat_opt(Columnformat_optContext ctx) {
    if ((ctx.LEXAT() != null) && ( ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
      // TODO action.lexat(#af);
    }
  }
  
  @Override
  public void enterConstructorstate(ConstructorstateContext ctx) {
    // TODO action.structorBegin(#c);
  }

  @Override
  public void exitConstructorstate(ConstructorstateContext ctx) {
    // TODO action.structorEnd(#c);
  }

  @Override
  public void enterCopylobstate(CopylobstateContext ctx) {
    // TODO Identify expression, then ...
    // TODO action.noteReference(#ex, ContextQualifier.UPDATING);
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
    // TODO  Verifier sur tous les createXX que ça fonctionne avec un exprt
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
    // TODO stack.push(action.defineBrowse(#def, #id));
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
      // TODO action.formItem(#fi1);
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
    // TODO action.formItem(#fi2); 
  }

  @Override
  public void enterDefinebuttonstate(DefinebuttonstateContext ctx) {
    // TODO stack.push(action.defineSymbol(ABLNodeType.BUTTON, #def, #id));
  }

  @Override
  public void enterButton_opt(Button_optContext ctx) {
    if (ctx.like_field() != null) {
      contextQualifiers.put(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefinebuttonstate(DefinebuttonstateContext ctx) {
    // TODO action.addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinedatasetstate(DefinedatasetstateContext ctx) {
    // TODO stack.push(action.defineSymbol(ABLNodeType.DATASET, #def, #id))
    for (RecordContext record : ctx.record()) {
      contextQualifiers.put(record, ContextQualifier.INIT);
    }
  }

  @Override
  public void exitDefinedatasetstate(DefinedatasetstateContext ctx) {
    // TODO action.addToSymbolScope(stack.pop());
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
    // TODO stack.push(action.defineSymbol(ABLNodeType.DATASOURCE, #def, #id));
  }

  @Override
  public void exitDefinedatasourcestate(DefinedatasourcestateContext ctx) {
    // TODO action.addToSymbolScope(stack.pop());
  }

  @Override
  public void enterSource_buffer_phrase(Source_buffer_phraseContext ctx) {
    // TODO action.recordNameNode(ctx.record(), ContextQualifier.INIT);
    if (ctx.field() != null) {
      for (FieldContext fld : ctx.field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterDefineeventstate(DefineeventstateContext ctx) {
    // TODO action.eventBegin(#e, #id); stack.push(action.defineEvent(#def, #id));
  }

  @Override
  public void exitDefineeventstate(DefineeventstateContext ctx) {
    // TODO action.eventEnd(#e); action.addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefineframestate(DefineframestateContext ctx) {
    // TODO action.frameDef(#def, #id);
    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.SYMBOL);

    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitDefineframestate(DefineframestateContext ctx) {
    // TODO action.frameStatementEnd();
  }

  @Override
  public void enterDefineimagestate(DefineimagestateContext ctx) {
    // TODO stack.push(action.defineSymbol(ABLNodeType.IMAGE, #def, #id));
  }
  
  @Override
  public void enterDefineimage_opt(Defineimage_optContext ctx) {
    if (ctx.like_field() != null) {
      contextQualifiers.put(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefineimagestate(DefineimagestateContext ctx) {
    // TODO action.addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinemenustate(DefinemenustateContext ctx) {
    // TODO stack.push(action.defineSymbol(ABLNodeType.MENU, #def, #id));
  }

  @Override
  public void exitDefinemenustate(DefinemenustateContext ctx) {
    // TODO action.addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinepropertystate(DefinepropertystateContext ctx) {
    // TODO stack.push(action.defineVariable(#def, #id));
    // TODO action.defAs(#as);
    
  }
  
  @Override
  public void exitDefinepropertystate(DefinepropertystateContext ctx) {
    // TODO Vérifier le moment où le pop est effectué, ce n'est pas exactement le exit
    // TODO action.addToSymbolScope(stack.pop()); 
  }

  @Override
  public void enterDefineproperty_accessor(Defineproperty_accessorContext ctx) {
    // TODO Probably only if ctx.code_block != null
    // TODO action.propGetSetBegin(#b1);
  }

  @Override
  public void exitDefineproperty_accessor(Defineproperty_accessorContext ctx) {
    // TODO Probably only if ctx.code_block != null
    // TODO action.propGetSetEnd(#b1);
  }

  @Override
  public void enterDefinequerystate(DefinequerystateContext ctx) {
    // TODO stack.push(action.defineSymbol(ABLNodeType.QUERY, #def, #id));
    for (RecordContext record : ctx.record()) {
      contextQualifiers.put(record, ContextQualifier.INIT);
    }
  }
  
  @Override
  public void exitDefinequerystate(DefinequerystateContext ctx) {
    // TODO action.addToSymbolScope(stack.pop()); 
  }
  
  @Override
  public void enterDefinerectanglestate(DefinerectanglestateContext ctx) {
    // TODO stack.push(action.defineSymbol(ABLNodeType.RECTANGLE, #def, #id));
  }

  @Override
  public void enterRectangle_opt(Rectangle_optContext ctx) {
    if (ctx.like_field() != null) {
      contextQualifiers.put(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefinerectanglestate(DefinerectanglestateContext ctx) {
    // TODO action.addToSymbolScope(stack.pop());
  }

  @Override
  public void exitDefinestreamstate(DefinestreamstateContext ctx) {
    // TODO action.addToSymbolScope(action.defineSymbol(ABLNodeType.STREAM, #def, #id));
  }

  @Override
  public void enterDefinesubmenustate(DefinesubmenustateContext ctx) {
    // TODO stack.push(action.defineSymbol(ABLNodeType.SUBMENU, #def, #id));
  }

  @Override
  public void exitDefinesubmenustate(DefinesubmenustateContext ctx) {
    // TODO action.addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinetemptablestate(DefinetemptablestateContext ctx) {
    // TODO action.defineTempTable(#def, #id);
    // TODO action.defineBuffer(#bt, #bt, #id, false);
  }

  @Override
  public void exitDefinetemptablestate(DefinetemptablestateContext ctx) {
    // TODO action.postDefineTempTable(#def, #id);
  }
  
  @Override
  public void enterDef_table_like(Def_table_likeContext ctx) {
    // TODO action.defineTableLike(#rec); 
    // TODO action.defineUseIndex(#rec, #id);
  }

  @Override
  public void enterDef_table_field(Def_table_fieldContext ctx) {
    // TODO stack.push(action.defineTableFieldInitialize(#id));
  }

  @Override
  public void exitDef_table_field(Def_table_fieldContext ctx) {
    // TODO action.defineTableFieldFinalize(stack.pop());
  }

  @Override
  public void enterDef_table_index(Def_table_indexContext ctx) {
    // TODO TP01Support.defineIndexInitialize(#id, #unq, #prim, #word);
    // TODO TP01Support.defineIndexField(#fld);
  }

  @Override
  public void enterDefineworktablestate(DefineworktablestateContext ctx) {
    // TODO TP01Support.defineWorktable(#def, #id);
  }

  @Override
  public void enterDefinevariablestate(DefinevariablestateContext ctx) {
    // TODO TP01Support.stack.push(action.defineVariable(#def, #id));
    // TODO Vérifier que les modificateurs sont bien là
  }

  @Override
  public void exitDefinevariablestate(DefinevariablestateContext ctx) {
    // TODO TP01Support.addToSymbolScope(stack.pop()); 
  }

  @Override
  public void enterDeletestate(DeletestateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterDestructorstate(DestructorstateContext ctx) {
    // TODO TP01Support.structorBegin(#d);
  }

  @Override
  public void exitDestructorstate(DestructorstateContext ctx) {
    // TODO TP01Support.structorEnd(#d);
  }

  @Override
  public void enterDisablestate(DisablestateContext ctx) {
    // TODO TP01Support.frameEnablingStatement(#head);
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
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void enterDisabletriggersstate(DisabletriggersstateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.SYMBOL);
  }

  @Override
  public void enterDisplaystate(DisplaystateContext ctx) {
    // TODO TP01Support.frameInitializingStatement(#head);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitDisplaystate(DisplaystateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
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
    // TODO TP01Support.blockBegin(#r);
    // TODO TP01Support.frameBlockCheck(#r);
    
    // TODO A revoir, il faut que ce soit fait avant d'entrer dans le code_block
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void exitDostate(DostateContext ctx) {
    // TODO TP01Support.blockEnd();
  }

  @Override
  public void enterDownstate(DownstateContext ctx) {
    // TODO TP01Support.frameEnablingStatement(#head);

  }

  @Override
  public void exitDownstate(DownstateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void enterEmptytemptablestate(EmptytemptablestateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.TEMPTABLESYMBOL);
  }

  @Override
  public void enterEnablestate(EnablestateContext ctx) {
    // TODO TP01Support.frameEnablingStatement(#head);
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
    // TODO TP01Support.frameStatementEnd();
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
    // TODO TP01Support.defExtent(#ex);
  }

  @Override
  public void enterFieldoption(FieldoptionContext ctx) {
    if (ctx.AS() != null) {
      // TODO TP01Support.defAs();
    } else if (ctx.LIKE() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);

      // TODO TP01Support.defLike();
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
    // r.setDefOrIdNode(blockNode);
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
        JPNode node = (JPNode) forwardScope.getRootBlock().getNode();
        Routine routine = (Routine) node.getSymbol();
        scopeSwap(forwardScope);

        // Weird (already set at the beginning)
        blockNode.setBlock(currentBlock);
        blockNode.setSymbol(routine);
        // routine.setDefOrIdNode(blocknode);
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
    currentBlock = (Block) popBlock();
  }

  public void scopeAdd(JPNode anode) {
    LOG.trace("Entering scopeAdd {}", anode);
    BlockNode blockNode = (BlockNode) anode;
    currentScope = currentScope.addScope();
    currentBlock = (Block) pushBlock(new Block(currentScope, blockNode));
    currentScope.setRootBlock(currentBlock);
    blockNode.setBlock(currentBlock);
  }

  public void blockBegin(JPNode blockAST) {
    LOG.trace("Entering blockBegin {}", blockAST);
    BlockNode blockNode = (BlockNode) blockAST;
    currentBlock = (Block) pushBlock(new Block(currentBlock, blockNode));
    blockNode.setBlock(currentBlock);
  }

  public void frameBlockCheck(JPNode ast) {
    LOG.trace("Entering frameBlockCheck {}", ast);
    frameStack.nodeOfBlock(ast, currentBlock);
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
    currentBlock = (Block) pushBlock(scope.getRootBlock());
  }

  public void defLike(JPNode likeNode) {
    LOG.trace("Entering defLike {}", likeNode);
    // currSymbol.setLikeNode(likeNode);
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

}
