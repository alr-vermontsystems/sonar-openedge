/********************************************************************************
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
package org.prorefactor.proparse.antlr4;

import javax.annotation.Nonnull;

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.prorefactor.core.ABLNodeType;
import org.prorefactor.core.JPNode.Builder;
import org.prorefactor.core.ProToken;
import org.prorefactor.core.ProparseRuntimeException;
import org.prorefactor.proparse.ParserSupport;
import org.prorefactor.proparse.antlr4.Proparse.*;

public class JPNodeVisitor extends ProparseBaseVisitor<Builder> {
  private final ParserSupport support;
  private final BufferedTokenStream stream;

  public JPNodeVisitor(ParserSupport support, BufferedTokenStream stream) {
    this.support = support;
    this.stream = stream;
  }

  @Override
  public Builder visitProgram(ProgramContext ctx) {
    return createTree(ctx, ABLNodeType.PROGRAM_ROOT, ABLNodeType.PROGRAM_TAIL);
  }

  @Override
  public Builder visitCode_block(Code_blockContext ctx) {
    support.visitorEnterScope(ctx.getParent());
    Builder retVal = createTree(ctx, ABLNodeType.CODE_BLOCK);
    support.visitorExitScope(ctx.getParent());

    return retVal;
  }

  @Override
  public Builder visitClass_code_block(Class_code_blockContext ctx) {
    return createTree(ctx, ABLNodeType.CODE_BLOCK);
  }

  @Override
  public Builder visitEmpty_statement(Empty_statementContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDot_comment(Dot_commentContext ctx) {
    ProToken start = (ProToken) ctx.getStart();
    StringBuilder sb = new StringBuilder(".");
    for (int zz = 0; zz < ctx.not_state_end().size(); zz++) {
      sb.append(ctx.not_state_end(zz).getText()).append(' ');
    }
    ProToken last = (ProToken) ctx.state_end().stop;

    start.setType(ABLNodeType.DOT_COMMENT.getType());
    start.setText(sb.toString());
    start.setEndFileIndex(last.getEndFileIndex());
    start.setEndLine(last.getEndLine());
    start.setEndCharPositionInLine(last.getEndCharPositionInLine());

    return new Builder(start).setRuleNode(ctx);
  }

  @Override
  public Builder visitFunc_call_statement(Func_call_statementContext ctx) {
    return createTree(ctx, ABLNodeType.EXPR_STATEMENT).setStatement();
  }

  @Override
  public Builder visitFunc_call_statement2(Func_call_statement2Context ctx) {
    return createTreeFromFirstNode(ctx).changeType(
        ABLNodeType.getNodeType(support.isMethodOrFunc(ctx.fname.getText())));
  }

  @Override
  public Builder visitExpression_statement(Expression_statementContext ctx) {
    return createTree(ctx, ABLNodeType.EXPR_STATEMENT).setStatement();
  }

  @Override
  public Builder visitLabeled_block(Labeled_blockContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBlock_for(Block_forContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBlock_opt_iterator(Block_opt_iteratorContext ctx) {
    return createTree(ctx, ABLNodeType.BLOCK_ITERATOR);
  }

  @Override
  public Builder visitBlock_opt_while(Block_opt_whileContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBlock_opt_group_by(Block_opt_group_byContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBlock_preselect(Block_preselectContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitPseudfn(PseudfnContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBuiltinfunc(BuiltinfuncContext ctx) {
    if (ctx.getChild(0) instanceof TerminalNode) {
      return createTreeFromFirstNode(ctx);
    }
    return visitChildren(ctx);
  }

  @Override
  public Builder visitArgfunc(ArgfuncContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    if (holder.getNodeType() == ABLNodeType.COMPARES)
      holder.changeType(ABLNodeType.COMPARE);
    return holder;
  }

  @Override
  public Builder visitOptargfunc(OptargfuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRecordfunc(RecordfuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitParameterBufferFor(ParameterBufferForContext ctx) {
    return createTreeFromFirstNode(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitParameterBufferRecord(ParameterBufferRecordContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitParameterOther(ParameterOtherContext ctx) {
    if (ctx.p == null) {
      return createTree(ctx, ABLNodeType.INPUT);
    } else {
      return createTreeFromFirstNode(ctx);
    }
  }

  @Override
  public Builder visitParameterlist(ParameterlistContext ctx) {
    return createTree(ctx, ABLNodeType.PARAMETER_LIST);
  }

  @Override
  public Builder visitEventlist(EventlistContext ctx) {
    return createTree(ctx, ABLNodeType.EVENT_LIST);
  }

  @Override
  public Builder visitAnyOrValueValue(AnyOrValueValueContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAnyOrValueAny(AnyOrValueAnyContext ctx) {
    return visitChildren(ctx).changeType(ABLNodeType.TYPELESS_TOKEN);
  }

  @Override
  public Builder visitValueexpression(ValueexpressionContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  // ----------
  // EXPRESSION
  // ----------

  @Override
  public Builder visitExpressionMinus(ExpressionMinusContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    holder.changeType(ABLNodeType.UNARY_MINUS);
    return holder;
  }

  @Override
  public Builder visitExpressionPlus(ExpressionPlusContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    holder.changeType(ABLNodeType.UNARY_PLUS);
    return holder;
  }

  @Override
  public Builder visitExpressionOp1(ExpressionOp1Context ctx) {
    Builder holder = createTreeFromSecondNode(ctx).setOperator();
    if (holder.getNodeType() == ABLNodeType.STAR)
      holder.changeType(ABLNodeType.MULTIPLY);
    else if (holder.getNodeType() == ABLNodeType.SLASH)
      holder.changeType(ABLNodeType.DIVIDE);
    return holder;
  }

  @Override
  public Builder visitExpressionOp2(ExpressionOp2Context ctx) {
    return createTreeFromSecondNode(ctx).setOperator();
  }

  @Override
  public Builder visitExpressionComparison(ExpressionComparisonContext ctx) {
    Builder holder = createTreeFromSecondNode(ctx).setOperator();
    if (holder.getNodeType() == ABLNodeType.LEFTANGLE)
      holder.changeType(ABLNodeType.LTHAN);
    else if (holder.getNodeType() == ABLNodeType.LTOREQUAL)
      holder.changeType(ABLNodeType.LE);
    else if (holder.getNodeType() == ABLNodeType.RIGHTANGLE)
      holder.changeType(ABLNodeType.GTHAN);
    else if (holder.getNodeType() == ABLNodeType.GTOREQUAL)
      holder.changeType(ABLNodeType.GE);
    else if (holder.getNodeType() == ABLNodeType.GTORLT)
      holder.changeType(ABLNodeType.NE);
    else if (holder.getNodeType() == ABLNodeType.EQUAL)
      holder.changeType(ABLNodeType.EQ);

    return holder;
  }

  @Override
  public Builder visitExpressionStringComparison(ExpressionStringComparisonContext ctx) {
    return createTreeFromSecondNode(ctx).setOperator();
  }

  @Override
  public Builder visitExpressionNot(ExpressionNotContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitExpressionAnd(ExpressionAndContext ctx) {
    return createTreeFromSecondNode(ctx).setOperator();
  }

  @Override
  public Builder visitExpressionOr(ExpressionOrContext ctx) {
    return createTreeFromSecondNode(ctx).setOperator();
  }

  // ---------------
  // EXPRESSION BITS
  // ---------------

  @Override
  public Builder visitExprtNoReturnValue(ExprtNoReturnValueContext ctx) {
    return createTree(ctx, ABLNodeType.WIDGET_REF);
  }

  @Override
  public Builder visitExprtWidName(ExprtWidNameContext ctx) {
    return createTree(ctx, ABLNodeType.WIDGET_REF);
  }

  @Override
  public Builder visitExprtExprt2(ExprtExprt2Context ctx) {
    if (ctx.attr_colon() != null) {
      return createTree(ctx, ABLNodeType.WIDGET_REF);
    }
    return visitChildren(ctx);
  }

  @Override
  public Builder visitExprt2ParenExpr(Exprt2ParenExprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitExprt2ParenCall(Exprt2ParenCallContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    holder.changeType(ABLNodeType.getNodeType(support.isMethodOrFunc(ctx.fname.getText())));
    return holder;
  }

  @Override
  public Builder visitExprt2New(Exprt2NewContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitExprt2ParenCall2(Exprt2ParenCall2Context ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    holder.changeType(ABLNodeType.LOCAL_METHOD_REF);
    return holder;
  }

  @Override
  public Builder visitExprt2Field(Exprt2FieldContext ctx) {
    if (ctx.ENTERED() != null)
      return createTree(ctx, ABLNodeType.ENTERED_FUNC);
    else
      return visitChildren(ctx);
  }

  @Override
  public Builder visitWidattrWidName(WidattrWidNameContext ctx) {
    return createTree(ctx, ABLNodeType.WIDGET_REF);
  }

  @Override
  public Builder visitWidattrExprt2(WidattrExprt2Context ctx) {
    return createTree(ctx, ABLNodeType.WIDGET_REF);
  }

  @Override
  public Builder visitGwidget(GwidgetContext ctx) {
    return createTree(ctx, ABLNodeType.WIDGET_REF);
  }

  @Override
  public Builder visitFiln(FilnContext ctx) {
    return visitChildren(ctx);
  }

  @Override
  public Builder visitFieldn(FieldnContext ctx) {
    return visitChildren(ctx);
  }

  @Override
  public Builder visitField(FieldContext ctx) {
    Builder holder = createTree(ctx, ABLNodeType.FIELD_REF).setRuleNode(ctx);
    if ((ctx.getParent() instanceof Message_optContext) && support.isInlineVar(ctx.getText())) {
      holder.setInlineVar();
    }
    return holder;
  }

  @Override
  public Builder visitField_frame_or_browse(Field_frame_or_browseContext ctx) {
    return createTreeFromFirstNode(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitArray_subscript(Array_subscriptContext ctx) {
    return createTree(ctx, ABLNodeType.ARRAY_SUBSCRIPT);
  }

  @Override
  public Builder visitMethod_param_list(Method_param_listContext ctx) {
    return createTree(ctx, ABLNodeType.METHOD_PARAM_LIST);
  }

  @Override
  public Builder visitInuic(InuicContext ctx) {
    return createTreeFromFirstNode(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitRecordAsFormItem(RecordAsFormItemContext ctx) {
    return createTree(ctx, ABLNodeType.FORM_ITEM).setRuleNode(ctx);
  }


  @Override
  public Builder visitRecord(RecordContext ctx) {
    return visitChildren(ctx).changeType(ABLNodeType.RECORD_NAME).setStoreType(support.getRecordExpression(ctx)).setRuleNode(ctx);
  }

  @Override
  public Builder visitBlocklabel(BlocklabelContext ctx) {
    return visitChildren(ctx).changeType(ABLNodeType.BLOCK_LABEL);
  }

  @Override
  public Builder visitIdentifierUKW(IdentifierUKWContext ctx) {
    return visitChildren(ctx).changeType(ABLNodeType.ID);
  }

  @Override
  public Builder visitNew_identifier(New_identifierContext ctx) {
    return visitChildren(ctx).changeType(ABLNodeType.ID);
  }


  @Override
  public Builder visitFilename(FilenameContext ctx) {
    ProToken start = (ProToken) ctx.t1.start;
    ProToken last = start;
    StringBuilder sb = new StringBuilder(ctx.t1.getText());
    for (int zz = 1; zz < ctx.filename_part().size(); zz++) {
      last = (ProToken) ctx.filename_part(zz).start;
      sb.append(last.getText());
    }
    
    start.setType(ABLNodeType.FILENAME.getType());
    start.setText(sb.toString());
    start.setEndFileIndex(last.getEndFileIndex());
    start.setEndLine(last.getEndLine());
    start.setEndCharPositionInLine(last.getEndCharPositionInLine());
    return new Builder(start).setRuleNode(ctx);
  }

  @Override
  public Builder visitType_name(Type_nameContext ctx) {
    return visitChildren(ctx).changeType(ABLNodeType.TYPE_NAME).setClassname(support.lookupClassName(ctx.getText()));
  }

  @Override
  public Builder visitType_name2(Type_name2Context ctx) {
    return visitChildren(ctx).changeType(ABLNodeType.TYPE_NAME);
  }

  @Override
  public Builder visitWidname(WidnameContext ctx) {
    return visitChildren(ctx).setRuleNode(ctx);
  }

  // **********
  // Statements
  // **********

  @Override
  public Builder visitAatraceclosestate(AatraceclosestateContext ctx) {
    return  createStatementTreeFromFirstNode(ctx, ABLNodeType.CLOSE);
  }

  @Override
  public Builder visitAatraceonoffstate(AatraceonoffstateContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    if (ctx.OFF() != null)
      holder.setStatement(ABLNodeType.OFF);
    else
      holder.setStatement(ABLNodeType.ON);
    return holder;
  }

  @Override
  public Builder visitAatracestate(AatracestateContext ctx) {
    return  createStatementTreeFromFirstNode(ctx);
    }

  @Override
  public Builder visitAccumulatestate(AccumulatestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAggregatephrase(AggregatephraseContext ctx) {
    return createTree(ctx, ABLNodeType.AGGREGATE_PHRASE);
  }

  @Override
  public Builder visitAggregate_opt(Aggregate_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAll_except_fields(All_except_fieldsContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAnalyzestate(AnalyzestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAnnotation(AnnotationContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitApplystate(ApplystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitApplystate2(Applystate2Context ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAssign_opt(Assign_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAssign_opt2(Assign_opt2Context ctx) {
    return createTreeFromSecondNode(ctx).setOperator();
  }

  @Override
  public Builder visitAssignstate(AssignstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAssignstate2(Assignstate2Context ctx) {
    Builder node1 = createTreeFromSecondNode(ctx).setOperator();

    Builder holder = new Builder(ABLNodeType.ASSIGN).setStatement().setDown(node1);
    Builder lastNode = node1;
    for (int zz = 3; zz < ctx.getChildCount(); zz++) {
      lastNode = lastNode.setRight(visit(ctx.getChild(zz))).getLast();
    }

    return holder;
  }

  @Override
  public Builder visitAssign_equal(Assign_equalContext ctx) {
    return createTreeFromSecondNode(ctx).setOperator();
  }

  @Override
  public Builder visitAssign_field(Assign_fieldContext ctx) {
    return createTree(ctx, ABLNodeType.ASSIGN_FROM_BUFFER);
  }

  @Override
  public Builder visitAt_expr(At_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAtphrase(AtphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitAtphraseab(AtphraseabContext ctx) {
    Builder builder = createTreeFromFirstNode(ctx);
    if (builder.getNodeType() == ABLNodeType.COLUMNS)
      builder.changeType(ABLNodeType.COLUMN);
    else if (builder.getNodeType() == ABLNodeType.COLOF)
      builder.changeType(ABLNodeType.COLUMNOF);

    return builder;
  }

  @Override
  public Builder visitBellstate(BellstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBuffercomparestate(BuffercomparestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBuffercompare_save(Buffercompare_saveContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBuffercompare_result(Buffercompare_resultContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBuffercompares_block(Buffercompares_blockContext ctx) {
    return createTree(ctx, ABLNodeType.CODE_BLOCK);
  }

  @Override
  public Builder visitBuffercompare_when(Buffercompare_whenContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBuffercompares_end(Buffercompares_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBuffercopystate(BuffercopystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBuffercopy_assign(Buffercopy_assignContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBy_expr(By_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCache_expr(Cache_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCallstate(CallstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCasesensNot(CasesensNotContext ctx) {
    return createTree(ctx, ABLNodeType.NOT_CASESENS);
  }

  @Override
  public Builder visitCasestate(CasestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCase_block(Case_blockContext ctx) {
    return createTree(ctx, ABLNodeType.CODE_BLOCK);
  }

  @Override
  public Builder visitCase_when(Case_whenContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCaseExpression1(CaseExpression1Context ctx) {
    return visitChildren(ctx);
  }
  
  @Override
  public Builder visitCaseExpression2(CaseExpression2Context ctx) {
    return createTreeFromSecondNode(ctx).setOperator();
  }

  @Override
  public Builder visitCase_expr_term(Case_expr_termContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCase_otherwise(Case_otherwiseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCase_end(Case_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCatchstate(CatchstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCatch_end(Catch_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitChoosestate(ChoosestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitChoose_field(Choose_fieldContext ctx) {
    return createTree(ctx, ABLNodeType.FORM_ITEM).setRuleNode(ctx);
  }

  @Override
  public Builder visitEnumstate(EnumstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDefenumstate(DefenumstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.ENUM);
  }

  @Override
  public Builder visitEnum_end(Enum_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitClassstate(ClassstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitClass_inherits(Class_inheritsContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitClass_implements(Class_implementsContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitClass_end(Class_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitClearstate(ClearstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitClosequerystate(ClosequerystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.QUERY);
  }

  @Override
  public Builder visitClosestoredprocedurestate(ClosestoredprocedurestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.STOREDPROCEDURE);
  }

  @Override
  public Builder visitClosestored_where(Closestored_whereContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCollatephrase(CollatephraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitColor_anyorvalue(Color_anyorvalueContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitColor_expr(Color_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitColorspecification(ColorspecificationContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitColor_display(Color_displayContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitColor_prompt(Color_promptContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    if (holder.getNodeType() == ABLNodeType.PROMPTFOR)
      holder.changeType(ABLNodeType.PROMPT);
    return holder;
  }

  @Override
  public Builder visitColorstate(ColorstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitColumn_expr(Column_exprContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    if (holder.getNodeType() == ABLNodeType.COLUMNS)
      holder.changeType(ABLNodeType.COLUMN);
    return holder;
  }

  @Override
  public Builder visitColumnformat(ColumnformatContext ctx) {
    return createTree(ctx, ABLNodeType.FORMAT_PHRASE);
  }

  @Override
  public Builder visitColumnformat_opt(Columnformat_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitComboboxphrase(ComboboxphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCombobox_opt(Combobox_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCompilestate(CompilestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCompile_opt(Compile_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCompile_lang(Compile_langContext ctx) {
    return visitChildren(ctx);
  }

  @Override
  public Builder visitCompile_lang2(Compile_lang2Context ctx) {
    return visitChildren(ctx).changeType(ABLNodeType.TYPELESS_TOKEN);
  }

  @Override
  public Builder visitCompile_into(Compile_intoContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCompile_equal(Compile_equalContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCompile_append(Compile_appendContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCompile_page(Compile_pageContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitConnectstate(ConnectstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitConstructorstate(ConstructorstateContext ctx) {
    Builder holder = createStatementTreeFromFirstNode(ctx);
    Builder typeName = holder.getDown();
    if (typeName.getNodeType() != ABLNodeType.TYPE_NAME)
      typeName = typeName.getRight();
    if (typeName.getNodeType() == ABLNodeType.TYPE_NAME) {
      typeName.setClassname(support.getClassName());
    }
    return holder;
  }

  @Override
  public Builder visitConstructor_end(Constructor_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitContexthelpid_expr(Contexthelpid_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitConvertphrase(ConvertphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCopylobstate(CopylobstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCopylob_for(Copylob_forContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCopylob_starting(Copylob_startingContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFor_tenant(For_tenantContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCreatestate(CreatestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCreate_whatever_state(Create_whatever_stateContext ctx) {
    Builder holder = createStatementTreeFromFirstNode(ctx);
    holder.setStatement(holder.getDown().getNodeType());
    return holder;
  }

  @Override
  public Builder visitCreatealiasstate(CreatealiasstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.ALIAS);
  }

  @Override
  public Builder visitCreate_connect(Create_connectContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCreatebrowsestate(CreatebrowsestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.BROWSE);
  }

  @Override
  public Builder visitCreatequerystate(CreatequerystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.QUERY);
  }

  @Override
  public Builder visitCreatebufferstate(CreatebufferstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.BUFFER);
  }

  @Override
  public Builder visitCreatebuffer_name(Createbuffer_nameContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCreatedatabasestate(CreatedatabasestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.DATABASE);
  }

  @Override
  public Builder visitCreatedatabase_from(Createdatabase_fromContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitCreateserverstate(CreateserverstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.SERVER);
  }

  @Override
  public Builder visitCreateserversocketstate(CreateserversocketstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.SERVERSOCKET);
  }

  @Override
  public Builder visitCreatesocketstate(CreatesocketstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.SOCKET);
  }

  @Override
  public Builder visitCreatetemptablestate(CreatetemptablestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.TEMPTABLE);
  }

  @Override
  public Builder visitCreatewidgetstate(CreatewidgetstateContext ctx) {
    if (ctx.create_connect() == null)
      return createStatementTreeFromFirstNode(ctx, ABLNodeType.WIDGET);
    else
      return createStatementTreeFromFirstNode(ctx, ABLNodeType.AUTOMATION_OBJECT);
  }

  @Override
  public Builder visitCreatewidgetpoolstate(CreatewidgetpoolstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.WIDGETPOOL);
  }

  @Override
  public Builder visitCanfindfunc(CanfindfuncContext ctx) {
    return createTreeFromFirstNode(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitCurrentvaluefunc(CurrentvaluefuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDatatype_var(Datatype_varContext ctx) {
    Builder builder = visitChildren(ctx);
    if (builder.getNodeType() == ABLNodeType.IN)
      builder.changeType(ABLNodeType.INTEGER);
    else if (builder.getNodeType() == ABLNodeType.LOG)
      builder.changeType(ABLNodeType.LOGICAL);
    else if (builder.getNodeType() == ABLNodeType.ROW)
      builder.changeType(ABLNodeType.ROWID);
    else if (builder.getNodeType() == ABLNodeType.WIDGET)
      builder.changeType(ABLNodeType.WIDGETHANDLE);
    else if (ctx.id != null)
      builder.changeType(ABLNodeType.getNodeType(support.abbrevDatatype(ctx.id.getText())));

    return builder;
  }

  @Override
  public Builder visitDdeadvisestate(DdeadvisestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.ADVISE);
  }

  @Override
  public Builder visitDdeexecutestate(DdeexecutestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.EXECUTE);
  }

  @Override
  public Builder visitDdegetstate(DdegetstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.GET);
  }

  @Override
  public Builder visitDdeinitiatestate(DdeinitiatestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.INITIATE);
  }

  @Override
  public Builder visitDderequeststate(DderequeststateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.REQUEST);
  }

  @Override
  public Builder visitDdesendstate(DdesendstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.SEND);
  }

  @Override
  public Builder visitDdeterminatestate(DdeterminatestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.TERMINATE);
  }

  @Override
  public Builder visitDecimals_expr(Decimals_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDefault_expr(Default_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDefinebrowsestate(DefinebrowsestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.BROWSE);
  }

  @Override
  public Builder visitDefinebufferstate(DefinebufferstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.BUFFER);
  }

  @Override
  public Builder visitDefinedatasetstate(DefinedatasetstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.DATASET);
  }

  @Override
  public Builder visitDefinedatasourcestate(DefinedatasourcestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.DATASOURCE);
  }

  @Override
  public Builder visitDefineeventstate(DefineeventstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.EVENT);
  }

  @Override
  public Builder visitDefineframestate(DefineframestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.FRAME);
  }

  @Override
  public Builder visitDefineimagestate(DefineimagestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.IMAGE);
  }

  @Override
  public Builder visitDefinemenustate(DefinemenustateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.MENU);
  }

  @Override
  public Builder visitDefineparameterstate(DefineparameterstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.PARAMETER);
  }

  @Override
  public Builder visitDefineparam_var(Defineparam_varContext ctx) {
    Builder retVal = visitChildren(ctx).moveRightToDown();
    if (retVal.getDown().getNodeType() == ABLNodeType.CLASS)
      retVal.moveRightToDown();

    return retVal;
  }

  @Override
  public Builder visitDefinepropertystate(DefinepropertystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.PROPERTY);
  }

  @Override
  public Builder visitDefinequerystate(DefinequerystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.QUERY);
  }

  @Override
  public Builder visitDefinerectanglestate(DefinerectanglestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.RECTANGLE);
  }

  @Override
  public Builder visitDefinestreamstate(DefinestreamstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.STREAM);
  }

  @Override
  public Builder visitDefinesubmenustate(DefinesubmenustateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.SUBMENU);
  }

  @Override
  public Builder visitDefinetemptablestate(DefinetemptablestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.TEMPTABLE);
  }

  @Override
  public Builder visitDefineworktablestate(DefineworktablestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.WORKTABLE);
  }

  @Override
  public Builder visitDefinevariablestate(DefinevariablestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.VARIABLE);
  }

  @Override
  public Builder visitDefine_share(Define_shareContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDef_browse_display(Def_browse_displayContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDef_browse_display_item(Def_browse_display_itemContext ctx) {
    return createTree(ctx, ABLNodeType.FORM_ITEM).setRuleNode(ctx);
  }

  @Override
  public Builder visitDef_browse_enable(Def_browse_enableContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDef_browse_enable_item(Def_browse_enable_itemContext ctx) {
    return createTree(ctx, ABLNodeType.FORM_ITEM).setRuleNode(ctx);
  }

  @Override
  public Builder visitDefinebuttonstate(DefinebuttonstateContext ctx) {
    Builder builder = createStatementTreeFromFirstNode(ctx, ABLNodeType.BUTTON);
    if (builder.getDown().getNodeType() == ABLNodeType.BUTTONS)
      builder.getDown().changeType(ABLNodeType.BUTTON);
    return builder;
  }

  @Override
  public Builder visitButton_opt(Button_optContext ctx) {
    if ((ctx.IMAGEDOWN() != null) || (ctx.IMAGE() != null) || (ctx.IMAGEUP() != null)
        || (ctx.IMAGEINSENSITIVE() != null) || (ctx.MOUSEPOINTER() != null) || (ctx.NOFOCUS() != null))
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitData_relation(Data_relationContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitParent_id_relation(Parent_id_relationContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitField_mapping_phrase(Field_mapping_phraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDatarelation_nested(Datarelation_nestedContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitEvent_signature(Event_signatureContext ctx) {
    if (ctx.SIGNATURE() != null)
      return createTreeFromFirstNode(ctx);
    else
      return createTree(ctx, ABLNodeType.SIGNATURE);
  }

  @Override
  public Builder visitEvent_delegate(Event_delegateContext ctx) {
    if (ctx.DELEGATE() != null)
      return createTreeFromFirstNode(ctx);
    else
      return createTree(ctx, ABLNodeType.DELEGATE);
  }

  @Override
  public Builder visitDefineimage_opt(Defineimage_optContext ctx) {
    if (ctx.STRETCHTOFIT() != null)
      return createTreeFromFirstNode(ctx);
    else
      return visitChildren(ctx);
  }

  @Override
  public Builder visitMenu_list_item(Menu_list_itemContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitMenu_item_opt(Menu_item_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDefineproperty_accessor_getblock(Defineproperty_accessor_getblockContext ctx) {
    return createTree(ctx, ABLNodeType.PROPERTY_GETTER).setRuleNode(ctx);
  }

  @Override
  public Builder visitDefineproperty_accessor_setblock(Defineproperty_accessor_setblockContext ctx) {
    return createTree(ctx, ABLNodeType.PROPERTY_SETTER).setRuleNode(ctx);
  }

  @Override
  public Builder visitRectangle_opt(Rectangle_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDef_table_beforetable(Def_table_beforetableContext ctx) {
    return createTreeFromFirstNode(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitDef_table_like(Def_table_likeContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDef_table_useindex(Def_table_useindexContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDef_table_field(Def_table_fieldContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx).setRuleNode(ctx);
    if (holder.getNodeType() == ABLNodeType.FIELDS)
      holder.changeType(ABLNodeType.FIELD);
    return holder;
  }

  @Override
  public Builder visitDef_table_index(Def_table_indexContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDeletestate(DeletestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDeletealiasstate(DeletealiasstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.ALIAS);
  }

  @Override
  public Builder visitDeleteobjectstate(DeleteobjectstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.OBJECT);
  }

  @Override
  public Builder visitDeleteprocedurestate(DeleteprocedurestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.PROCEDURE);
  }

  @Override
  public Builder visitDeletewidgetstate(DeletewidgetstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.WIDGET);
  }

  @Override
  public Builder visitDeletewidgetpoolstate(DeletewidgetpoolstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.WIDGETPOOL);
  }

  @Override
  public Builder visitDelimiter_constant(Delimiter_constantContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDestructorstate(DestructorstateContext ctx) {
    Builder holder = createStatementTreeFromFirstNode(ctx);
    Builder typeName = holder.getDown();
    if (typeName.getNodeType() != ABLNodeType.TYPE_NAME)
      typeName = typeName.getRight();
    if (typeName.getNodeType() == ABLNodeType.TYPE_NAME) {
      typeName.setClassname(support.getClassName());
    }

    return holder;
  }

  @Override
  public Builder visitDestructor_end(Destructor_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDictionarystate(DictionarystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDisablestate(DisablestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDisabletriggersstate(DisabletriggersstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.TRIGGERS);
  }

  @Override
  public Builder visitDisconnectstate(DisconnectstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDisplaystate(DisplaystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDisplay_item(Display_itemContext ctx) {
    return createTree(ctx, ABLNodeType.FORM_ITEM).setRuleNode(ctx);
  }

  @Override
  public Builder visitDisplay_with(Display_withContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDostate(DostateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDownstate(DownstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDynamiccurrentvaluefunc(DynamiccurrentvaluefuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitDynamicnewstate(DynamicnewstateContext ctx) {
    return createTree(ctx, ABLNodeType.ASSIGN_DYNAMIC_NEW).setStatement();
  }

  @Override
  public Builder visitField_equal_dynamic_new(Field_equal_dynamic_newContext ctx) {
    return createTreeFromSecondNode(ctx).setOperator();
  }

  @Override
  public Builder visitDynamic_new(Dynamic_newContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitEditorphrase(EditorphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitEditor_opt(Editor_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitEmptytemptablestate(EmptytemptablestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitEnablestate(EnablestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitEditingphrase(EditingphraseContext ctx) {
    // TODO Double check
    return createTree(ctx, ABLNodeType.EDITING_PHRASE);
  }

  @Override
  public Builder visitEntryfunc(EntryfuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitExcept_fields(Except_fieldsContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitExcept_using_fields(Except_using_fieldsContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitExportstate(ExportstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitExtentphrase(ExtentphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitField_form_item(Field_form_itemContext ctx) {
    return createTree(ctx, ABLNodeType.FORM_ITEM).setRuleNode(ctx);
  }

  @Override
  public Builder visitField_list(Field_listContext ctx) {
    return createTree(ctx, ABLNodeType.FIELD_LIST);
  }

  @Override
  public Builder visitFields_fields(Fields_fieldsContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    if (holder.getNodeType() == ABLNodeType.FIELD)
      holder.changeType(ABLNodeType.FIELDS);
    return holder;
  }

  @Override
  public Builder visitFieldoption(FieldoptionContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFillinphrase(FillinphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFinallystate(FinallystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFinally_end(Finally_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFindstate(FindstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFont_expr(Font_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitForstate(ForstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFormat_expr(Format_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitForm_item(Form_itemContext ctx) {
    return createTree(ctx, ABLNodeType.FORM_ITEM).setRuleNode(ctx);
  }

  @Override
  public Builder visitFormstate(FormstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFormatphrase(FormatphraseContext ctx) {
    return createTree(ctx, ABLNodeType.FORMAT_PHRASE);
  }

  @Override
  public Builder visitFormat_opt(Format_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFrame_widgetname(Frame_widgetnameContext ctx) {
    return createTreeFromFirstNode(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitFramephrase(FramephraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFrame_exp_col(Frame_exp_colContext ctx) {
    return createTree(ctx, ABLNodeType.WITH_COLUMNS);
  }

  @Override
  public Builder visitFrame_exp_down(Frame_exp_downContext ctx) {
    return createTree(ctx, ABLNodeType.WITH_DOWN);
  }

  @Override
  public Builder visitBrowse_opt(Browse_optContext ctx) {
    if (ctx.DOWN() != null)
      return visitChildren(ctx);
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFrame_opt(Frame_optContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    if (holder.getNodeType() == ABLNodeType.COLUMNS)
      holder.changeType(ABLNodeType.COLUMN);
    return holder;
  }

  @Override
  public Builder visitFrameviewas(FrameviewasContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFrameviewas_opt(Frameviewas_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFrom_pos(From_posContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFunctionstate(FunctionstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitExt_functionstate(Ext_functionstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFunction_end(Function_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFunction_params(Function_paramsContext ctx) {
    return createTree(ctx, ABLNodeType.PARAMETER_LIST);
  }

  @Override
  public Builder visitFunctionParamBufferFor(FunctionParamBufferForContext ctx) {
    return createTreeFromFirstNode(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitFunctionParamStandard(FunctionParamStandardContext ctx) {
    if (ctx.qualif == null)
      return createTree(ctx, ABLNodeType.INPUT);
    else
      return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitFunctionParamStandardAs(FunctionParamStandardAsContext ctx) {
    return visitChildren(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitFunctionParamStandardTableHandle(FunctionParamStandardTableHandleContext ctx) {
    return visitChildren(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitFunctionParamStandardDatasetHandle(FunctionParamStandardDatasetHandleContext ctx) {
    return visitChildren(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitGetstate(GetstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitGetkeyvaluestate(GetkeyvaluestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitGoonphrase(GoonphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitHeader_background(Header_backgroundContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitHelp_const(Help_constContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitHidestate(HidestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitIfstate(IfstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitIf_else(If_elseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitIn_expr(In_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitIn_window_expr(In_window_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitImagephrase_opt(Imagephrase_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitImportstate(ImportstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitIn_widgetpool_expr(In_widgetpool_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitInitial_constant(Initial_constantContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitInputclearstate(InputclearstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.CLEAR);
  }

  @Override
  public Builder visitInputclosestate(InputclosestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.CLOSE);
  }

  @Override
  public Builder visitInputfromstate(InputfromstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.FROM);
  }

  @Override
  public Builder visitInputthroughstate(InputthroughstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.THROUGH);
  }

  @Override
  public Builder visitInputoutputclosestate(InputoutputclosestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.CLOSE);
  }

  @Override
  public Builder visitInputoutputthroughstate(InputoutputthroughstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.THROUGH);
  }

  @Override
  public Builder visitInsertstate(InsertstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitInterfacestate(InterfacestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitInterface_inherits(Interface_inheritsContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitInterface_end(Interface_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitIoPhraseAnyTokensSub3(IoPhraseAnyTokensSub3Context ctx) {
    ProToken start = (ProToken) ctx.getStart();
    ProToken last = start;
    StringBuilder sb = new StringBuilder(start.getText());
    for (int zz = 1; zz < ctx.not_io_opt().size(); zz++) {
      last = (ProToken) ctx.not_io_opt(zz).start;
      sb.append(last.getText());
    }
    
    start.setType(ABLNodeType.FILENAME.getType());
    start.setText(sb.toString());
    start.setEndFileIndex(last.getEndFileIndex());
    start.setEndLine(last.getEndLine());
    start.setEndCharPositionInLine(last.getEndCharPositionInLine());

    return new Builder(start).setRuleNode(ctx);
  }

  @Override
  public Builder visitIo_opt(Io_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitIo_osdir(Io_osdirContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitIo_printer(Io_printerContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLabel_constant(Label_constantContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLdbnamefunc(LdbnamefuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLdbname_opt1(Ldbname_opt1Context ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLeavestate(LeavestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLengthfunc(LengthfuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLike_field(Like_fieldContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLike_widgetname(Like_widgetnameContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLoadstate(LoadstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitLoad_opt(Load_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitMessagestate(MessagestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitMessage_item(Message_itemContext ctx) {
    return createTree(ctx, ABLNodeType.FORM_ITEM).setRuleNode(ctx);
  }

  @Override
  public Builder visitMessage_opt(Message_optContext ctx) {
    Builder builder = createTreeFromFirstNode(ctx);
    Builder tmp = builder.getDown();
    while (tmp != null) {
      if (tmp.getNodeType() == ABLNodeType.BUTTON)
        tmp.changeType(ABLNodeType.BUTTONS);
      tmp = tmp.getRight();
    }
    return builder;
  }

  @Override
  public Builder visitMethodstate(MethodstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitMethod_end(Method_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitNamespace_prefix(Namespace_prefixContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitNamespace_uri(Namespace_uriContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitNextstate(NextstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitNextpromptstate(NextpromptstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitNextvaluefunc(NextvaluefuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitNullphrase(NullphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOnstate(OnstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOnAssign(OnAssignContext ctx) {
    return visitChildren(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitOnstate_run_params(Onstate_run_paramsContext ctx) {
    return createTree(ctx, ABLNodeType.PARAMETER_LIST);
  }

  @Override
  public Builder visitOn___phrase(On___phraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOn_undo(On_undoContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOn_action(On_actionContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOpenquerystate(OpenquerystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.QUERY);
  }

  @Override
  public Builder visitOpenquery_opt(Openquery_optContext ctx) {
    if (ctx.MAXROWS() != null)
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitOsappendstate(OsappendstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOscommandstate(OscommandstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOscopystate(OscopystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOscreatedirstate(OscreatedirstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOsdeletestate(OsdeletestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOsrenamestate(OsrenamestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitOutputclosestate(OutputclosestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.CLOSE);
  }

  @Override
  public Builder visitOutputthroughstate(OutputthroughstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.THROUGH);
  }

  @Override
  public Builder visitOutputtostate(OutputtostateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.TO);
  }

  @Override
  public Builder visitPagestate(PagestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitPause_expr(Pause_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitPausestate(PausestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitPause_opt(Pause_optContext ctx) {
    if (ctx.MESSAGE() != null)
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitProcedure_expr(Procedure_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitProcedurestate(ProcedurestateContext ctx) {
    Builder holder = createStatementTreeFromFirstNode(ctx);
    holder.getDown().changeType(ABLNodeType.ID);
    return holder;
  }

  @Override
  public Builder visitExt_procedurestate(Ext_procedurestateContext ctx) {
    Builder holder = createStatementTreeFromFirstNode(ctx);
    holder.getDown().changeType(ABLNodeType.ID);
    holder.getDown().getRight().moveRightToDown();

    return holder;
  }

  @Override
  public Builder visitProcedure_opt(Procedure_optContext ctx) {
    if (ctx.EXTERNAL() != null)
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitProcedure_dll_opt(Procedure_dll_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitProcedure_end(Procedure_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitProcesseventsstate(ProcesseventsstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitPromptforstate(PromptforstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx).changeType(ABLNodeType.PROMPTFOR);
  }

  @Override
  public Builder visitPublishstate(PublishstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitPublish_opt1(Publish_opt1Context ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitPutstate(PutstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitPutcursorstate(PutcursorstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.CURSOR);
  }

  @Override
  public Builder visitPutscreenstate(PutscreenstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.SCREEN);
  }

  @Override
  public Builder visitPutkeyvaluestate(PutkeyvaluestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitQuery_queryname(Query_querynameContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitQuerytuningphrase(QuerytuningphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitQuerytuning_opt(Querytuning_optContext ctx) {
    if ((ctx.CACHESIZE() != null) || (ctx.DEBUG() != null) || (ctx.HINT() != null))
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitQuitstate(QuitstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRadiosetphrase(RadiosetphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRadioset_opt(Radioset_optContext ctx) {
    if (ctx.RADIOBUTTONS() != null)
      return createTreeFromFirstNode(ctx);
    else
      return visitChildren(ctx);
  }

  @Override
  public Builder visitRadio_label(Radio_labelContext ctx) {
    Builder holder = visitChildren(ctx);
    if (holder.getNodeType() != ABLNodeType.QSTRING)
      holder.changeType(ABLNodeType.UNQUOTEDSTRING);
    return holder;
  }

  @Override
  public Builder visitRawfunc(RawfuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRawtransferstate(RawtransferstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitReadkeystate(ReadkeystateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRepeatstate(RepeatstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRecord_fields(Record_fieldsContext ctx) {
    Builder holder = createTreeFromFirstNode(ctx);
    if (holder.getNodeType() == ABLNodeType.FIELD)
      holder.changeType(ABLNodeType.FIELDS);
    return holder;
  }

  @Override
  public Builder visitRecordphrase(RecordphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRecord_opt(Record_optContext ctx) {
    if ((ctx.LEFT() != null) || (ctx.OF() != null) || (ctx.WHERE() != null) || (ctx.USEINDEX() != null) || (ctx.USING() != null))
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitReleasestate(ReleasestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitReleaseexternalstate(ReleaseexternalstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.EXTERNAL);
  }

  @Override
  public Builder visitReleaseobjectstate(ReleaseobjectstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.OBJECT);
  }

  @Override
  public Builder visitRepositionstate(RepositionstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitReposition_opt(Reposition_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitReturnstate(ReturnstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRoutinelevelstate(RoutinelevelstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitBlocklevelstate(BlocklevelstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRow_expr(Row_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRunstate(RunstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRunOptPersistent(RunOptPersistentContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRunOptSingleRun(RunOptSingleRunContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRunOptSingleton(RunOptSingletonContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRunOptServer(RunOptServerContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRunOptAsync(RunOptAsyncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRun_event(Run_eventContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRun_set(Run_setContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitRunstoredprocedurestate(RunstoredprocedurestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.STOREDPROCEDURE);
  }

  @Override
  public Builder visitRunsuperstate(RunsuperstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.SUPER);
  }

  @Override
  public Builder visitSavecachestate(SavecachestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitScrollstate(ScrollstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSeekstate(SeekstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSelectionlistphrase(SelectionlistphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSelectionlist_opt(Selectionlist_optContext ctx) {
    if ((ctx.LISTITEMS() != null) || (ctx.LISTITEMPAIRS() != null) || (ctx.INNERCHARS() != null)
        || (ctx.INNERLINES() != null))
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitSerialize_name(Serialize_nameContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSetstate(SetstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitShowstatsstate(ShowstatsstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSizephrase(SizephraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSkipphrase(SkipphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSliderphrase(SliderphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSlider_opt(Slider_optContext ctx) {
    if ((ctx.MAXVALUE() != null) || (ctx.MINVALUE() != null) || (ctx.TICMARKS() != null))
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitSlider_frequency(Slider_frequencyContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSpacephrase(SpacephraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitStatusstate(StatusstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitStatus_opt(Status_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitStop_after(Stop_afterContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitStopstate(StopstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitStream_name_or_handle(Stream_name_or_handleContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSubscribestate(SubscribestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSubscribe_run(Subscribe_runContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSubstringfunc(SubstringfuncContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSystemdialogcolorstate(SystemdialogcolorstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.COLOR);
  }

  @Override
  public Builder visitSystemdialogfontstate(SystemdialogfontstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.FONT);
  }

  @Override
  public Builder visitSysdiafont_opt(Sysdiafont_optContext ctx) {
    if ((ctx.MAXSIZE() != null) || (ctx.MINSIZE() != null))
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitSystemdialoggetdirstate(SystemdialoggetdirstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.GETDIR);
  }

  @Override
  public Builder visitSystemdialoggetdir_opt(Systemdialoggetdir_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSystemdialoggetfilestate(SystemdialoggetfilestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.GETFILE);
  }

  @Override
  public Builder visitSysdiagetfile_opt(Sysdiagetfile_optContext ctx) {
    if ((ctx.FILTERS() != null) || (ctx.DEFAULTEXTENSION() != null) || (ctx.INITIALDIR() != null)
        || (ctx.UPDATE() != null))
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitSysdiagetfile_initfilter(Sysdiagetfile_initfilterContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSystemdialogprintersetupstate(SystemdialogprintersetupstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx, ABLNodeType.PRINTERSETUP);
  }

  @Override
  public Builder visitSysdiapri_opt(Sysdiapri_optContext ctx) {
    if (ctx.NUMCOPIES() != null)
      return createTreeFromFirstNode(ctx);
    return visitChildren(ctx);
  }

  @Override
  public Builder visitSystemhelpstate(SystemhelpstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSystemhelp_window(Systemhelp_windowContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitSystemhelp_opt(Systemhelp_optContext ctx) {
    if (ctx.children.size() > 1)
      return createTreeFromFirstNode(ctx);
    else
      return visitChildren(ctx);
  }

  @Override
  public Builder visitText_opt(Text_optContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTextphrase(TextphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitThisobjectstate(ThisobjectstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTitle_expr(Title_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTime_expr(Time_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTitlephrase(TitlephraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTo_expr(To_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitToggleboxphrase(ToggleboxphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTooltip_expr(Tooltip_exprContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTransactionmodeautomaticstate(TransactionmodeautomaticstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTriggerphrase(TriggerphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTrigger_block(Trigger_blockContext ctx) {
    return createTree(ctx, ABLNodeType.CODE_BLOCK);
  }

  @Override
  public Builder visitTrigger_on(Trigger_onContext ctx) {
    return createTreeFromFirstNode(ctx).setRuleNode(ctx);
  }

  @Override
  public Builder visitTriggers_end(Triggers_endContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTriggerprocedurestate(TriggerprocedurestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTriggerOfSub1(TriggerOfSub1Context ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTriggerOfSub2(TriggerOfSub2Context ctx) {
    support.defVar(ctx.id.getText());
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTrigger_table_label(Trigger_table_labelContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitTrigger_old(Trigger_oldContext ctx) {
    Builder node = createTreeFromFirstNode(ctx);
    support.defVar(ctx.id.getText());
    return node;
  }

  @Override
  public Builder visitUnderlinestate(UnderlinestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUndostate(UndostateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUndo_action(Undo_actionContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUnloadstate(UnloadstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUnsubscribestate(UnsubscribestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUpstate(UpstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUpdate_field(Update_fieldContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUpdatestate(UpdatestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUsestate(UsestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUsing_row(Using_rowContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitUsingstate(UsingstateContext ctx) {
    Builder using = visit(ctx.USING());
    using.setStatement();
    
    ProToken typ = (ProToken) ctx.type.start;
    typ.setNodeType(ABLNodeType.TYPE_NAME);
    if (ctx.star != null) {
      typ.setText(typ.getText() + "*");
      typ.setEndFileIndex(((ProToken) ctx.star) .getEndFileIndex());
      typ.setEndLine(((ProToken) ctx.star).getEndLine());
      typ.setEndCharPositionInLine(((ProToken) ctx.star).getEndCharPositionInLine());
    }
    Builder child1 = new Builder(typ).setRuleNode(ctx);
    using.setDown(child1);

    Builder last = child1.getLast();
    if (ctx.using_from() != null) {
      last = last.setRight(visit(ctx.using_from())).getRight();
    }
    last.setRight(visit(ctx.state_end()));
    support.usingState(typ.getText());

    return using;
  }

  @Override
  public Builder visitUsing_from(Using_fromContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitValidatephrase(ValidatephraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitValidatestate(ValidatestateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitViewstate(ViewstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitViewasphrase(ViewasphraseContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitWaitforstate(WaitforstateContext ctx) {
    return createStatementTreeFromFirstNode(ctx).changeType(ABLNodeType.WAITFOR);
  }

  @Override
  public Builder visitWaitfor_or(Waitfor_orContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitWaitfor_focus(Waitfor_focusContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitWaitfor_set(Waitfor_setContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitWhen_exp(When_expContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitWidget_id(Widget_idContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitXml_data_type(Xml_data_typeContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitXml_node_name(Xml_node_nameContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  @Override
  public Builder visitXml_node_type(Xml_node_typeContext ctx) {
    return createTreeFromFirstNode(ctx);
  }

  // ------------------
  // Internal functions
  // ------------------

  /**
   * Default behavior for each ParseTree node is to create an array of JPNode.
   * ANTLR2 construct ruleName: TOKEN TOKEN | rule TOKEN | rule ...
   */
  @Override
  @Nonnull
  public Builder visitChildren(RuleNode ctx) {
    if (ctx.getChildCount() == 0)
      return new Builder(ABLNodeType.EMPTY_NODE);

    Builder firstNode = visit(ctx.getChild(0));
    Builder lastNode = firstNode.getLast();

    for (int zz = 1; zz < ctx.getChildCount(); zz++) {
      Builder xx = visit(ctx.getChild(zz));
      if (lastNode != null) {
        lastNode = lastNode.setRight(xx).getLast();
      } else if (xx != null) {
        firstNode = xx;
        lastNode = firstNode.getLast();
      }
    }
    return firstNode;
  }

  /**
   * Generate Builder with only one JPNode object
   */
  @Override
  @Nonnull
  public Builder visitTerminal(TerminalNode node) {
    ProToken tok = (ProToken) node.getSymbol();

    ProToken lastHiddenTok = null;
    ProToken firstHiddenTok = null;

    ProToken t = node.getSymbol().getTokenIndex() > 0 ? (ProToken) stream.get(node.getSymbol().getTokenIndex() - 1)
        : null;
    while ((t != null) && (t.getChannel() != Token.DEFAULT_CHANNEL)) {
      if (firstHiddenTok == null) {
        firstHiddenTok = t;
      } else {
        lastHiddenTok.setHiddenBefore(t);
      }
      lastHiddenTok = t;

      t = t.getTokenIndex() > 0 ? (ProToken) stream.get(t.getTokenIndex() - 1) : null;
    }
    if (firstHiddenTok != null)
      tok.setHiddenBefore(firstHiddenTok);

    return new Builder(tok);
  }

  @Override
  protected Builder aggregateResult(Builder aggregate, Builder nextResult) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  protected Builder defaultResult() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Builder visitErrorNode(ErrorNode node) {
    throw new ProparseRuntimeException(node.getText());
  }

  /**
   * ANTLR2 construct ruleName: TOKEN^ (TOKEN | rule)....
   */
  @Nonnull
  private Builder createTreeFromFirstNode(RuleNode ctx) {
    Builder node = visit(ctx.getChild(0));

    Builder firstChild = node.getDown();
    Builder lastChild = firstChild == null ? null : firstChild.getLast();

    for (int zz = 1; zz < ctx.getChildCount(); zz++) {
      Builder xx = visit(ctx.getChild(zz));
      if (lastChild != null) {
        lastChild = lastChild.setRight(xx).getLast();
      } else if (xx != null) {
        firstChild = xx;
        lastChild = firstChild.getLast();
      }
    }
    node.setDown(firstChild);
    return node;
  }

  /**
   * ANTLR2 construct ruleName: TOKEN^ (TOKEN | rule).... { ##.setStatementHead(); }
   */
  @Nonnull
  private Builder createStatementTreeFromFirstNode(RuleNode ctx) {
    return createTreeFromFirstNode(ctx).setStatement().setRuleNode(ctx);
  }

  /**
   * ANTLR2 construct ruleName: TOKEN^ (TOKEN | rule).... { ##.setStatementHead(state2); }
   */
  @Nonnull
  private Builder createStatementTreeFromFirstNode(RuleNode ctx, ABLNodeType state2) {
    return createTreeFromFirstNode(ctx).setStatement(state2).setRuleNode(ctx);
  }

  /**
   * ANTLR2 construct ruleName: exp OR^ exp ...
   */
  @Nonnull
  private Builder createTreeFromSecondNode(RuleNode ctx) {
    Builder node = visit(ctx.getChild(1));
    Builder left = visit(ctx.getChild(0));
    Builder right = visit(ctx.getChild(2));

    node.setDown(left);
    left.getLast().setRight(right);
    Builder lastNode = node.getLast();
    for (int zz = 3; zz < ctx.getChildCount(); zz++) {
      lastNode = lastNode.setRight(visit(ctx.getChild(zz))).getLast();
    }
    node.setRuleNode(ctx);
    return node;
  }

  /**
   * ANTLR2 construct ruleName: rule | token ... {## = #([NodeType], ##);}
   */
  @Nonnull
  private Builder createTree(RuleNode ctx, ABLNodeType parentType) {
    return new Builder(parentType).setDown(visitChildren(ctx));
  }

  /**
   * ANTLR2 construct ruleName: rule | token ... {## = #([NodeType], ##, [TailNodeType]);}
   */
  @Nonnull
  private Builder createTree(RuleNode ctx, ABLNodeType parentType, ABLNodeType tail) {
    Builder node = new Builder(parentType);
    Builder down = visitChildren(ctx);
    node.setDown(down);
    down.getLast().setRight(new Builder(tail));
    node.setRuleNode(ctx);
    return node;
  }

}

