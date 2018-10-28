package org.prorefactor.proparse.antlr4;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.prorefactor.core.ABLNodeType;
import org.prorefactor.proparse.ParserSupport;
import org.prorefactor.proparse.antlr4.Proparse.*;
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
  public void enterDynamic_new(Dynamic_newContext ctx) {
    // TODO TP01Support.callBegin();
  }

  @Override
  public void exitDynamic_new(Dynamic_newContext ctx) {
    // TODO TP01Support.callEnd();
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
