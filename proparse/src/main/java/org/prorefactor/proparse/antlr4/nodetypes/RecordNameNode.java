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
package org.prorefactor.proparse.antlr4.nodetypes;

import org.prorefactor.core.IConstants;
import org.prorefactor.proparse.antlr4.JPNode;
import org.prorefactor.proparse.antlr4.ProToken;
import org.prorefactor.treeparser.BufferScope;
import org.prorefactor.treeparser.symbols.ITableBuffer;

public class RecordNameNode extends JPNode {

  public RecordNameNode(ProToken t) {
    super(t);
  }

  public BufferScope getBufferScope() {
    BufferScope bufferScope = (BufferScope) getLink(IConstants.BUFFERSCOPE);
    assert bufferScope != null;
    return bufferScope;
  }

  public ITableBuffer getTableBuffer() {
    ITableBuffer buffer = (ITableBuffer) getLink(IConstants.SYMBOL);
    assert buffer != null;
    return buffer;
  }

  public void setBufferScope(BufferScope bufferScope) {
    setLink(IConstants.BUFFERSCOPE, bufferScope);
  }

  public void setTableBuffer(ITableBuffer buffer) {
    setLink(IConstants.SYMBOL, buffer);
  }

}
