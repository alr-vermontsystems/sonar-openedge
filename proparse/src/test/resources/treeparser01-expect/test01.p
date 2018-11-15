 /* 0:ROOT buffers=sports2000.Customer,sports2000.State,sports2000.bs,tt1,tt12,wt1 */ 
/* Data file for testing treeparser01.
 */


 /* 0:tt1 */ DEF TEMP-TABLE tt1  /* 0:tt1.f1 */ FIELD f1 AS INT.
 /* 0:wt1 */ DEF WORK-TABLE wt1  /* 0:wt1.f1 */ FIELD f1 AS INT.

FIND FIRST  /* 0:sports2000.Customer */ customer NO-ERROR.
FIND FIRST  /* 0:sports2000.Customer abbrev */ cust NO-ERROR.
DISPLAY /* 0:sports2000.Customer.Address unqualfield */  address /* 0:sports2000.Customer.Balance abbrev unqualfield */  bal.
DISPLAY /* 0:sports2000.Customer.Discount */  customer.discount.
DISPLAY /* 0:sports2000.Customer.Discount abbrev */  customer.disc.
DISPLAY /* 0:sports2000.Customer.Discount abbrev */  cust.discount.
DISPLAY /* 0:sports2000.Customer.Comments abbrev */  sports2000.cust.comm.

 /* 0:outer1 */ DEF VAR outer1 AS INT.

 /* 0:myproc1 buffers=b_tt1,sports2000.b_cust */ PROCEDUREmyproc1:
   /* 1:inner1c */ DEF INPUT PARAMETER inner1c AS INT.
   /* 1:b_tt1 */ DEF BUFFER b_tt1 FOR  /* 0:tt1 */ tt1.
   /* 1:sports2000.b_cust */ DEF BUFFER b_cust FOR  /* 0:sports2000.Customer abbrev */ cust.
   /* 1:inner1a */ DEF VAR inner1a AS INT.
   /* 1:inner1b */ DEF VAR inner1b AS INT.
  DISPLAY /* 1:inner1c */  inner1c.
  FIND FIRST  /* 0:tt1 */ tt1 NO-ERROR.
  FIND FIRST  /* 0:wt1 */ wt1 NO-ERROR.
  FIND FIRST  /* 1:b_tt1 */ b_tt1 NO-ERROR.
  FIND FIRST  /* 1:sports2000.b_cust */ b_cust NO-ERROR.
  DISPLAY /* 1:sports2000.b_cust.Comments abbrev */  b_cust.comm.
END.

 /* 0:outer2 */ DEF VAR outer2 AS INT.

 /* 0:myFunc1 */ FUNCTION myFunc1 RETURNS LOGICAL ( /* 1:inner2c */ inner2c AS INT):
   /* 1:inner2a */ DEF VAR inner2a AS INT.
  ON ENDKEY ANYWHERE DO:
     /* 1:inner2aa */ DEF VAR inner2aa AS INT.
    DISPLAY /* 1:inner2aa */  inner2aa.
    DISPLAY /* 1:inner2a */  inner2a.
    DISPLAY /* 1:inner2c */  inner2c.
    DISPLAY /* 0:outer1 */  outer1.
  END.
   /* 1:inner2b */ DEF VAR inner2b AS INT.
  RETURN TRUE.
END.

 /* 0:outer3 */ DEF VAR outer3 AS INT.


/* Bug in the tree parser used to prevent parameter buffers from working */
 /* 0:tt11 */ DEFINE TEMP-TABLE tt11
   /* 0:tt11.f1 */ FIELD f1 AS CHARACTER.
 /* 0:fn11 buffers=sports2000.bf11 */ function fn11 returns logical
    (    /* 1:sports2000.bf11 */ buffer bf11 for  /* 0:sports2000.Customer */ customer,
        table for  /* 0:tt11 */ tt11 append,
        table  /* 0:tt11 */ tt11,
         /* 1:thandle11 */ table-handle thandle11 append
    ):
  message /* 1:thandle11 */  thandle11.
  find first  /* 1:sports2000.bf11 */ bf11.
  return false.
end.


/* Test that define table LIKE works
 * i.e. Ensure that the field names get copied into the
 * new table def.
 */
 /* 0:tt12a */ def temp-table tt12a
  rcode-information
   /* 0:tt12a.f1 */ field f1 as char.
 /* 0:tt12 */ def temp-table tt12 no-undo like  /* 0:tt12a */ tt12a.
find first  /* 0:tt12 */ tt12.
display /* 0:tt12.f1 */  tt12.f1.


/* Make sure MESSAGE..UPDATE..AS works.
 * Note that defining the variable state changes the "display state"
 * statement. Normally it would display the record (not state.state)
 * but in this case, the variable is displayed.
 */
find first  /* 0:sports2000.State */ state.
MESSAGE "hello" 
  VIEW-AS ALERT-BOX QUESTION BUTTONS YES-NO UPDATE /* 0:state */  state AS LOGICAL.
display /* 0:state */  state.


/* Make sure that we aren't comparing a buffer name to the
 * table name.
 */
 /* 0:sports2000.bs */ define buffer bs for  /* 0:sports2000.State */ state.
find first  /* 0:sports2000.bs */ bs.
display /* 0:sports2000.bs.State */  bs.state.


/* There used to be a problem with references like this... */
 /* 0:state */ DEFINE TEMP-TABLE state NO-UNDO LIKE  /* 0:sports2000.State */ state
        /* 0:state.oldstate */ Field oldstate like /* 0:state.State */  state.state.