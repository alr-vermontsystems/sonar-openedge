package org.prorefactor.proparse.antlr4;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.prorefactor.core.ABLNodeType;
import org.prorefactor.proparse.ParserSupport;
import org.prorefactor.proparse.antlr4.Proparse.Block_forContext;
import org.prorefactor.proparse.antlr4.Proparse.Block_opt_iteratorContext;
import org.prorefactor.proparse.antlr4.Proparse.Block_preselectContext;
import org.prorefactor.proparse.antlr4.Proparse.DownstateContext;
import org.prorefactor.proparse.antlr4.Proparse.EmptytemptablestateContext;
import org.prorefactor.proparse.antlr4.Proparse.EnablestateContext;
import org.prorefactor.proparse.antlr4.Proparse.ExportstateContext;
import org.prorefactor.proparse.antlr4.Proparse.ExpressionContext;
import org.prorefactor.proparse.antlr4.Proparse.ExtentphraseContext;
import org.prorefactor.proparse.antlr4.Proparse.FieldContext;
import org.prorefactor.proparse.antlr4.Proparse.Field_form_itemContext;
import org.prorefactor.proparse.antlr4.Proparse.FieldoptionContext;
import org.prorefactor.proparse.antlr4.Proparse.FindstateContext;
import org.prorefactor.proparse.antlr4.Proparse.For_record_specContext;
import org.prorefactor.proparse.antlr4.Proparse.Form_itemContext;
import org.prorefactor.proparse.antlr4.Proparse.Form_items_or_recordContext;
import org.prorefactor.proparse.antlr4.Proparse.Format_optContext;
import org.prorefactor.proparse.antlr4.Proparse.FormstateContext;
import org.prorefactor.proparse.antlr4.Proparse.ForstateContext;
import org.prorefactor.proparse.antlr4.Proparse.Frame_optContext;
import org.prorefactor.proparse.antlr4.Proparse.Frame_widgetnameContext;
import org.prorefactor.proparse.antlr4.Proparse.FramephraseContext;
import org.prorefactor.proparse.antlr4.Proparse.FunargsContext;
import org.prorefactor.proparse.antlr4.Proparse.FunctionstateContext;
import org.prorefactor.proparse.antlr4.Proparse.GetkeyvaluestateContext;
import org.prorefactor.proparse.antlr4.Proparse.ImportstateContext;
import org.prorefactor.proparse.antlr4.Proparse.InsertstateContext;
import org.prorefactor.proparse.antlr4.Proparse.Ldbname_opt1Context;
import org.prorefactor.proparse.antlr4.Proparse.LdbnamefuncContext;
import org.prorefactor.proparse.antlr4.Proparse.Message_optContext;
import org.prorefactor.proparse.antlr4.Proparse.MessagestateContext;
import org.prorefactor.proparse.antlr4.Proparse.MethodstateContext;
import org.prorefactor.proparse.antlr4.Proparse.NextpromptstateContext;
import org.prorefactor.proparse.antlr4.Proparse.OpenquerystateContext;
import org.prorefactor.proparse.antlr4.Proparse.ParameterBufferForContext;
import org.prorefactor.proparse.antlr4.Proparse.ParameterBufferRecordContext;
import org.prorefactor.proparse.antlr4.Proparse.ParameterOtherContext;
import org.prorefactor.proparse.antlr4.Proparse.ProcedurestateContext;
import org.prorefactor.proparse.antlr4.Proparse.ProgramContext;
import org.prorefactor.proparse.antlr4.Proparse.PromptforstateContext;
import org.prorefactor.proparse.antlr4.Proparse.PseudfnContext;
import org.prorefactor.proparse.antlr4.Proparse.PublishstateContext;
import org.prorefactor.proparse.antlr4.Proparse.Rawtransfer_elemContext;
import org.prorefactor.proparse.antlr4.Proparse.RawtransferstateContext;
import org.prorefactor.proparse.antlr4.Proparse.RecordContext;
import org.prorefactor.proparse.antlr4.Proparse.Record_fieldsContext;
import org.prorefactor.proparse.antlr4.Proparse.Record_optContext;
import org.prorefactor.proparse.antlr4.Proparse.RecordfuncContext;
import org.prorefactor.proparse.antlr4.Proparse.RecordphraseContext;
import org.prorefactor.proparse.antlr4.Proparse.ReleasestateContext;
import org.prorefactor.proparse.antlr4.Proparse.RepeatstateContext;
import org.prorefactor.proparse.antlr4.Proparse.RunOptInContext;
import org.prorefactor.proparse.antlr4.Proparse.RunOptPersistentContext;
import org.prorefactor.proparse.antlr4.Proparse.Run_setContext;
import org.prorefactor.proparse.antlr4.Proparse.RunstateContext;
import org.prorefactor.proparse.antlr4.Proparse.RunstoredprocedurestateContext;
import org.prorefactor.proparse.antlr4.Proparse.RunsuperstateContext;
import org.prorefactor.proparse.antlr4.Proparse.ScrollstateContext;
import org.prorefactor.proparse.antlr4.Proparse.SetstateContext;
import org.prorefactor.proparse.antlr4.Proparse.Sysdiafont_optContext;
import org.prorefactor.proparse.antlr4.Proparse.Sysdiagetfile_optContext;
import org.prorefactor.proparse.antlr4.Proparse.Sysdiapri_optContext;
import org.prorefactor.proparse.antlr4.Proparse.SystemdialogcolorstateContext;
import org.prorefactor.proparse.antlr4.Proparse.Systemdialoggetdir_optContext;
import org.prorefactor.proparse.antlr4.Proparse.SystemdialoggetdirstateContext;
import org.prorefactor.proparse.antlr4.Proparse.SystemdialoggetfilestateContext;
import org.prorefactor.proparse.antlr4.Proparse.SystemdialogprintersetupstateContext;
import org.prorefactor.proparse.antlr4.Proparse.ThisobjectstateContext;
import org.prorefactor.proparse.antlr4.Proparse.Trigger_onContext;
import org.prorefactor.proparse.antlr4.Proparse.UnderlinestateContext;
import org.prorefactor.proparse.antlr4.Proparse.UpdatestateContext;
import org.prorefactor.proparse.antlr4.Proparse.UpstateContext;
import org.prorefactor.proparse.antlr4.Proparse.ValidatestateContext;
import org.prorefactor.proparse.antlr4.Proparse.Var_rec_fieldContext;
import org.prorefactor.proparse.antlr4.Proparse.ViewstateContext;
import org.prorefactor.proparse.antlr4.Proparse.Waitfor_setContext;
import org.prorefactor.proparse.antlr4.Proparse.WaitforstateContext;
import org.prorefactor.proparse.antlr4.nodetypes.BlockNode;
import org.prorefactor.proparse.antlr4.treeparser.Block;
import org.prorefactor.refactor.RefactorSession;
import org.prorefactor.treeparser.ContextQualifier;
import org.prorefactor.treeparser.IBlock;
import org.prorefactor.treeparser.ITreeParserRootSymbolScope;
import org.prorefactor.treeparser.ITreeParserSymbolScope;
import org.prorefactor.treeparser.ParseUnit;
import org.prorefactor.treeparser.TreeParserRootSymbolScope;
import org.prorefactor.treeparser.symbols.IRoutine;
import org.prorefactor.treeparser.symbols.Routine;

