package org.prorefactor.proparse.antlr4;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeParser extends ProparseBaseListener {
  private static final Logger LOG = LoggerFactory.getLogger(TreeParser.class);

  private static final String LOG_ADDING_QUAL_TO = "{}> Adding {} qualifier to '{}'";

  private final ParserSupport support;
  private final RefactorSession refSession;
  private final TreeParserRootSymbolScope rootScope;

  private int currentLevel;

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

  private boolean formItem2 = false;

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
    this.currentScope = rootScope;
  }

  public TreeParserRootSymbolScope getRootScope() {
    return rootScope;
  }

  private void setContextQualifier(ParseTree ctx, ContextQualifier cq) {
    if ((cq == null) || (ctx == null))
      return;
    if (LOG.isDebugEnabled()) {
      LOG.debug(LOG_ADDING_QUAL_TO, indent(), cq, ctx.getText());
    }
    contextQualifiers.put(ctx, cq);
  }

  @Override
  public void enterProgram(ProgramContext ctx) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Entering program", indent());

    if (rootRoutine != null) {
      // Executing TreeParser more than once on a ParseTree would just result in meaningless result
      throw new IllegalStateException("TreeParser has already been executed...");
    }

    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    currentBlock = pushBlock(new Block(rootScope, blockNode));
    rootScope.setRootBlock(currentBlock);
    blockNode.setBlock(currentBlock);

    Routine routine = new Routine("", rootScope, rootScope);
    routine.setProgressType(ABLNodeType.PROGRAM_ROOT);
    routine.setDefinitionNode(blockNode);
    blockNode.setSymbol(routine);

    rootScope.add(routine);
    currentRoutine = routine;
    rootRoutine = routine;
  }

  @Override
  public void exitProgram(ProgramContext ctx) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Exiting program", indent());
  }

  @Override
  public void enterBlock_for(Block_forContext ctx) {
    for (RecordContext record : ctx.record()) {
      setContextQualifier(record, ContextQualifier.BUFFERSYMBOL);
    }
  }

  @Override
  public void exitBlock_for(Block_forContext ctx) {
    for (RecordContext record : ctx.record()) {
      if (LOG.isDebugEnabled())
        LOG.debug("{}> Adding strong buffer scope for {} to current block", indent(), record.getText());

      RecordNameNode recNode = (RecordNameNode) support.getNode(record);
      currentBlock.addStrongBufferScope(recNode);
    }
  }

  @Override
  public void enterRecord(RecordContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    if (qual != null) {
      recordNameNode((RecordNameNode) support.getNode(ctx), qual);
    } else {
      LOG.info("No context qualifier for {}, I probably should do something...", ctx.getText());
    }
  }

  @Override
  public void enterBlock_opt_iterator(Block_opt_iteratorContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.REFUP);
    setContextQualifier(ctx.expression(0), ContextQualifier.REF);
    setContextQualifier(ctx.expression(1), ContextQualifier.REF);
  }

  @Override
  public void enterBlock_opt_while(Block_opt_whileContext ctx) {
    setContextQualifier(ctx.expression(), ContextQualifier.REF);
  }

  @Override
  public void enterBlock_preselect(Block_preselectContext ctx) {
    setContextQualifier(ctx.for_record_spec(), ContextQualifier.INITWEAK);
  }

  @Override
  public void enterMemoryManagementFunc(MemoryManagementFuncContext ctx) {
    if ((ctx.PUTBITS() != null) || (ctx.PUTBYTE() != null) || (ctx.PUTBYTES() != null) || (ctx.PUTDOUBLE() != null)
        || (ctx.PUTFLOAT() != null) || (ctx.PUTINT64() != null) || (ctx.PUTLONG() != null) || (ctx.PUTSHORT() != null)
        || (ctx.PUTSTRING() != null) || (ctx.PUTUNSIGNEDLONG() != null) || (ctx.PUTUNSIGNEDSHORT() != null)
        || (ctx.SETPOINTERVALUE() != null)) {
      setContextQualifier(ctx.funargs().expression(0), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterFunargs(FunargsContext ctx) {
    // TODO Check conflict with previous function (memory mgmt function)
    for (ExpressionContext exp : ctx.expression()) {
      setContextQualifier(exp, ContextQualifier.REF);
    }
  }

  @Override
  public void enterRecordfunc(RecordfuncContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.REF);
  }

  @Override
  public void enterParameterBufferFor(ParameterBufferForContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.REF);
  }

  @Override
  public void enterParameterBufferRecord(ParameterBufferRecordContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.INIT);
  }

  @Override
  public void enterParameterOther(ParameterOtherContext ctx) {
    if (ctx.OUTPUT() != null) {
      setContextQualifier(ctx.parameter_arg(), ContextQualifier.UPDATING);
    } else if (ctx.INPUTOUTPUT() != null) {
      setContextQualifier(ctx.parameter_arg(), ContextQualifier.REFUP);
    } else {
      setContextQualifier(ctx.parameter_arg(), ContextQualifier.REF);
    }
  }

  @Override
  public void enterParameterArgTableHandle(ParameterArgTableHandleContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.INIT);
    noteReference(support.getNode(ctx.field()), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterParameterArgTable(ParameterArgTableContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.TEMPTABLESYMBOL);
  }

  @Override
  public void enterParameterArgDatasetHandle(ParameterArgDatasetHandleContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.INIT);
    noteReference(support.getNode(ctx.field()), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterParameterArgAs(ParameterArgAsContext ctx) {
    // TODO ?
  }

  @Override
  public void enterParameterArgComDatatype(ParameterArgComDatatypeContext ctx) {
    setContextQualifier(ctx.expression(), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterFunctionParamBufferFor(FunctionParamBufferForContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.SYMBOL);
  }

  @Override
  public void exitFunctionParamBufferFor(FunctionParamBufferForContext ctx) {
    if (ctx.bn != null) {
      defineBuffer(ctx, support.getNode(ctx), support.getNode(ctx), ctx.bn.getText(), support.getNode(ctx.record()), true);
    }
  }

  @Override
  public void exitFunctionParamStandardAs(FunctionParamStandardAsContext ctx) {
    addToSymbolScope(defineVariable(ctx, support.getNode(ctx), ctx.n.getText(), true));
    defAs(ctx.asDataTypeVar());
  }

  @Override
  public void enterFunctionParamStandardLike(FunctionParamStandardLikeContext ctx) {
    stack.push(defineVariable(ctx, support.getNode(ctx), ctx.n2.getText(), true));
  }

  @Override
  public void exitFunctionParamStandardLike(FunctionParamStandardLikeContext ctx) {
    defLike(ctx, support.getNode(ctx.like_field().field()));
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterFunctionParamStandardTable(FunctionParamStandardTableContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.TEMPTABLESYMBOL);
  }

  @Override
  public void enterFunctionParamStandardTableHandle(FunctionParamStandardTableHandleContext ctx) {
    addToSymbolScope(defineVariable(ctx, support.getNode(ctx), ctx.hn.getText(), DataType.HANDLE, true));
  }

  @Override
  public void enterFunctionParamStandardDatasetHandle(FunctionParamStandardDatasetHandleContext ctx) {
    addToSymbolScope(defineVariable(ctx, support.getNode(ctx), ctx.hn2.getText(), DataType.HANDLE, true));
  }

  private void enterExpression(ExpressionContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    if (qual == null) qual = ContextQualifier.REF;
    for (ExprtContext c : ctx.getRuleContexts(ExprtContext.class)) {
      setContextQualifier(c, qual);
    }
    for (ExpressionContext c : ctx.getRuleContexts(ExpressionContext.class)) {
      setContextQualifier(c, qual);
    }
  }

  @Override
  public void enterExpressionMinus(ExpressionMinusContext ctx) {
    enterExpression(ctx);
  }

  @Override
  public void enterExpressionPlus(ExpressionPlusContext ctx) {
    enterExpression(ctx);
  }

  @Override
  public void enterExpressionOp1(ExpressionOp1Context ctx) {
    enterExpression(ctx);
  }
  
  @Override
  public void enterExpressionOp2(ExpressionOp2Context ctx) {
    enterExpression(ctx);
  }
  
  @Override
  public void enterExpressionComparison(ExpressionComparisonContext ctx) {
    enterExpression(ctx);
  }
  
  @Override
  public void enterExpressionStringComparison(ExpressionStringComparisonContext ctx) {
    enterExpression(ctx);
  }
  
  @Override
  public void enterExpressionNot(ExpressionNotContext ctx) {
    enterExpression(ctx);
  }
  
  @Override
  public void enterExpressionAnd(ExpressionAndContext ctx) {
    enterExpression(ctx);
  }
  
  @Override
  public void enterExpressionOr(ExpressionOrContext ctx) {
    enterExpression(ctx);
  }
  
  @Override
  public void enterExpressionExprt(ExpressionExprtContext ctx) {
    enterExpression(ctx);
  }

  // Expression term
  
  @Override
  public void enterExprtNoReturnValue(ExprtNoReturnValueContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    setContextQualifier(ctx.s_widget(), qual);
    setContextQualifier(ctx.attr_colon(), qual);
  }

  @Override
  public void enterExprtWidName(ExprtWidNameContext ctx) {
    widattr(ctx, support.getNode(ctx.widname()), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterExprtExprt2(ExprtExprt2Context ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    if ((ctx.attr_colon() != null) && (ctx.exprt2() instanceof Exprt2FieldContext)) {
      widattr(ctx, null, qual);
    } else {
      setContextQualifier(ctx.exprt2(), qual);
      if (ctx.attr_colon() != null)
        setContextQualifier(ctx.attr_colon(), qual);
    }
  }

  @Override
  public void enterExprt2ParenExpr(Exprt2ParenExprContext ctx) {
    setContextQualifier(ctx.expression(), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterExprt2Field(Exprt2FieldContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.REF);
  }

  @Override
  public void enterWidattrExprt2(WidattrExprt2Context ctx) {
    widattr(ctx, support.getNode(ctx.exprt2()), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterWidattrWidName(WidattrWidNameContext ctx) {
    widattr(ctx, support.getNode(ctx.widname()), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterGwidget(GwidgetContext ctx) {
    if (ctx.inuic() != null) {
      if (ctx.inuic().FRAME() != null) {
        frameRef(support.getNode(ctx.inuic()).nextNode().nextNode());
      } else if (ctx.inuic().BROWSE() != null) {
        browseRef(support.getNode(ctx.inuic()).nextNode().nextNode());
      }
    }
  }

  @Override
  public void enterS_widget(S_widgetContext ctx) {
    if (ctx.field() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.REF);
    }
  }

  @Override
  public void enterWidname(WidnameContext ctx) {
    if (ctx.FRAME() != null) {
      frameRef(support.getNode(ctx).nextNode());
    } else if (ctx.BROWSE() != null) {
      browseRef(support.getNode(ctx).nextNode());
    } else if (ctx.BUFFER() != null) {
      bufferRef(ctx.filn().getText());
    } else if (ctx.FIELD() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.REF);
    }
  }

  @Override
  public void enterAggregate_opt(Aggregate_optContext ctx) {
    // TODO Verifier le nom de la variable
    addToSymbolScope(defineVariable(ctx, support.getNode(ctx.accum_what()), "", DataType.DECIMAL, false));
    // TODO Ou integer depending on type
  }

  @Override
  public void enterAssignment_list(Assignment_listContext ctx) {
    if (ctx.record() != null) {
      setContextQualifier(ctx.record(), ContextQualifier.UPDATING);
    }
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void enterAssignstate2(Assignstate2Context ctx) {
    if (ctx.widattr() != null) {
      setContextQualifier(ctx.widattr(), ContextQualifier.UPDATING);
    } else if (ctx.field() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
    }
    setContextQualifier(ctx.expression(), ContextQualifier.REF);
  }

  @Override
  public void enterAssign_equal(Assign_equalContext ctx) {
    if (ctx.widattr() != null) {
      setContextQualifier(ctx.widattr(), ContextQualifier.UPDATING);
    } else if (ctx.field() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterReferencepoint(ReferencepointContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
  }

  @Override
  public void enterBuffercomparestate(BuffercomparestateContext ctx) {
    setContextQualifier(ctx.record(0), ContextQualifier.REF);
    
    if ((ctx.except_using_fields() != null) && (ctx.except_using_fields().field() != null)) {
      ContextQualifier qual = ctx.except_using_fields().USING() == null ? ContextQualifier.SYMBOL : ContextQualifier.REF;
      for (FieldContext field : ctx.except_using_fields().field()) {
        setContextQualifier(field, qual);
        nameResolution.put(field, TableNameResolution.LAST);
      }
    }

    setContextQualifier(ctx.record(1), ContextQualifier.REF);
  }

  @Override
  public void enterBuffercompare_save(Buffercompare_saveContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterBuffercopystate(BuffercopystateContext ctx) {
    setContextQualifier(ctx.record(0), ContextQualifier.REF);
    
    if ((ctx.except_using_fields() != null) && (ctx.except_using_fields().field() != null)) {
      ContextQualifier qual = ctx.except_using_fields().USING() == null ? ContextQualifier.SYMBOL : ContextQualifier.REF;
      for (FieldContext field : ctx.except_using_fields().field()) {
        setContextQualifier(field, qual);
        nameResolution.put(field, TableNameResolution.LAST);
      }
    }

    setContextQualifier(ctx.record(1), ContextQualifier.UPDATING);
  }

  @Override
  public void enterChoosestate(ChoosestateContext ctx) {
    frameInitializingStatement(ctx);
  }

  @Override
  public void enterChoose_field(Choose_fieldContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
    frameStack.formItem(support.getNode(ctx.field()));
  }

  @Override
  public void enterChoose_opt(Choose_optContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void exitChoosestate(ChoosestateContext ctx) {
    frameStack.statementEnd();
  }
  
  @Override
  public void enterClassstate(ClassstateContext ctx) {
    rootScope.setClassName(ctx.tn.getText());
    rootScope.setTypeInfo(refSession.getTypeInfo(ctx.tn.getText()));
    rootScope.setAbstractClass(!ctx.ABSTRACT().isEmpty());
    rootScope.setSerializableClass(!ctx.SERIALIZABLE().isEmpty());
    rootScope.setFinalClass(!ctx.FINAL().isEmpty());
  }
  
  @Override
  public void enterInterfacestate(InterfacestateContext ctx) {
    rootScope.setClassName(ctx.name.getText());
    rootScope.setTypeInfo(refSession.getTypeInfo(ctx.name.getText()));
    rootScope.setInterface(true);
  }

  @Override
  public void exitClearstate(ClearstateContext ctx) {
    if (ctx.frame_widgetname() != null) {
      frameStack.simpleFrameInitStatement(ctx, support.getNode(ctx), support.getNode(ctx.frame_widgetname()), currentBlock);
    }
  }

  @Override
  public void enterCatchstate(CatchstateContext ctx) {
    scopeAdd(support.getNode(ctx));
    addToSymbolScope(defineVariable(ctx, support.getNode(ctx).getFirstChild(), ctx.n.getText()));
    defAs(ctx.class_type_name());
  }

  @Override
  public void exitCatchstate(CatchstateContext ctx) {
    scopeClose(support.getNode(ctx));
  }
  
  @Override
  public void enterClosestored_field(Closestored_fieldContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.REF);
  }
  
  @Override
  public void enterClosestored_where(Closestored_whereContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.REF);
  }
  
  @Override
  public void enterColorstate(ColorstateContext ctx) {
    frameInitializingStatement(ctx);
    for (Field_form_itemContext item : ctx.field_form_item()) {
      setContextQualifier(item, ContextQualifier.SYMBOL);
      frameStack.formItem(support.getNode(item));
    }
  }
  
  @Override
  public void exitColorstate(ColorstateContext ctx) {
    frameStack.statementEnd();
  }
  
  @Override
  public void exitColumnformat_opt(Columnformat_optContext ctx) {
    if ((ctx.LEXAT() != null) && ( ctx.field() != null)) {
      setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
      frameStack.lexAt(support.getNode(ctx.field()));
    }
  }
  
  @Override
  public void enterConstructorstate(ConstructorstateContext ctx) {
    /*
     * Since 'structors don't have a name, we don't add them to any sort of map in the parent scope.
     */
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope;
    scopeAdd(blockNode);

    // 'structors don't have names, so use empty string.
    Routine r = new Routine("", definingScope, currentScope);
    r.setProgressType(blockNode.getNodeType());
    r.setDefinitionNode(blockNode);
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
    setContextQualifier(ctx.expression(0), ContextQualifier.REF);
    setContextQualifier(ctx.expression(1), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatestate(CreatestateContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreate_whatever_state(Create_whatever_stateContext ctx) {
    setContextQualifier(ctx.exprt(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatebrowsestate(CreatebrowsestateContext ctx) {
    setContextQualifier(ctx.exprt(), ContextQualifier.UPDATING);
  }
  
  @Override
  public void enterCreatebufferstate(CreatebufferstateContext ctx) {
    setContextQualifier(ctx.exprt(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatequerystate(CreatequerystateContext ctx) {
    setContextQualifier(ctx.exprt(), ContextQualifier.UPDATING);
    }
  
  @Override
  public void enterCreateserverstate(CreateserverstateContext ctx) {
    setContextQualifier(ctx.exprt(), ContextQualifier.UPDATING);
    }
  
  @Override
  public void enterCreateserversocketstate(CreateserversocketstateContext ctx) {
    setContextQualifier(ctx.exprt(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatetemptablestate(CreatetemptablestateContext ctx) {
    setContextQualifier(ctx.exprt(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCreatewidgetstate(CreatewidgetstateContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterCanfindfunc(CanfindfuncContext ctx) {
    RecordNameNode recordNode = (RecordNameNode) support.getNode(ctx.recordphrase().record());
    // Keep a ref to the current block...
    Block b = currentBlock;
    // ...create a can-find scope and block (assigns currentBlock)...
    scopeAdd(support.getNode(ctx));
    // ...and then set this "can-find block" to use it as its parent.
    currentBlock.setParent(b);
    String buffName = ctx.recordphrase().record().getText();
    ITable table;
    boolean isDefault;
    TableBuffer tableBuffer = currentScope.lookupBuffer(buffName);
    if (tableBuffer != null) {
      table = tableBuffer.getTable();
      isDefault = tableBuffer.isDefault();
      // Notify table buffer that it's used in a CAN-FIND
      tableBuffer.noteReference(ContextQualifier.INIT);
    } else {
      table = refSession.getSchema().lookupTable(buffName);
      isDefault = true;
    }
    TableBuffer newBuff = currentScope.defineBuffer(isDefault ? "" : buffName, table);
    recordNode.setTableBuffer(newBuff);
    currentBlock.addHiddenCursor(recordNode);

    setContextQualifier(ctx.recordphrase().record(), ContextQualifier.INIT);
  }

  @Override
  public void exitCanfindfunc(CanfindfuncContext ctx) {
    scopeClose(support.getNode(ctx));
  }

  @Override
  public void enterDdegetstate(DdegetstateContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
    }
  
  @Override
  public void enterDdeinitiatestate(DdeinitiatestateContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
  }
  
  @Override
  public void enterDderequeststate(DderequeststateContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterDefinebrowsestate(DefinebrowsestateContext ctx) {
    stack.push(defineBrowse(ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }

  @Override
  public void exitDefinebrowsestate(DefinebrowsestateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDef_browse_display(Def_browse_displayContext ctx) {
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void enterDef_browse_display_items_or_record(Def_browse_display_items_or_recordContext ctx) {
    if (ctx.recordAsFormItem() != null) {
      setContextQualifier(ctx.recordAsFormItem(), ContextQualifier.INIT);
    }
  }

  @Override
  public void exitDef_browse_display_items_or_record(Def_browse_display_items_or_recordContext ctx) {
    if (ctx.recordAsFormItem() != null) {
      frameStack.formItem(support.getNode(ctx.recordAsFormItem()));
    }
    for (Def_browse_display_itemContext item :ctx.def_browse_display_item()) {
      frameStack.formItem(support.getNode(item));
    }
  }

  @Override
  public void enterDef_browse_enable(Def_browse_enableContext ctx) {
    if ((ctx.all_except_fields() != null) && (ctx.all_except_fields().except_fields() != null)) {
      for (FieldContext fld : ctx.all_except_fields().except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterDef_browse_enable_item(Def_browse_enable_itemContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
    frameStack.formItem(support.getNode(ctx));
  }

  @Override
  public void enterDefinebufferstate(DefinebufferstateContext ctx) {
    setContextQualifier(ctx.record(), ctx.TEMPTABLE() == null ? ContextQualifier.SYMBOL : ContextQualifier.TEMPTABLESYMBOL);
  }

  @Override
  public void enterFields_fields(Fields_fieldsContext ctx) {
    for (FieldContext fld : ctx.field()) {
      setContextQualifier(fld, ContextQualifier.SYMBOL);
      nameResolution.put(fld, TableNameResolution.LAST);
    }
  }

  @Override
  public void exitDefinebufferstate(DefinebufferstateContext ctx) {
    defineBuffer(ctx, support.getNode(ctx), support.getNode(ctx).getIdNode(), ctx.n.getText(), support.getNode(ctx.record()), false);
  }

  @Override
  public void enterDefinebuttonstate(DefinebuttonstateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.BUTTON, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }

  @Override
  public void enterButton_opt(Button_optContext ctx) {
    if (ctx.like_field() != null) {
      setContextQualifier(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefinebuttonstate(DefinebuttonstateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinedatasetstate(DefinedatasetstateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.DATASET, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
    for (RecordContext record : ctx.record()) {
      setContextQualifier(record, ContextQualifier.INIT);
    }
  }

  @Override
  public void exitDefinedatasetstate(DefinedatasetstateContext ctx) {
    addToSymbolScope(stack.pop());
  }
  
  @Override
  public void enterData_relation(Data_relationContext ctx) {
    for (RecordContext record : ctx.record()) {
      setContextQualifier(record, ContextQualifier.INIT);
    }
  }

  @Override
  public void enterParent_id_relation(Parent_id_relationContext ctx) {
    for (RecordContext record : ctx.record()) {
      setContextQualifier(record, ContextQualifier.INIT);
    }
    for (FieldContext fld : ctx.field()) {
      setContextQualifier(fld, ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void enterField_mapping_phrase(Field_mapping_phraseContext ctx) {
    for (int zz = 0; zz < ctx.field().size(); zz += 2) {
      setContextQualifier(ctx.field().get(zz), ContextQualifier.SYMBOL);
      nameResolution.put(ctx.field().get(zz), TableNameResolution.PREVIOUS);
      setContextQualifier(ctx.field().get(zz + 1), ContextQualifier.SYMBOL);
      nameResolution.put(ctx.field().get(zz + 1), TableNameResolution.LAST);
    }
  }

  @Override
  public void enterDefinedatasourcestate(DefinedatasourcestateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.DATASOURCE, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }

  @Override
  public void exitDefinedatasourcestate(DefinedatasourcestateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterSource_buffer_phrase(Source_buffer_phraseContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.INIT);
    if (ctx.field() != null) {
      for (FieldContext fld : ctx.field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void enterDefineeventstate(DefineeventstateContext ctx) {
    this.inDefineEvent = true;
    stack.push(defineEvent(ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }

  @Override
  public void exitDefineeventstate(DefineeventstateContext ctx) {
    this.inDefineEvent = false;
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefineframestate(DefineframestateContext ctx) {
    formItem2 = true;
    frameStack.nodeOfDefineFrame(ctx, support.getNode(ctx), null, ctx.identifier().getText(), currentScope);
    setContextQualifier(ctx.form_items_or_record(), ContextQualifier.SYMBOL);

    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void exitDefineframestate(DefineframestateContext ctx) {
    frameStack.statementEnd();
    formItem2 = false;
  }

  @Override
  public void enterDefineimagestate(DefineimagestateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.IMAGE, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }
  
  @Override
  public void enterDefineimage_opt(Defineimage_optContext ctx) {
    if (ctx.like_field() != null) {
      setContextQualifier(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefineimagestate(DefineimagestateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinemenustate(DefinemenustateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.MENU, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }

  @Override
  public void exitDefinemenustate(DefinemenustateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefineparameterstatesub1(Defineparameterstatesub1Context ctx) {
    setContextQualifier(ctx.record(), ctx.TEMPTABLE() == null ? ContextQualifier.SYMBOL : ContextQualifier.TEMPTABLESYMBOL);
  }

  @Override
  public void exitDefineparameterstatesub1(Defineparameterstatesub1Context ctx) {
    defineBuffer(ctx.parent, support.getNode(ctx.parent), support.getNode(ctx.parent).getIdNode(), ctx.bn.getText(), support.getNode(ctx.record()), true);
  }

  @Override
  public void enterDefineParameterStatementSub2Variable(DefineParameterStatementSub2VariableContext ctx) {
    stack.push(defineVariable(ctx.parent, support.getNode(ctx.parent), ctx.identifier().getText(), true));
  }

  @Override
  public void exitDefineParameterStatementSub2Variable(DefineParameterStatementSub2VariableContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefineParameterStatementSub2VariableLike(DefineParameterStatementSub2VariableLikeContext ctx) {
    stack.push(defineVariable(ctx.parent, support.getNode(ctx.parent), ctx.identifier().getText(), true));
  }

  @Override
  public void exitDefineParameterStatementSub2VariableLike(DefineParameterStatementSub2VariableLikeContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefineparam_var(Defineparam_varContext ctx) {
    // TODO defAs()
  }
  
  @Override
  public void exitDefineparam_var_like(Defineparam_var_likeContext ctx) {
    defLike(ctx, support.getNode(ctx.field()));
  }

  @Override
  public void enterDefineParameterStatementSub2Table(DefineParameterStatementSub2TableContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.TEMPTABLESYMBOL);
  }
  
  @Override
  public void enterDefineParameterStatementSub2TableHandle(DefineParameterStatementSub2TableHandleContext ctx) {
    addToSymbolScope(defineVariable(ctx, support.getNode(ctx.parent), ctx.pn2.getText(), DataType.HANDLE, true));
    }
  

  @Override
  public void enterDefineParameterStatementSub2DatasetHandle(DefineParameterStatementSub2DatasetHandleContext ctx) {
    addToSymbolScope(defineVariable(ctx, support.getNode(ctx.parent), ctx.dsh.getText(), DataType.HANDLE, true));
  }

  @Override
  public void enterDefinepropertystate(DefinepropertystateContext ctx) {
    stack.push(defineVariable(ctx, support.getNode(ctx), ctx.n.getText()));
    defAs(ctx.datatype());
    
  }
  
  @Override
  public void exitDefinepropertystate(DefinepropertystateContext ctx) {
    // TODO Vérifier le moment où le pop est effectué, ce n'est pas exactement le exit
    addToSymbolScope(stack.pop()); 
  }

  @Override
  public void enterDefineproperty_accessor_getblock(Defineproperty_accessor_getblockContext ctx) {
    if (ctx.code_block() != null)
      propGetSetBegin(ctx, support.getNode(ctx));
  }

  @Override
  public void enterDefineproperty_accessor_setblock(Defineproperty_accessor_setblockContext ctx) {
    if (ctx.code_block() != null)
      propGetSetBegin(ctx, support.getNode(ctx));
  }

  @Override
  public void exitDefineproperty_accessor_getblock(Defineproperty_accessor_getblockContext ctx) {
    if (ctx.code_block() != null)
      propGetSetEnd(support.getNode(ctx));
  }

  @Override
  public void exitDefineproperty_accessor_setblock(Defineproperty_accessor_setblockContext ctx) {
    if (ctx.code_block() != null)
      propGetSetEnd(support.getNode(ctx));
  }

  @Override
  public void enterDefinequerystate(DefinequerystateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.QUERY, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
    for (RecordContext record : ctx.record()) {
      setContextQualifier(record, ContextQualifier.INIT);
    }
  }
  
  @Override
  public void exitDefinequerystate(DefinequerystateContext ctx) {
    addToSymbolScope(stack.pop()); 
  }
  
  @Override
  public void enterDefinerectanglestate(DefinerectanglestateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.RECTANGLE, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }

  @Override
  public void enterRectangle_opt(Rectangle_optContext ctx) {
    if (ctx.like_field() != null) {
      setContextQualifier(ctx.like_field().field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitDefinerectanglestate(DefinerectanglestateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void exitDefinestreamstate(DefinestreamstateContext ctx) {
    addToSymbolScope(defineSymbol(ABLNodeType.STREAM, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }

  @Override
  public void enterDefinesubmenustate(DefinesubmenustateContext ctx) {
    stack.push(defineSymbol(ABLNodeType.SUBMENU, ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText()));
  }

  @Override
  public void exitDefinesubmenustate(DefinesubmenustateContext ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterDefinetemptablestate(DefinetemptablestateContext ctx) {
    defineTempTable(ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText());
  }

  @Override
  public void enterDef_table_beforetable(Def_table_beforetableContext ctx) {
    defineBuffer(ctx, support.getNode(ctx), support.getNode(ctx), ctx.i.getText(), support.getNode(ctx.parent), false);
  }

  @Override
  public void exitDefinetemptablestate(DefinetemptablestateContext ctx) {
    postDefineTempTable();
  }

  @Override
  public void enterDef_table_like(Def_table_likeContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.SYMBOL);
  }

  @Override
  public void exitDef_table_like(Def_table_likeContext ctx) {
    defineTableLike(ctx.record());
    for (Def_table_useindexContext useIndex : ctx.def_table_useindex()) {
      defineUseIndex(support.getNode(ctx.record()), useIndex.identifier().getText());
    }
  }

  @Override
  public void enterDef_table_field(Def_table_fieldContext ctx) {
    stack.push(defineTableFieldInitialize(ctx, support.getNode(ctx), ctx.identifier().getText()));
  }

  @Override
  public void exitDef_table_field(Def_table_fieldContext ctx) {
    defineTableFieldFinalize(stack.pop());
  }

  @Override
  public void enterDef_table_index(Def_table_indexContext ctx) {
    defineIndexInitialize(ctx.identifier(0).getText(), ctx.UNIQUE() != null, ctx.PRIMARY() != null, false);
    for (int zz = 1; zz < ctx.identifier().size(); zz++) {
      defineIndexField(ctx.identifier(zz).getText());
    }
  }

  @Override
  public void enterDefineworktablestate(DefineworktablestateContext ctx) {
    defineWorktable(ctx, support.getNode(ctx), support.getNode(ctx.identifier()), ctx.identifier().getText());
  }

  @Override
  public void enterDefinevariablestate(DefinevariablestateContext ctx) {
    stack.push(defineVariable(ctx, support.getNode(ctx), ctx.n.getText()));
    // TODO Vérifier que les modificateurs sont bien là
  }

  @Override
  public void exitDefinevariablestate(DefinevariablestateContext ctx) {
    addToSymbolScope(stack.pop()); 
  }

  @Override
  public void enterDeletestate(DeletestateContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterDestructorstate(DestructorstateContext ctx) {
    /*
     * Since 'structors don't have a name, we don't add them to any sort of map in the parent scope.
     */
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope;
    scopeAdd(blockNode);

    // 'structors don't have names, so use empty string.
    Routine r = new Routine("", definingScope, currentScope);
    r.setProgressType(blockNode.getNodeType());
    r.setDefinitionNode(blockNode);
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
    formItem2 = true;
    frameEnablingStatement(ctx);
    for (Form_itemContext form : ctx.form_item()) {
      setContextQualifier(form, ContextQualifier.SYMBOL);
    }
    if ((ctx.all_except_fields() != null) && (ctx.all_except_fields().except_fields() != null)) {
      for (FieldContext fld : ctx.all_except_fields().except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitDisablestate(DisablestateContext ctx) {
    frameStack.statementEnd();
    formItem2 = false;
  }

  @Override
  public void enterDisabletriggersstate(DisabletriggersstateContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.SYMBOL);
  }

  @Override
  public void enterDisplaystate(DisplaystateContext ctx) {
    frameInitializingStatement(ctx);
    setContextQualifier(ctx.display_items_or_record(), ContextQualifier.SYMBOL);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void enterDisplay_items_or_record(Display_items_or_recordContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    for (int kk = 0; kk < ctx.getChildCount(); kk++) {
      if (ctx.getChild(kk) instanceof RecordAsFormItemContext)
        setContextQualifier(ctx.getChild(kk), ContextQualifier.BUFFERSYMBOL);
      else
        setContextQualifier(ctx.getChild(kk), qual);
    }
  }

  @Override
  public void enterDisplay_item(Display_itemContext ctx) {
    if (ctx.expression() != null) {
      setContextQualifier(ctx.expression(), contextQualifiers.removeFrom(ctx));
    }
  }

  @Override
  public void exitDisplay_item(Display_itemContext ctx) {
    if (ctx.expression() != null) {
      frameStack.formItem(support.getNode(ctx));
    }
  }

  @Override
  public void exitDisplaystate(DisplaystateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterField_equal_dynamic_new(Field_equal_dynamic_newContext ctx) {
    if (ctx.widattr() != null) {
      setContextQualifier(ctx.widattr(), ContextQualifier.UPDATING);
    } else if (ctx.field() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterDostate(DostateContext ctx) {
    blockBegin(ctx);
    frameBlockCheck(support.getNode(ctx));
  }

  @Override
  public void enterDostatesub(DostatesubContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void exitDostate(DostateContext ctx) {
    blockEnd();
  }

  @Override
  public void enterDownstate(DownstateContext ctx) {
    frameEnablingStatement(ctx);
  }

  @Override
  public void exitDownstate(DownstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterEmptytemptablestate(EmptytemptablestateContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.TEMPTABLESYMBOL);
  }

  @Override
  public void enterEnablestate(EnablestateContext ctx) {
    formItem2 = true;
    frameEnablingStatement(ctx);

    for (Form_itemContext form : ctx.form_item()) {
      setContextQualifier(form, ContextQualifier.SYMBOL);
    }
    if ((ctx.all_except_fields() != null) && (ctx.all_except_fields().except_fields() != null)) {
      for (FieldContext fld : ctx.all_except_fields().except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitEnablestate(EnablestateContext ctx) {
    frameStack.statementEnd();
    formItem2 = false;
  }

  @Override
  public void enterExportstate(ExportstateContext ctx) {
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void enterExtentphrase(ExtentphraseContext ctx) {
    // TODO Warning: action only has to be applied in limited number of cases i.e. rule extentphrase_def_symbol
    if (ctx.constant() != null)
      defExtent(ctx.constant().getText());
  }

  @Override
  public void enterFieldoption(FieldoptionContext ctx) {
    if (ctx.AS() != null) {
      defAs(ctx.asDataTypeField());
    } else if (ctx.LIKE() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void exitFieldoption(FieldoptionContext ctx) {
    if (ctx.LIKE() != null) {
      defLike(ctx.field(), support.getNode(ctx.field()));
    }
  }

  @Override
  public void enterFindstate(FindstateContext ctx) {
    setContextQualifier(ctx.recordphrase().record(), ContextQualifier.INIT);
  }
  
  @Override
  public void enterForstate(ForstateContext ctx) {
    blockBegin(ctx);
    frameBlockCheck(support.getNode(ctx));

    setContextQualifier(ctx.for_record_spec(), ContextQualifier.INITWEAK);
  }

  @Override
  public void enterForstate_sub(Forstate_subContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void exitForstate(ForstateContext ctx) {
    blockEnd();
  }

  @Override
  public void enterFor_record_spec(For_record_specContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    for (RecordphraseContext rec : ctx.recordphrase()) {
      setContextQualifier(rec.record(), qual);
    }
  }

  @Override
  public void enterForm_item(Form_itemContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    if (ctx.field() != null) {
      setContextQualifier(ctx.field(), qual);
    } else if (ctx.recordAsFormItem() != null) {
      setContextQualifier(ctx.recordAsFormItem(), qual);
    }
    // TODO Il reste le cas text_opt (line 1306 de TreeParser01.g)
  }

  @Override
  public void exitForm_item(Form_itemContext ctx) {
    if ((ctx.field() != null) || (ctx.recordAsFormItem() != null)) {
      frameStack.formItem(support.getNode(ctx));
    }
  }

  @Override
  public void enterForm_items_or_record(Form_items_or_recordContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    for (int kk = 0; kk < ctx.getChildCount(); kk++) {
      if (formItem2 && (ctx.getChild(kk) instanceof RecordAsFormItemContext)&& (qual == ContextQualifier.SYMBOL))
        setContextQualifier(ctx.getChild(kk), ContextQualifier.BUFFERSYMBOL);
      else
        setContextQualifier(ctx.getChild(kk), qual);
    }
  }

  @Override
  public void enterRecordAsFormItem(RecordAsFormItemContext ctx) {
    setContextQualifier(ctx.record(), contextQualifiers.removeFrom(ctx));
  }

  @Override
  public void enterFormstate(FormstateContext ctx) {
    formItem2 = true;
    frameInitializingStatement(ctx);
    setContextQualifier(ctx.form_items_or_record(), ContextQualifier.SYMBOL);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void exitFormstate(FormstateContext ctx) {
    frameStack.statementEnd();
    formItem2 = false;
  }

  @Override
  public void enterFormat_opt(Format_optContext ctx) {
    if ((ctx.LEXAT() != null) && (ctx.field() != null)) {
      setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
      frameStack.lexAt(support.getNode(ctx.field()));
    } else if ((ctx.LIKE() != null) && (ctx.field() != null)) {
      setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void enterFrame_widgetname(Frame_widgetnameContext ctx) {
    frameStack.frameRefNode(support.getNode(ctx).getFirstChild(), currentScope);
  }

  @Override
  public void enterFrame_opt(Frame_optContext ctx) {
    if (((ctx.CANCELBUTTON() != null) || (ctx.DEFAULTBUTTON() != null)) && (ctx.field() != null)) {
      setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
    } 
  }

  @Override
  public void enterFunctionstate(FunctionstateContext ctx) {
    // John: Need some comments here. Why don't I just fetch any
    // function forward scope right away? Why wait until funcDef()?
    // Why bother with a funcForward map specifically, rather than
    // just a funcScope map generally?
    TreeParserSymbolScope definingScope = currentScope;
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    scopeAdd(blockNode);

    Routine r = new Routine(ctx.id.getText(), definingScope, currentScope);
    r.setProgressType(ABLNodeType.FUNCTION);
    r.setDefinitionNode(blockNode);
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
      /* if (forwardScope != null) {
        JPNode node = null; // XXX forwardScope.getRootBlock().getNode();
        Routine routine = (Routine) node.getSymbol();
        scopeSwap(forwardScope);

        // Weird (already set at the beginning)
        blockNode.setBlock(currentBlock);
        blockNode.setSymbol(routine);
        routine.setDefinitionNode(ctx);
        currentRoutine = routine;
      }*/

    }
  }

  @Override
  public void exitFunctionstate(FunctionstateContext ctx) {
    scopeClose(support.getNode(ctx));
    currentRoutine = rootRoutine;
  }

  @Override
  public void enterGetkeyvaluestate(GetkeyvaluestateContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
  }

  @Override
  public void enterImportstate(ImportstateContext ctx) {
    for (FieldContext fld : ctx.field()) {
      setContextQualifier(fld, ContextQualifier.UPDATING);
    }
    if (ctx.var_rec_field() != null) {
      setContextQualifier(ctx.var_rec_field(), ContextQualifier.UPDATING);
    }
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void enterInsertstate(InsertstateContext ctx) {
    frameInitializingStatement(ctx);

    setContextQualifier(ctx.record(), ContextQualifier.UPDATING);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void exitInsertstate(InsertstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterLdbname_opt1(Ldbname_opt1Context ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.BUFFERSYMBOL);
  }

  @Override
  public void enterMessage_opt(Message_optContext ctx) {
    if ((ctx.SET() != null) && (ctx.field() != null)) {
      setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
    } else if ((ctx.UPDATE() != null) && (ctx.field() != null)) {
      setContextQualifier(ctx.field(), ContextQualifier.REFUP);
    } 
  }

  @Override
  public void enterMethodstate(MethodstateContext ctx) {
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope;
    scopeAdd(blockNode);

    Routine r = new Routine(ctx.id.getText(), definingScope, currentScope);
    r.setProgressType(ABLNodeType.METHOD);
    r.setDefinitionNode(blockNode);
    blockNode.setSymbol(r);
    definingScope.add(r);
    currentRoutine = r;

    if (ctx.VOID() != null) {
      currentRoutine.setReturnDatatypeNode(DataType.VOID);
    } else {
      if (ctx.datatype().CLASS() != null) {
        currentRoutine.setReturnDatatypeNode(DataType.CLASS);
      } else {
        currentRoutine.setReturnDatatypeNode(DataType.getDataType(ctx.datatype().datatype_var().start.getType()));
      }
    }
  }

  @Override
  public void exitMethodstate(MethodstateContext ctx) {
    scopeClose(support.getNode(ctx));
    currentRoutine = rootRoutine;
  }

  @Override
  public void enterNextpromptstate(NextpromptstateContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
  }

  @Override
  public void enterOpenquerystate(OpenquerystateContext ctx) {
    setContextQualifier(ctx.for_record_spec(), ContextQualifier.INIT);
  }

  @Override
  public void enterProcedurestate(ProcedurestateContext ctx) {
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    TreeParserSymbolScope definingScope = currentScope;
    scopeAdd(blockNode);

    Routine r = new Routine(ctx.filename().getText(), definingScope, currentScope);
    r.setProgressType(ABLNodeType.PROCEDURE);
    r.setDefinitionNode(blockNode);
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
    formItem2 = true;
    frameEnablingStatement(ctx);

    setContextQualifier(ctx.form_items_or_record(), ContextQualifier.SYMBOL);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void exitPromptforstate(PromptforstateContext ctx) {
    frameStack.statementEnd();
    formItem2 = false;
  }

  @Override
  public void enterRawtransferstate(RawtransferstateContext ctx) {
    setContextQualifier(ctx.rawtransfer_elem(0), ContextQualifier.REF);
    setContextQualifier(ctx.rawtransfer_elem(1), ContextQualifier.UPDATING);
  }

  @Override
  public void enterRawtransfer_elem(Rawtransfer_elemContext ctx) {
    if (ctx.record() != null) {
      setContextQualifier(ctx.record(), contextQualifiers.removeFrom(ctx));
    } else if (ctx.field() != null) {
      setContextQualifier(ctx.field(), contextQualifiers.removeFrom(ctx));
    } else {
      setContextQualifier(ctx.var_rec_field(), contextQualifiers.removeFrom(ctx));
      // TODO Il faut que ce soit traité par enterVarRecField
    }
  }

  @Override
  public void enterField_frame_or_browse(Field_frame_or_browseContext ctx) {
    if (ctx.FRAME() != null)
      frameRef(support.getNode(ctx).getFirstChild());
    else if (ctx.BROWSE() != null)
      browseRef(support.getNode(ctx).getFirstChild());
  }

  @Override
  public void exitField(FieldContext ctx) {
    TableNameResolution tnr = nameResolution.removeFrom(ctx);
    if (tnr == null) tnr = TableNameResolution.ANY;
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    if (qual == null) qual = ContextQualifier.REF;
    field(ctx, support.getNode(ctx), null, ctx.id.getText(), qual, tnr);
  }

  @Override
  public void enterRecord_fields(Record_fieldsContext ctx) {
    for (FieldContext fld : ctx.field()) {
      setContextQualifier(fld, ContextQualifier.SYMBOL);
      nameResolution.put(fld, TableNameResolution.LAST);
    }
  }

  @Override
  public void enterRecord_opt(Record_optContext ctx) {
    if ((ctx.OF() != null) && (ctx.record() != null)) {
      setContextQualifier(ctx.record(), ContextQualifier.REF);
    }
    if ((ctx.USING() != null) && (ctx.field() != null)) {
      for (FieldContext field : ctx.field()) {
        setContextQualifier(field, ContextQualifier.SYMBOL);
        nameResolution.put(field, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void enterOnstate(OnstateContext ctx) {
    scopeAdd(support.getNode(ctx));
  }

  @Override
  public void exitOnstate(OnstateContext ctx) {
    scopeClose(support.getNode(ctx));
  }

  @Override
  public void enterOnOtherOfDbObject(OnOtherOfDbObjectContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.SYMBOL);
  }

  @Override
  public void exitOnOtherOfDbObject(OnOtherOfDbObjectContext ctx) {
    defineBufferForTrigger(support.getNode(ctx.record()));
  }

  @Override
  public void enterOnWriteOfDbObject(OnWriteOfDbObjectContext ctx) {
    setContextQualifier(ctx.bf, ContextQualifier.SYMBOL);
  }

  @Override
  public void exitOnWriteOfDbObject(OnWriteOfDbObjectContext ctx) {
    if (ctx.n != null) {
      defineBuffer(ctx, support.getNode(ctx.parent.parent).findDirectChild(ABLNodeType.NEW), null, ctx.n.getText(),
          support.getNode(ctx.bf), true);
    } else {
      defineBufferForTrigger(support.getNode(ctx.bf));
    }

    if (ctx.o != null) {
      defineBuffer(ctx, support.getNode(ctx.parent.parent).findDirectChild(ABLNodeType.OLD), null, ctx.o.getText(),
          support.getNode(ctx.bf), true);
    }
  }

  @Override
  public void enterOnAssign(OnAssignContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.INIT);
    
  }

  @Override
  public void exitOnAssign(OnAssignContext ctx) {
    // TODO Likely to have side effect, variable has to be defined before starting block
    if (ctx.OLD() != null) {
      stack.push(defineVariable(ctx, support.getNode(ctx), ctx.f.getText(), support.getNode(ctx.field())));
    }
  }

  @Override
  public void enterReleasestate(ReleasestateContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.REF);
  }

  @Override
  public void enterRepeatstate(RepeatstateContext ctx) {
    blockBegin(ctx);
    // TODO I think it should be support.getNode().getFirstChild()
    frameBlockCheck(support.getNode(ctx));
  }

  @Override
  public void enterRepeatstatesub(RepeatstatesubContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void exitRepeatstate(RepeatstateContext ctx) {
    blockEnd();
  }

  @Override
  public void enterRun_set(Run_setContext ctx) {
    if (ctx.field() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterScrollstate(ScrollstateContext ctx) {
    frameInitializingStatement(ctx);
  }

  @Override
  public void exitScrollstate(ScrollstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterSetstate(SetstateContext ctx) {
    formItem2 = true;
    frameInitializingStatement(ctx);

    setContextQualifier(ctx.form_items_or_record(), ContextQualifier.REFUP);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void exitSetstate(SetstateContext ctx) {
     frameStack.statementEnd();
     formItem2 = false;
  }

  @Override
  public void enterSystemdialogcolorstate(SystemdialogcolorstateContext ctx) {
    if (ctx.update_field() != null) {
      setContextQualifier(ctx.update_field().field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterSysdiafont_opt(Sysdiafont_optContext ctx) {
    if (ctx.update_field() != null) {
      setContextQualifier(ctx.update_field().field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterSystemdialoggetdirstate(SystemdialoggetdirstateContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.REFUP);
  }
 
  @Override
  public void enterSystemdialoggetdir_opt(Systemdialoggetdir_optContext ctx) {
    if (ctx.field() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.REFUP);
    }
  }

  @Override
  public void enterSystemdialoggetfilestate(SystemdialoggetfilestateContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.REFUP);
  }

  @Override
  public void enterSysdiagetfile_opt(Sysdiagetfile_optContext ctx) {
    if (ctx.field() != null) {
      setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterSysdiapri_opt(Sysdiapri_optContext ctx) {
    if (ctx.update_field() != null) {
      setContextQualifier(ctx.update_field().field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void enterTriggerprocedurestatesub1(Triggerprocedurestatesub1Context ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.SYMBOL);
  }

  @Override
  public void exitTriggerprocedurestatesub1(Triggerprocedurestatesub1Context ctx) {
    defineBufferForTrigger(support.getNode(ctx.record()));
  }

  @Override
  public void enterTriggerprocedurestatesub2(Triggerprocedurestatesub2Context ctx) {
    setContextQualifier(ctx.buff, ContextQualifier.SYMBOL);
  }
  
  @Override
  public void exitTriggerprocedurestatesub2(Triggerprocedurestatesub2Context ctx) {
    if (ctx.newBuff != null) {
      defineBuffer(ctx, support.getNode(ctx.parent.parent).findDirectChild(ABLNodeType.NEW), null, ctx.newBuff.getText(),
          support.getNode(ctx.buff), true);
    } else {
      defineBufferForTrigger(support.getNode(ctx.buff));
    }

    if (ctx.oldBuff != null) {
      defineBuffer(ctx, support.getNode(ctx.parent.parent).findDirectChild(ABLNodeType.OLD), null, ctx.oldBuff.getText(),
          support.getNode(ctx.buff), true);
    }
  }

  @Override
  public void enterTriggerOfSub1(TriggerOfSub1Context ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.SYMBOL);
  }

  @Override
  public void enterTriggerOfSub2(TriggerOfSub2Context ctx) {
      stack.push(defineVariable(ctx, support.getNode(ctx), ctx.id.getText()));
  }

  @Override
  public void exitTriggerOfSub2(TriggerOfSub2Context ctx) {
    addToSymbolScope(stack.pop());
  }

  @Override
  public void enterTrigger_old(Trigger_oldContext ctx) {
    stack.push(defineVariable(ctx, support.getNode(ctx), ctx.id.getText()));
  }
  
  @Override
  public void exitTrigger_old(Trigger_oldContext ctx) {
    addToSymbolScope(stack.pop());
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
    frameInitializingStatement(ctx);

    for (Field_form_itemContext field : ctx.field_form_item()) {
      setContextQualifier(field, ContextQualifier.SYMBOL);
       frameStack.formItem(support.getNode(field));
    }
  }

  @Override
  public void exitUnderlinestate(UnderlinestateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterUpstate(UpstateContext ctx) {
    frameInitializingStatement(ctx);
  }

  @Override
  public void exitUpstate(UpstateContext ctx) {
    frameStack.statementEnd();
  }

  @Override
  public void enterUpdatestate(UpdatestateContext ctx) {
    formItem2 = true;
    frameEnablingStatement(ctx);
    setContextQualifier(ctx.form_items_or_record(), ContextQualifier.REFUP);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        setContextQualifier(fld, ContextQualifier.SYMBOL);
        nameResolution.put(fld, TableNameResolution.LAST);
      }
    }
  }

  @Override
  public void exitUpdatestate(UpdatestateContext ctx) {
    frameStack.statementEnd();
    formItem2 = false;
  }

  @Override
  public void enterValidatestate(ValidatestateContext ctx) {
    setContextQualifier(ctx.record(), ContextQualifier.REF);
  }

  @Override
  public void exitViewstate(ViewstateContext ctx) {
    // The VIEW statement grammar uses gwidget, so we have to do some
    // special searching for FRAME to initialize.
    JPNode headNode = support.getNode(ctx);
    for (JPNode frameNode : headNode.query(ABLNodeType.FRAME)) {
      ABLNodeType parentType = frameNode.getParent().getNodeType();
      if (parentType == ABLNodeType.WIDGET_REF || parentType == ABLNodeType.IN) {
        frameStack.simpleFrameInitStatement(ctx, headNode, frameNode, currentBlock);
        return;
      }
    }
  }

  @Override
  public void enterWaitfor_set(Waitfor_setContext ctx) {
    setContextQualifier(ctx.field(), ContextQualifier.UPDATING);
  }

  // ******************
  //  INTERNAL METHODS
  // ******************

  /** Called at the *end* of the statement that defines the symbol. */
  private void addToSymbolScope(Symbol symbol) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Adding symbol '{}' to current scope", indent(), symbol);
    if (inDefineEvent) return;
    currentScope.add(symbol);
  }

  private Block pushBlock(Block block) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Pushing block '{}' to stack", indent(), block);
    blockStack.add(block);
    return block;
  }

  private Block popBlock() {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Popping block from stack", indent());
    blockStack.remove(blockStack.size() - 1);
    return blockStack.get(blockStack.size() - 1);
  }


  private void recordNameNode(RecordNameNode recordNode, ContextQualifier contextQualifier) {
    if (LOG.isDebugEnabled())
      LOG.debug("Entering recordNameNode {} {}", recordNode, contextQualifier);

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
    if (LOG.isDebugEnabled())
      LOG.debug("Entering recordNodeSymbol {} {}", node, buffer);

    String nodeText = node.getText();
    if (buffer == null) {
      LOG.error("Could not resolve table '{}' in file #{}:{}:{}", nodeText ,node.getFileIndex() , node.getLine(), node.getColumn());
      return;
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

  private void blockBegin(ParseTree ctx) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Creating new block", indent());
    BlockNode blockNode = (BlockNode) support.getNode(ctx);
    currentBlock = pushBlock(new Block(currentBlock, blockNode));
    blockNode.setBlock(currentBlock);
  }

  private void blockEnd() {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> End of block", indent());
    currentBlock = popBlock();
  }

  private void scopeAdd(JPNode anode) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Creating new scope for block {}", indent(), anode.getNodeType());

    BlockNode blockNode = (BlockNode) anode;
    currentScope = currentScope.addScope();
    currentBlock = pushBlock(new Block(currentScope, blockNode));
    currentScope.setRootBlock(currentBlock);
    blockNode.setBlock(currentBlock);
  }

  private void scopeClose(JPNode scopeRootNode) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> End of scope", indent());

    currentScope = currentScope.getParentScope();
    blockEnd();
  }

  /**
   * In the case of a function definition that comes some time after a function forward declaration, we want to use the
   * scope that was created with the forward declaration, because it is the scope that has all of the parameter
   * definitions. We have to do this because the definition itself may have left out the parameter list - it's not
   * required - it just uses the parameter list from the declaration.
   */
  private void scopeSwap(TreeParserSymbolScope scope) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Swapping scope...", indent());

    currentScope = scope;
    blockEnd(); // pop the unused block from the stack
    currentBlock = pushBlock(scope.getRootBlock());
  }

  /** This is a specialization of frameInitializingStatement, called for ENABLE|UPDATE|PROMPT-FOR. */
  private void frameEnablingStatement(ParseTree ctx) {
    LOG.trace("Entering frameEnablingStatement");

    // Flip this flag before calling nodeOfInitializingStatement.
    frameStack.statementIsEnabler();
    frameStack.nodeOfInitializingStatement(ctx, support.getNode(ctx), currentBlock);
  }

  public void frameInitializingStatement(ParseTree ctx) {
    frameStack.nodeOfInitializingStatement(ctx, support.getNode(ctx), currentBlock);
  }

  private void frameBlockCheck(JPNode ast) {
    LOG.trace("Entering frameBlockCheck {}", ast);
    frameStack.nodeOfBlock(ast, currentBlock);
  }

  private Variable defineVariable(ParseTree ctx, JPNode defAST, String name) {
    return defineVariable(ctx, defAST, name, false);
  }

  private Variable defineVariable(ParseTree ctx, JPNode defNode, String name, boolean parameter) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> New variable {} (parameter: {})", indent(), name, parameter);

    // We need to create the Variable Symbol right away, because further actions in the grammar might need to set
    // attributes on it. We can't add it to the scope yet, because of statements like this: def var xyz like xyz.
    // The tree parser is responsible for calling addToScope at the end of the statement or when it is otherwise safe to
    // do so.
    Variable variable = new Variable(name, currentScope, parameter);
    if (defNode == null)
      LOG.info("Unable to set JPNode symbol for variable {}", ctx.getText());
    else {
      defNode.getIdNode().setSymbol(variable);
      variable.setDefinitionNode(defNode.getIdNode());
    }
    currSymbol = variable;
    return variable;
  }

  private Variable defineVariable(ParseTree ctx, JPNode defAST, String name, DataType dataType, boolean parameter) {
    Variable v = defineVariable(ctx, defAST, name, parameter);
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Adding datatype {}", indent(), dataType);
    v.setDataType(dataType);
    return v;
  }

  private Variable defineVariable(ParseTree ctx, JPNode defAST, String name, JPNode likeAST) {
    return defineVariable(ctx, defAST, name, likeAST, false);
  }

  public Variable defineVariable(ParseTree ctx, JPNode defAST, String name, JPNode likeAST, boolean parameter) {
    Variable v = defineVariable(ctx, defAST, name, parameter);
    FieldRefNode likeRefNode = (FieldRefNode) likeAST;
    v.setDataType(likeRefNode.getDataType());
    v.setClassName(likeRefNode.getClassName());
    return v;
  }

  /** The tree parser calls this at an AS node */
  public void defAs(ParserRuleContext ctx) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Variable AS '{}'", indent(), ctx.getText());

    Primative primative = (Primative) currSymbol;
    if ((ctx.getStart().getType() == ABLNodeType.CLASS.getType()) ||  (ctx.getStop().getType() == ABLNodeType.TYPE_NAME.getType())) {
      primative.setDataType(DataType.CLASS);
      primative.setClassName(ctx.getStop().getText());
    } else {
      primative.setDataType(DataType.getDataType(ctx.getStop().getType()));
    }
  }

  public void defExtent(String text) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Variable extent '{}'", indent(), text);

    Primative primative = (Primative) currSymbol;
    try {
      primative.setExtent(Integer.parseInt(text));
    } catch (NumberFormatException caught) {
      primative.setExtent(-1);
    }
  }

  public void defLike(ParseTree ctx, JPNode likeNode) {
    LOG.trace("Entering defLike {}", likeNode);
    Primative likePrim = (Primative) likeNode.getSymbol();
    Primative newPrim = (Primative) currSymbol;
    if (likePrim != null) {
      newPrim.assignAttributesLike(likePrim);
      assert newPrim.getDataType() != null : "Failed to set datatype at " + likeNode.getFileIndex() + " line "
          + likeNode.getLine();
    } else {
      LOG.error("Failed to find LIKE datatype at {} line {}", likeNode.getFileIndex(), likeNode.getLine());
    }
  }

  public Symbol defineSymbol(ABLNodeType symbolType, ParseTree defSymbol, JPNode defNode, JPNode idNode, String name) {
    if (LOG.isDebugEnabled())
      LOG.debug("{}> Entering defineSymbol {} - {}", indent(), symbolType, defNode);
    /*
     * Some notes: We need to create the Symbol right away, because further actions in the grammar might need to set
     * attributes on it. We can't add it to the scope yet, because of statements like this: def var xyz like xyz. The
     * tree parser is responsible for calling addToScope at the end of the statement or when it is otherwise safe to do
     * so.
     */
    Symbol symbol = SymbolFactory.create(symbolType, name, currentScope);
    currSymbol = symbol;
    currSymbol.setDefinitionNode(defNode.getIdNode());
    defNode.getIdNode().setLink(IConstants.SYMBOL, symbol);
    return symbol;
  }

  /** Called at the start of a DEFINE BROWSE statement. */
  public Browse defineBrowse(ParseTree defSymbol, JPNode defAST, JPNode idAST, String name) {
    LOG.trace("Entering defineBrowse {} - {}", defAST, idAST);
    Browse browse = (Browse) defineSymbol(ABLNodeType.BROWSE, defSymbol, defAST, idAST, name);
    frameStack.nodeOfDefineBrowse(browse, (JPNode) defAST, defSymbol);
    return browse;
  }

  public Event defineEvent(ParseTree ctx, JPNode defNode, JPNode idNode, String name) {
    LOG.trace("Entering defineEvent {} - {}", defNode, idNode);
    /* String name = idNode.getText();
    if (name == null || name.length() == 0)
      name = idNode.getNodeType().name(); */
    Event event = new Event(name, currentScope);
    event.setDefinitionNode(defNode.getIdNode());
    currSymbol = event;
    defNode.getIdNode().setLink(IConstants.SYMBOL, event);
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
  public Symbol defineTableFieldInitialize(ParseTree ctx, JPNode idNode, String text) {
    LOG.trace("Entering defineTableFieldInitialize {}", idNode);
    FieldBuffer fieldBuff = rootScope.defineTableFieldDelayedAttach(text, currDefTable);
    currSymbol = fieldBuff;
    fieldBuff.setDefinitionNode(idNode.getFirstChild());
    idNode.getFirstChild().setLink(IConstants.SYMBOL, fieldBuff);
    return fieldBuff;
  }

  public void defineTableFieldFinalize(Object obj) {
    LOG.trace("Entering defineTableFieldFinalize {}", obj);
    ((FieldBuffer) obj).getField().setTable(currDefTable.getTable());
  }

  private void defineTableLike(ParseTree ctx) {
    // Get table for "LIKE table"
    ITable table = astTableLink(support.getNode(ctx));
    currDefTableLike = table;
    // For each field in "table", create a field def in currDefTable
    for (IField field : table.getFieldPosOrder()) {
      rootScope.defineTableField(field.getName(), currDefTable).assignAttributesLike(field);
    }
  }

  private void defineUseIndex(JPNode recNode, String name) {
    ITable table = astTableLink(recNode);
    IIndex idx = table.lookupIndex(name);
    currDefTable.getTable().add(new Index(currDefTable.getTable(), idx.getName(), idx.isUnique(), idx.isPrimary()));
    currDefTableUseIndex = true;
  }

  private void defineIndexInitialize(String name, boolean unique, boolean primary, boolean word) {
    currDefIndex = new Index(currDefTable.getTable(), name, unique, primary);
    currDefTable.getTable().add(currDefIndex);
  }

  private void defineIndexField(String name) {
    IField fld = currDefTable.getTable().lookupField(name);
    if (fld != null)
      currDefIndex.addField(fld);
  }

  private void defineTable(ParseTree ctx, JPNode defNode, JPNode idNode, String name, int storeType) {
    if (LOG.isDebugEnabled())
      LOG.trace("{}> Table definition {} {}", indent(), defNode, storeType);

    TableBuffer buffer = rootScope.defineTable(name, storeType);
    currSymbol = buffer;
    currSymbol.setDefinitionNode(defNode.getIdNode());
    currDefTable = buffer;
    currDefTableLike = null;
    currDefTableUseIndex = false;

    defNode.getIdNode().setLink(IConstants.SYMBOL, buffer);
  }

  private void postDefineTempTable() {
    if (LOG.isDebugEnabled())
      LOG.trace("{}> End of table definition", indent());

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

  private void defineTempTable(ParseTree ctx, JPNode defAST, JPNode idAST, String name) {
    defineTable(ctx, defAST, idAST, name, IConstants.ST_TTABLE);
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
  public void defineBuffer(ParseTree ctx, JPNode defAST, JPNode idNode, String name, JPNode tableAST, boolean init) {
    LOG.trace("Entering defineBuffer {} {} {}", defAST, tableAST, init);
    ITable table = astTableLink(tableAST.getIdNode());
    TableBuffer bufSymbol = currentScope.defineBuffer(name, table);
    currSymbol = bufSymbol;
    currSymbol.setDefinitionNode(defAST.getIdNode());
    defAST.getIdNode().setLink(IConstants.SYMBOL, bufSymbol);
    if (init) {
      BufferScope bufScope = currentBlock.getBufferForReference(bufSymbol);
      defAST.setLink(IConstants.BUFFERSCOPE, bufScope);
    }
  }

  /**
   * Define an unnamed buffer which is scoped (symbol and buffer) to the trigger scope/block.
   */
  public void defineBufferForTrigger(JPNode tableAST) {
    LOG.trace("Entering defineBufferForTrigger {}", tableAST);
    ITable table = astTableLink(tableAST);
    TableBuffer bufSymbol = currentScope.defineBuffer("", table);
    currentBlock.getBufferForReference(bufSymbol); // Create the BufferScope
    currSymbol = bufSymbol;
  }

  private void defineWorktable(ParseTree ctx, JPNode defAST, JPNode idAST, String name) {
    defineTable(ctx, defAST, idAST, name, IConstants.ST_WTABLE);
  }

  public void noteReference(JPNode node, ContextQualifier cq) {
    if ((node.getSymbol() != null) && ((cq == ContextQualifier.UPDATING) || (cq == ContextQualifier.REFUP))) {
      node.getSymbol().noteReference(cq);
    }
  }

  public void propGetSetBegin(ParseTree ctx, JPNode propAST) {
    LOG.trace("Entering propGetSetBegin {}", propAST);
    scopeAdd(propAST);
    BlockNode blockNode = (BlockNode) propAST;
    TreeParserSymbolScope definingScope = currentScope.getParentScope();
 
    Routine r = new Routine(propAST.getText(), definingScope, currentScope);
    r.setProgressType(propAST.getNodeType());
    r.setDefinitionNode(blockNode);
    blockNode.setSymbol(r);
    definingScope.add(r);
    currentRoutine = r;
  }

  public void propGetSetEnd(JPNode propAST) {
    LOG.trace("Entering propGetSetEnd {}", propAST);
    scopeClose(propAST);
    currentRoutine = rootRoutine;
  }
  
  private void widattr(ExprtWidNameContext ctx, JPNode idNode, ContextQualifier cq) {
    if ((ctx.widname().systemhandlename() != null) && (ctx.widname().systemhandlename().THISOBJECT() != null)) {
      if (ctx.attr_colon().OBJCOLON(0) != null) {
        String name = ctx.attr_colon().id.getText();
        
        FieldLookupResult result =  currentBlock.lookupField(name, true);
        if (result == null)
          return;

        // Variable
        if (result.getSymbol() instanceof Variable) {
          result.getSymbol().noteReference(cq);
        }
      }
    }
  }

  private void widattr(WidattrWidNameContext ctx, JPNode idNode, ContextQualifier cq) {
    if ((ctx.widname().systemhandlename() != null) && (ctx.widname().systemhandlename().THISOBJECT() != null)) {
      if (ctx.attr_colon().OBJCOLON(0) != null) {
        String name = ctx.attr_colon().id.getText();
        
        FieldLookupResult result =  currentBlock.lookupField(name, true);
        if (result == null)
          return;

        // Variable
        if (result.getSymbol() instanceof Variable) {
          result.getSymbol().noteReference(cq);
        }
      }
    }
  }

  private void widattr(WidattrExprt2Context ctx, JPNode idNode, ContextQualifier cq) {
    if (ctx.exprt2() instanceof Exprt2FieldContext) {
      Exprt2FieldContext ctx2 = (Exprt2FieldContext) ctx.exprt2();
      if (ctx.attr_colon().OBJCOLON(0) != null) {
        String clsRef = ctx2.field().getText();
        String clsName = rootScope.getClassName();
        if ((clsRef != null) && (clsName != null) && (clsRef.indexOf('.') == -1) && (clsName.indexOf('.') != -1))
          clsName = clsName.substring(clsName.indexOf('.') + 1);
        
        if ((clsRef != null) && (clsName != null) && clsRef.equalsIgnoreCase(clsName)) {
          String right = ctx.attr_colon().id.getText();
          
          FieldLookupResult result =  currentBlock.lookupField(right, true);
          if (result == null)
            return;

          // Variable
          if (result.getSymbol() instanceof Variable) {
            result.getSymbol().noteReference(cq);
          }
        }
      }
    }
  }

  private void widattr(ExprtExprt2Context ctx, JPNode idNode, ContextQualifier cq) {
    if (ctx.exprt2() instanceof Exprt2FieldContext) {
      Exprt2FieldContext ctx2 = (Exprt2FieldContext) ctx.exprt2();
    if (ctx.attr_colon().OBJCOLON(0) != null) {
      String clsRef = ctx2.field().getText();
      String clsName = rootScope.getClassName();
      if ((clsRef != null) && (clsName != null) && (clsRef.indexOf('.') == -1) && (clsName.indexOf('.') != -1))
        clsName = clsName.substring(clsName.indexOf('.') + 1);
      
      if ((clsRef != null) && (clsName != null) && clsRef.equalsIgnoreCase(clsName)) {
        String right = ctx.attr_colon().id.getText();
        
        FieldLookupResult result =  currentBlock.lookupField(right, true);
        if (result == null)
          return;

        // Variable
        if (result.getSymbol() instanceof Variable) {
          result.getSymbol().noteReference(cq);
        }
      }
    } }
  }

  private void frameRef(JPNode idAST) {
    frameStack.frameRefNode(idAST, currentScope);
  }

  private void browseRef(JPNode idAST) {
    LOG.trace("Entering browseRef {}", idAST);
    frameStack.browseRefNode(idAST, currentScope);
  }

  private void bufferRef(String name) {
    TableBuffer tableBuffer = currentScope.lookupBuffer(name);
    if (tableBuffer != null) {
      tableBuffer.noteReference(ContextQualifier.SYMBOL);
    }
  }

  public void field(ParseTree ctx, JPNode refAST, JPNode idNode, String name, ContextQualifier cq, TableNameResolution resolution) {
    LOG.trace("Entering field {} {} {} {}", refAST, idNode, cq, resolution);
    FieldRefNode refNode = (FieldRefNode) refAST;
    FieldLookupResult result = null;

    refNode.attrSet(IConstants.CONTEXT_QUALIFIER, cq.toString());

    // Check if this is a Field_ref being "inline defined"
    // If so, we define it right now.
    if (refNode.attrGet(IConstants.INLINE_VAR_DEF) == 1)
      addToSymbolScope(defineVariable(ctx, refAST, name));

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
      result = new FieldLookupResult.Builder().setSymbol(fieldBuffer).build();
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

    if (result.isUnqualified())
      refNode.attrSet(IConstants.UNQUALIFIED_FIELD, IConstants.TRUE);
    if (result.isAbbreviated())
      refNode.attrSet(IConstants.ABBREVIATED, IConstants.TRUE);

    // Buffer attributes
    if (result.getBufferScope() != null) {
      refNode.setBufferScope(result.getBufferScope());
    }

    refNode.setSymbol(result.getSymbol());
    result.getSymbol().noteReference(cq);
    if (result.getSymbol() instanceof FieldBuffer) {
      FieldBuffer fb = (FieldBuffer) result.getSymbol();
      refNode.attrSet(IConstants.STORETYPE, fb.getField().getTable().getStoretype());
      if (fb.getBuffer() != null) {
        fb.getBuffer().noteReference(cq);
      }
    } else {
      refNode.attrSet(IConstants.STORETYPE, IConstants.ST_VAR);
    }

  } // field()

  @Override
  public void enterEveryRule(ParserRuleContext ctx) {
    currentLevel++;
    if (LOG.isTraceEnabled())
      LOG.trace("{}> {}", indent(), Proparse.ruleNames[ctx.getRuleIndex()]);
  }
  
  @Override
  public void exitEveryRule(ParserRuleContext ctx) {
    currentLevel--;
  }

  private String indent() {
    return java.nio.CharBuffer.allocate(currentLevel).toString().replace('\0', ' ');
  }

}
