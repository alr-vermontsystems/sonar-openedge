package org.prorefactor.proparse.antlr4;

import java.util.ArrayList;
import java.util.List;

import org.prorefactor.proparse.ParserSupport;
import org.prorefactor.proparse.ProParserTokenTypes;
import org.prorefactor.proparse.antlr4.Proparse.ProgramContext;
import org.prorefactor.proparse.antlr4.nodetypes.BlockNode;
import org.prorefactor.proparse.antlr4.treeparser.Block;
import org.prorefactor.refactor.RefactorSession;
import org.prorefactor.treeparser.IBlock;
import org.prorefactor.treeparser.ITreeParserRootSymbolScope;
import org.prorefactor.treeparser.ITreeParserSymbolScope;
import org.prorefactor.treeparser.ParseUnit;
import org.prorefactor.treeparser.TreeParserRootSymbolScope;
import org.prorefactor.treeparser.TreeParserSymbolScope;
import org.prorefactor.treeparser.symbols.Routine;

public class TreeParser extends ProparseBaseListener {

  private final ParserSupport support;
  private final RefactorSession refSession;
  private final ParseUnit unit;
  private final ITreeParserRootSymbolScope rootScope;

  private IBlock currentBlock;
  private ITreeParserSymbolScope currentScope;
  
  /*
   * Note that blockStack is *only* valid for determining the current block - the stack itself cannot be used for
   * determining a block's parent, buffer scopes, etc. That logic is found within the Block class. Conversely, we cannot
   * use Block.parent to find the current block when we close out a block. That is because a scope's root block parent
   * is always the program block, but a programmer may code a scope into a non-root block... which we need to make
   * current again once done inside the scope.
   */
  private List<IBlock> blockStack = new ArrayList<>();

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
    r.setProgressType(ProParserTokenTypes.Program_root);
    r.setDefOrIdNode(blockNode);
    blockNode.setSymbol(r);
    rootScope.add(r);
    currentRoutine = r;
    rootRoutine = r;

  }
}