public class TreeParser extends ProparseBaseListener {

  private final ParserSupport support;
  private final RefactorSession refSession;
  private final ParseUnit unit;
  private final ITreeParserRootSymbolScope rootScope;

  private IBlock currentBlock;
  private ITreeParserSymbolScope currentScope;
  private IRoutine currentRoutine;

  /*
   * Note that blockStack is *only* valid for determining the current block - the stack itself cannot be used for
   * determining a block's parent, buffer scopes, etc. That logic is found within the Block class. Conversely, we cannot
   * use Block.parent to find the current block when we close out a block. That is because a scope's root block parent
   * is always the program block, but a programmer may code a scope into a non-root block... which we need to make
   * current again once done inside the scope.
   */
  private List<IBlock> blockStack = new ArrayList<>();

  private ParseTreeProperty<ContextQualifier> contextQualifiers = new ParseTreeProperty<>();

  public TreeParser(ParserSupport support, RefactorSession session, ParseUnit unit) {
    this.support = support;
    
    this.refSession = session;
    this.unit = unit;
    this.rootScope = new TreeParserRootSymbolScope(refSession);

    currentScope = rootScope;

  }

  private IBlock popBlock() {
    blockStack.remove(blockStack.size() - 1);
    return blockStack.get(blockStack.size() - 1);
  }

  private IBlock pushBlock(IBlock block) {
    blockStack.add(block);
    return block;
  }

