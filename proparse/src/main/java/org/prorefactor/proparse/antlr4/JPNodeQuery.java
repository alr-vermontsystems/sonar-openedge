/*******************************************************************************
 * Copyright (c) 2016-2018 Riverside Software
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gilles Querret - initial API and implementation and/or initial documentation
 *******************************************************************************/ 
package org.prorefactor.proparse.antlr4;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.prorefactor.core.ABLNodeType;

class JPNodeQuery implements ICallback<List<JPNode>> {
  private final List<JPNode> result = new ArrayList<>();
  private final Set<ABLNodeType> findTypes;
  private final boolean stateHeadOnly;
  private final boolean mainFileOnly;
  private final JPNode currStatement;

  /**
   * @deprecated Since 2.1.3, use {@link JPNodeQuery#JPNodeQuery(ABLNodeType, ABLNodeType...)}
   */
  @Deprecated
  public JPNodeQuery(Integer... types) {
    this(false, false, null, types);
  }

  /**
   * @deprecated Since 2.1.3, use {@link JPNodeQuery#JPNodeQuery(boolean, ABLNodeType, ABLNodeType...)}
   */
  @Deprecated
  public JPNodeQuery(boolean stateHeadOnly, Integer... types) {
    this(stateHeadOnly, false, null, types);
  }

  /**
   * @deprecated Since 2.1.3, use {@link JPNodeQuery#JPNodeQuery(boolean, boolean, ABLNodeType, ABLNodeType...)}
   */
  @Deprecated
  public JPNodeQuery(boolean stateHeadOnly, boolean mainFileOnly, JPNode currentStatement, Integer... types) {
    this.stateHeadOnly = stateHeadOnly;
    this.mainFileOnly = mainFileOnly;
    if ((currentStatement != null) && (currentStatement.getStatement() != null)) {
      this.currStatement = currentStatement.getStatement();
    } else {
      this.currStatement = null;
    }
    this.findTypes = new HashSet<>();
    for (Integer i : types) {
      findTypes.add(ABLNodeType.getNodeType(i));
    }
  }

  public JPNodeQuery(ABLNodeType type, ABLNodeType... types) {
    this(false, false, null, type, types);
  }

  public JPNodeQuery(boolean stateHeadOnly, ABLNodeType type, ABLNodeType... types) {
    this(stateHeadOnly, false, null, type , types);
  }

  public JPNodeQuery(boolean stateHeadOnly, boolean mainFileOnly, JPNode currentStatement, ABLNodeType type,  ABLNodeType... types) {
    this.stateHeadOnly = stateHeadOnly;
    this.mainFileOnly = mainFileOnly;
    this.currStatement = currentStatement;
    this.findTypes = EnumSet.of(type, types);
  }

  @Override
  public List<JPNode> getResult() {
    return result;
  }

  @Override
  public boolean visitNode(JPNode node) {
    if ((currStatement != null) && (node.getStatement() != currStatement))
      return false;

    if (mainFileOnly && (node.getFileIndex() > 0))
      return true;

    if (stateHeadOnly && !node.isStateHead())
      return true;

    if (findTypes.isEmpty() || findTypes.contains(node.getNodeType())) {
      result.add(node);
    }

    return true;
  }

}