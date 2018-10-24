package org.prorefactor.proparse.antlr4;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.prorefactor.core.ABLNodeType;
import org.prorefactor.proparse.ParserSupport;
import org.prorefactor.proparse.antlr4.Proparse.Block_forContext;
import org.prorefactor.proparse.antlr4.Proparse.Block_opt_iteratorContext;
import org.prorefactor.proparse.antlr4.Proparse.Block_preselectContext;
import org.prorefactor.proparse.antlr4.Proparse.ExpressionContext;
import org.prorefactor.proparse.antlr4.Proparse.For_record_specContext;
import org.prorefactor.proparse.antlr4.Proparse.FunargsContext;
import org.prorefactor.proparse.antlr4.Proparse.ParameterBufferForContext;
import org.prorefactor.proparse.antlr4.Proparse.ParameterBufferRecordContext;
import org.prorefactor.proparse.antlr4.Proparse.ParameterOtherContext;
import org.prorefactor.proparse.antlr4.Proparse.ProgramContext;
import org.prorefactor.proparse.antlr4.Proparse.PseudfnContext;
import org.prorefactor.proparse.antlr4.Proparse.RecordContext;
import org.prorefactor.proparse.antlr4.Proparse.RecordfuncContext;
import org.prorefactor.proparse.antlr4.Proparse.RecordphraseContext;
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
    unit.setRootScope(rootScope);
    
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
  public void enterFor_record_spec(For_record_specContext ctx) {
    // contextQualifiers.get(ctx);
    for (RecordphraseContext rec : ctx.recordphrase()) {
      // TP01Support.recordNameNode(support.getNode(rec), contextQualifiers.get(ctx));
    }
    // contextQualifiers.removeFrom(ctx);
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
}