  @Override
  public void enterProgram(ProgramContext ctx) {
    JPNode rootAST = support.getNode(ctx);
    BlockNode blockNode = (BlockNode) rootAST;
    
    currentBlock = pushBlock(new Block(rootScope, blockNode));
    rootScope.setRootBlock(currentBlock);
    blockNode.setBlock(currentBlock);
    // unit.setRootScope(rootScope);
    
    Routine r = new Routine("", rootScope, rootScope);
    r.setProgressType(ABLNodeType.PROGRAM_ROOT);
//    r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);
    rootScope.add(r);
    currentRoutine = r;
//    rootRoutine = r;

  }

  @Override
  public void exitProgram(ProgramContext ctx) {
    // See TP01Support#programTail()
  }

  @Override
  public void enterBlock_for(Block_forContext ctx) {
    // Example
    for (RecordContext record : ctx.record()) {
      // TP01Support.recordNameNode(support.getNode(record), ContextQualifier.BUFFERSYMBOL);
      // currentBlock.addStrongBufferScope(support.getNode(record));
    }
  }

  @Override
  public void enterBlock_opt_iterator(Block_opt_iteratorContext ctx) {
    // TP01Support.field(support.getNode(ctx.field()), ctx.field().id, ContextQualifier.REFUP, TableNameResolution.ANY)
  }

  @Override
  public void enterBlock_preselect(Block_preselectContext ctx) {
    // Read (and removed ?) in for_record_spec
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

  // XXX

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
    // TODO TP01Support.recordNameNode(ContextQualifier.INIT)
  }
  
  @Override
  public void enterForstate(ForstateContext ctx) {
    // TODO TP01Support.blockBegin(#f); 
    // TODO TP01Support.frameBlockCheck(#f);
    contextQualifiers.put(ctx.for_record_spec(), ContextQualifier.INITWEAK);
    
    // TODO Compliqué, faire le TP01Support.frameStatementEnd() après le block_colon
    // C'est fait également dans un autre cas, je ne sais plus lequel
  }

  @Override
  public void exitForstate(ForstateContext ctx) {
    // TODO TP01Support.blockEnd();
  }

  // TODO Move method to top
  @Override
  public void enterFor_record_spec(For_record_specContext ctx) {
    ContextQualifier qual = contextQualifiers.removeFrom(ctx);
    for (RecordphraseContext rec : ctx.recordphrase()) {
      // TP01Support.recordNameNode(support.getNode(rec), qual);
    }
  }

