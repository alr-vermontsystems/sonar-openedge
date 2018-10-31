/********************************************************************************
 * Copyright (c) 2003-2015 John Green
 * Copyright (c) 2015-2018 Riverside Software
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU Lesser General Public License v3.0
 * which is available at https://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * SPDX-License-Identifier: EPL-2.0 OR LGPL-3.0
 ********************************************************************************/
package org.prorefactor.proparse.antlr4.treeparser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.prorefactor.core.nodetypes.RecordNameNode;
import org.prorefactor.proparse.antlr4.JPNode;
import org.prorefactor.treeparser.BufferScope;
import org.prorefactor.treeparser.BufferScope.Strength;
import org.prorefactor.treeparser.FieldLookupResult;
import org.prorefactor.treeparser.IBlock;
import org.prorefactor.treeparser.ITreeParserSymbolScope;
import org.prorefactor.treeparser.symbols.ITableBuffer;
import org.prorefactor.treeparser.symbols.widgets.Frame;

/**
 * For keeping track of blocks, block attributes, and the things that are scoped within those blocks - especially buffer
 * scopes.
 */
public class Block implements IBlock {

  private List<Frame> frames = new ArrayList<>();
  private IBlock parent;
  private Frame defaultFrame = null;
  private JPNode blockStatementNode;
  private Set<BufferScope> bufferScopes = new HashSet<>();

  /**
   * The SymbolScope for a block is going to be the root program scope, unless the block is inside a method
   * (function/trigger/procedure).
   */
  private ITreeParserSymbolScope symbolScope;

  /** For constructing nested blocks */
  public Block(IBlock parent, JPNode node) {
    this.blockStatementNode = node;
    this.parent = parent;
    this.symbolScope = parent.getSymbolScope();
  }

  /**
   * For constructing a root (method root or program root) block.
   * 
   * @param symbolScope
   * @param node Is the Program_root if this is the program root block.
   */
  public Block(ITreeParserSymbolScope symbolScope, JPNode node) {
    this.blockStatementNode = node;
    this.symbolScope = symbolScope;
    if (symbolScope.getParentScope() != null)
      this.parent = symbolScope.getParentScope().getRootBlock();
    else
      this.parent = null; // is program-block
  }

  @Override
  public JPNode getNode() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ITreeParserSymbolScope getSymbolScope() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public org.prorefactor.treeparser.BufferScope getBufferForReference(ITableBuffer symbol) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public org.prorefactor.treeparser.BufferScope addWeakBufferScope(ITableBuffer symbol) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void addStrongBufferScope(RecordNameNode node) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void addHiddenCursor(RecordNameNode node) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void addBufferScopeReferences(org.prorefactor.treeparser.BufferScope bufferScope) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public org.prorefactor.treeparser.BufferScope getBufferForReferenceSub(ITableBuffer symbol) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public org.prorefactor.treeparser.BufferScope getBufferScopeSub(ITableBuffer symbol, Strength creating) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public org.prorefactor.treeparser.BufferScope findBufferScope(ITableBuffer symbol) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setParent(IBlock parent) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public IBlock getParent() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Frame getDefaultFrame() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<Frame> getFrames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public IBlock addFrame(Frame frame) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setDefaultFrameExplicit(Frame frame) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public IBlock setDefaultFrameImplicit(Frame frame) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public FieldLookupResult lookupUnqualifiedField(String name) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public FieldLookupResult lookupField(String name, boolean getBufferScope) {
    // TODO Auto-generated method stub
    return null;
  }
}