  // TODO Move method to top
  @Override
  public void enterForm_item(Form_itemContext ctx) {
    if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), contextQualifiers.removeFrom(ctx));
      // TODO TP01Support.formItem();
    } else if (ctx.recordAsFormItem() != null) {
      contextQualifiers.put(ctx.recordAsFormItem(), contextQualifiers.removeFrom(ctx));
      // TODO TP01Support.formItem();
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
    // TODO TP01Support.frameInitializingStatement(#head);
    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.SYMBOL);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitFormstate(FormstateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void enterFormat_opt(Format_optContext ctx) {
    if ((ctx.LEXAT() != null) && (ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
      // TODO TP01Support.lexAt();
    } else if ((ctx.LIKE() != null) && (ctx.field() != null)) {
      contextQualifiers.put(ctx.field(), ContextQualifier.SYMBOL);
    }
  }

  @Override
  public void enterFrame_widgetname(Frame_widgetnameContext ctx) {
    // TODO TP01Support.frameRef();
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
    // TODO TP01Support.routineReturnDatatype(functionstate_AST_in);
    
    if (ctx.FORWARDS() != null) {
      // TODO TP01Support.funcForward();
    } else {
      // TODO TP01Support.funcDef();
    }
  }

  @Override
  public void exitFunctionstate(FunctionstateContext ctx) {
    // TODO TP01Support.funcEnd();
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
    // TODO TP01Support.frameInitializingStatement(#head);
    contextQualifiers.put(ctx.record(), ContextQualifier.UPDATING);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitInsertstate(InsertstateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
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
    // TODO TP01Support.methodBegin(#m, #id);
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
    // TP01Support.procedureBegin(#p, #id);
  }

  @Override
  public void exitProcedurestate(ProcedurestateContext ctx) {
    // TP01Support.procedureEnd();
  }

  @Override
  public void enterPromptforstate(PromptforstateContext ctx) {
    // TODO TP01Support.frameEnablingStatement(#head);
    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.SYMBOL);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitPromptforstate(PromptforstateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void enterPublishstate(PublishstateContext ctx) {
    // TODO TP01Support.callBegin(#pu);
  }

  @Override
  public void exitPublishstate(PublishstateContext ctx) {
    // TODO TP01Support.callEnd();
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
    // TODO TP01Support.blockBegin(#r);
    // TODO TP01Support.frameBlockCheck(#r);
    
    // TODO A revoir, il faut que ce soit fait avant d'entrer dans le code_block
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void exitRepeatstate(RepeatstateContext ctx) {
    // TODO TP01Support.blockEnd();
  }

  @Override
  public void enterRunstate(RunstateContext ctx) {
    // TODO TP01Support.runBegin(#r); 
  }

  @Override
  public void enterRun_set(Run_setContext ctx) {
    if (ctx.field() != null) {
      contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
    }
  }

  @Override
  public void exitRunOptPersistent(RunOptPersistentContext ctx) {
    // TODO TP01Support.runPersistentSet(#hnd);
  }

  @Override
  public void exitRunOptIn(RunOptInContext ctx) {
    // TODO TP01Support.runInHandle(#hexp);
  }

  @Override
  public void exitRunstate(RunstateContext ctx) {
    // TODO TP01Support.runEnd(#r); 
  }

  @Override
  public void enterRunstoredprocedurestate(RunstoredprocedurestateContext ctx) {
    // TODO TP01Support.callBegin()
  }

  @Override
  public void exitRunstoredprocedurestate(RunstoredprocedurestateContext ctx) {
    // TODO TP01Support.callEnd()
  }

  @Override
  public void enterRunsuperstate(RunsuperstateContext ctx) {
    // TODO TP01Support.callBegin()
  }

  @Override
  public void exitRunsuperstate(RunsuperstateContext ctx) {
    // TODO TP01Support.callEnd()
  }

  @Override
  public void enterScrollstate(ScrollstateContext ctx) {
    // TODO TP01Support.frameInitializingStatement(#head);
  }

  @Override
  public void exitScrollstate(ScrollstateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void enterSetstate(SetstateContext ctx) {
    // TODO TP01Support.frameInitializingStatement(#head);
    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.REFUP);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitSetstate(SetstateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
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
  public void enterThisobjectstate(ThisobjectstateContext ctx) {
    // TODO TP01Support.callBegin(#to);
  }

  @Override
  public void exitThisobjectstate(ThisobjectstateContext ctx) {
    // TODO TP01Support.callEnd();
  }

  @Override
  public void enterTrigger_on(Trigger_onContext ctx) {
    // TODO TP01Support.scopeAdd(#on);
  }

  @Override
  public void exitTrigger_on(Trigger_onContext ctx) {
    // TODO TP01Support.scopeClose(#on);
  }

  @Override
  public void enterUnderlinestate(UnderlinestateContext ctx) {
    // TODO TP01Support.frameInitializingStatement(#head);
    for (Field_form_itemContext field : ctx.field_form_item()) {
      contextQualifiers.put(field, ContextQualifier.SYMBOL);
      // TODO TP01Support.formItem(field);
    }
  }

  @Override
  public void exitUnderlinestate(UnderlinestateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void enterUpstate(UpstateContext ctx) {
    // TODO TP01Support.frameInitializingStatement(#head);
  }

  @Override
  public void exitUpstate(UpstateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void enterUpdatestate(UpdatestateContext ctx) {
    // TODO TP01Support.frameEnablingStatement(#head);
    contextQualifiers.put(ctx.form_items_or_record(), ContextQualifier.REFUP);
    if (ctx.except_fields() != null) {
      for (FieldContext fld : ctx.except_fields().field()) {
        contextQualifiers.put(fld, ContextQualifier.SYMBOL);
      }
    }
  }

  @Override
  public void exitUpdatestate(UpdatestateContext ctx) {
    // TODO TP01Support.frameStatementEnd();
  }

  @Override
  public void enterValidatestate(ValidatestateContext ctx) {
    contextQualifiers.put(ctx.record(), ContextQualifier.REF);
  }

  @Override
  public void exitViewstate(ViewstateContext ctx) {
    // TODO TP01Support#viewState();
  }

  @Override
  public void enterWaitfor_set(Waitfor_setContext ctx) {
    contextQualifiers.put(ctx.field(), ContextQualifier.UPDATING);
  }

}
