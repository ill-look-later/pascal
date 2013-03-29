(ns me.arrdem.pascal.grammar
  (:require [name.choi.joshua.fnparse :as fnp]
            [me.arrdem.pascal.tokens :refer :all]
            [me.arrdem.sad.runtime :refer [defrule]]))

;;------------------------------------------------------------------------------
;; Forward declarations of rules generated by sad
(declare
 label-declaration unary-expression subscript-list proc-or-func empty label
 pascal-program simple-type element-list for-list block5 procid case-list
 block-or-forward element variable constant record-field expression
 fixed-part variable-declaration variableid-list case-label-list
 primary-expression fieldid identifier-list field-list block3
 parameterid-list relational-op block4 block1 block2 funcid structured-type
 formal-parameter-list block formal-parameter-section statement-list
 variant-list type-declaration constid expression-list
 proc-and-func-declaration unary-op fieldid-list parameters varid typeid
 statement multiplicative-expression additive-op variant-part
 constant-declaration tag-field ptype record-variable-list program-heading
 additive-expression index-list variant multiplicative-op)

;;------------------------------------------------------------------------------
;; Grammar terminals & nonterminals which were not defined in the grammar
;; TODO Read the k&W book, figure out what these look like and get em defined
(defrule unsigned-integer fnp/anything)
(defrule unsigned-real fnp/anything)
(defrule string fnp/anything)

(defrule integer
  (fnp/conc (fnp/opt (fnp/alt op_add op_sub))
            integer))

(defrule real
  (fnp/conc (fnp/opt (fnp/alt op_add op_sub))
            unsigned-real))

;;------------------------------------------------------------------------------
;; The compiled grammar

(defrule pascal-program
  (fnp/conc tok_program
            identifier
            program-heading
            delim_semi
            block
            op_dot))

(defrule program-heading
  (fnp/conc delim_lparen
            identifier-list
            delim_rparen))

(defrule identifier-list
  (fnp/alt (fnp/conc identifier
                     delim_comma
                     identifier-list)
           identifier))

(defrule block
  (fnp/alt (fnp/conc label-declaration
                     delim_semi
                     block1)
           block1))

(defrule block1
  (fnp/alt (fnp/conc constant-declaration
                     delim_semi
                     block2)
           block2))

(defrule block2
  (fnp/alt (fnp/conc type-declaration
                     delim_semi
                     block3)
           block3))

(defrule block3
  (fnp/alt (fnp/conc variable-declaration
                     delim_semi
                     block4)
           block4))

(defrule block4
  (fnp/alt (fnp/conc proc-and-func-declaration
                     delim_semi
                     block5)
           block5))

(defrule block5
  (fnp/conc tok_begin
            statement-list
            tok_end))

(defrule label-declaration
  (fnp/conc tok_label
            unsigned-integer
            (fnp/rep*
             (fnp/conc
              delim_comma
              unsigned-integer))))

(defrule constant-declaration
  (fnp/alt
   (fnp/conc
    identifier
    op_eq
    constant
    delim_semi
    constant-declaration)
   (fnp/conc tok_const
             identifier
             op_eq
             constant)))

(defrule type-declaration
  (fnp/alt
   (fnp/conc identifier
             op_eq
             ptype
             delim_semi
             type-declaration)
   (fnp/conc tok_type
             identifier
             op_eq
             ptype)))

(defrule vardecl
  (fnp/conc variableid-list
            delim_colon
            ptype))

(defrule vardecls
  (fnp/alt (fnp/conc vardecl delim_semi vardecls)
           vardecl))

(defrule variable-declaration
  (fnp/conc tok_var
            vardecls))

;; variableid-list is now OK, consistently returns a pair [id ids?] where
;; ids? may be nil. semantics also in place.
(defrule variableid-list
  (fnp/conc identifier
            (fnp/opt
             (fnp/conc delim_comma
                       variableid-list))))

(defrule constant
  (fnp/alt integer
           real
           string
           constid
           (fnp/conc op_add constid)
           (fnp/conc op_sub constid)))

(defrule ptype
  (fnp/alt simple-type
           structured-type
           (fnp/conc op_point typeid)))

(defrule simple-type
  (fnp/alt
   (fnp/conc delim_lparen
             identifier-list
             delim_rparen)
   (fnp/conc constant
             delim_dotdot
             constant)
   typeid))

(defrule structured-type
  (fnp/alt
   (fnp/conc tok_array delim_lbrack index-list delim_rbrack tok_of ptype)
   (fnp/conc tok_record field-list tok_end)
   (fnp/conc tok_set tok_of simple-type)
   (fnp/conc tok_file tok_of ptype)
   (fnp/conc tok_packed structured-type)))

(defrule index-list
  (fnp/alt (fnp/conc simple-type
                     delim_comma
                     index-list)
           simple-type))

(defrule field-list
  (fnp/alt
   (fnp/conc fixed-part delim_semi variant-part)
   fixed-part
   variant-part))

(defrule fixed-part
  (fnp/alt record-field
           (fnp/conc fixed-part
                     delim_semi
                     record-field)))

(defrule record-field
  (fnp/alt (fnp/conc fieldid-list
                     delim_colon
                     ptype)
           fnp/emptiness))

(defrule fieldid-list
  (fnp/alt (fnp/conc identifier delim_comma fieldid-list)
           identifier))

(defrule variant-part
  (fnp/conc tok_case
            tag-field
            tok_of
            variant-list))

(defrule tag-field
  (fnp/alt typeid
           (fnp/conc identifier
                     delim_colon
                     typeid)))

(defrule variant-list
  (fnp/alt variant
           (fnp/conc variant-list
                     delim_semi
                     variant)))

(defrule variant
  (fnp/alt
   (fnp/conc case-label-list
             delim_colon
             delim_lparen
             field-list
             delim_rparen)
   fnp/emptiness))

(defrule case-label-list
  (fnp/alt
   (fnp/conc constant
             delim_comma
             case-label-list)
   constant))

(defrule proc-and-func-declaration
  (fnp/alt proc-or-func
           (fnp/conc proc-or-func
                     delim_semi
                     proc-and-func-declaration)))

(defrule parameters?
  (fnp/opt parameters))

(defrule proc-or-func
  (fnp/alt
   (fnp/conc tok_procedure
             identifier
             parameters?
             delim_semi
             block-or-forward)
   (fnp/conc tok_function
             identifier
             parameters?
             delim_colon
             typeid
             delim_semi
             block-or-forward)))

(defrule block-or-forward
  (fnp/alt block
           tok_forward))

(defrule parameters
  (fnp/conc delim_lparen
            formal-parameter-list
            delim_rparen))

(defrule formal-parameter-list
  (fnp/alt (fnp/conc formal-parameter-section
                     delim_semi
                     formal-parameter-list)
           formal-parameter-section))

(defrule formal-parameter-section
  (fnp/alt
   (fnp/conc parameterid-list delim_colon typeid)
   (fnp/conc tok_var parameterid-list delim_colon typeid)
   (fnp/conc tok_procedure identifier (fnp/opt parameters))
   (fnp/conc tok_function identifier (fnp/opt parameters) delim_colon typeid)))

(defrule parameterid-list
  (fnp/alt (fnp/conc identifier delim_comma parameterid-list)
           identifier))

;;------------------------------------------------------------------------------
;; a bunch of rules which were pulled out of statement to make it easier to
;; build hook and semantics operations for them.

(defrule statement-list
  (fnp/alt (fnp/conc statement delim_semi statement-list)
           statement))

(defrule assignment
  (fnp/conc variable op_assign expression))

(defrule statements
  (fnp/conc tok_begin statement-list tok_end))

(defrule ifte
  (fnp/conc tok_if
            expression
            tok_then
            statement
            tok_else
            statement))

(defrule ift
  (fnp/conc tok_if
            expression
            tok_then
            statement))

(defrule case-stmnt
  (fnp/conc tok_case expression tok_of case-list tok_end))

(defrule while-stmnt
  (fnp/conc tok_while expression tok_do statement))

(defrule repeat-stmnt
  (fnp/conc tok_repeat statement-list tok_until expression))

(defrule for-stmnt
  (fnp/conc tok_for varid op_assign for-list tok_do statement))

(defrule procinvoke
  (fnp/conc procid
            (fnp/opt
             (fnp/conc delim_lparen expression-list delim_rparen))))

(defrule goto-stmnt
  (fnp/conc tok_goto label))

(defrule with-stmnt
  (fnp/conc tok_with record-variable-list tok_do statement))

(defrule label-stmnt
  (fnp/conc label delim_colon statement))

;;------------------------------------------------------------------------------
;; Statement v2.0

(defrule statement
  (fnp/alt
   statements
   assignment
   ifte
   ift
   case-stmnt
   while-stmnt
   repeat-stmnt
   for-stmnt
   procinvoke
   goto-stmnt
   with-stmnt
   label-stmnt
   fnp/emptiness))

;; Recursively defines valid variable or address access, being either a
;; raw symbol, a pointer deref or a miltipart indexing operation. Also
;; note that these can be nested arbitrarily, there is nothing wrong with
;; a statement such as
;; a[1, C, 5, 9001].bar[2]^ or soforth.
;; Note however that the `identifier` group is absoltuely terminal here.
;; As this will recur left until an identifier is found, we can devise
;; an equivalnet grammar by beginning with an identifier and recuring
;; right until we have reeached the limit of the tokens which this rule
;; can consume.

(defrule var-postfix
  (fnp/alt
   (fnp/conc delim_lbrack subscript-list delim_rbrack)
   (fnp/conc op_dot fieldid)
   (fnp/conc op_point)))

(defrule var-postfixes
  (fnp/alt (fnp/conc var-postfix var-postfixes)
           var-postfix))

(defrule variable
  (fnp/alt
   (fnp/conc identifier var-postfixes)
   identifier))

(defrule subscript-list
  (fnp/alt (fnp/conc expression delim_comma subscript-list)
           expression))

(defrule case-list
  (fnp/alt
   (fnp/conc statement delim_colon case-label-list delim_semi case-list)
   (fnp/conc statement delim_colon case-label-list)))

(defrule for-list
  (fnp/alt
   (fnp/conc expression tok_to expression)
   (fnp/conc expression tok_downto expression)))

(defrule expression-list
  (fnp/alt expression
           (fnp/conc expression-list
                     delim_comma
                     expression)))

(defrule label
  unsigned-integer)

(defrule record-variable-list
  (fnp/alt variable
           (fnp/conc variable
                     delim_comma
                     record-variable-list)))
(defrule expression
  (fnp/alt
   (fnp/conc expression relational-op additive-expression)
   additive-expression))

(defrule relational-op
  (fnp/alt op_le
           op_ne
           op_lt
           op_ge
           op_eq
           op_gt))

(defrule additive-expression
  (fnp/alt
   (fnp/conc multiplicative-expression
             additive-op
             additive-expression)
   multiplicative-expression))

(defrule additive-op
  (fnp/alt op_add op_sub op_or))

(defrule multiplicative-expression
  (fnp/alt
   (fnp/conc unary-expression
             multiplicative-op
             multiplicative-expression)
   unary-expression))

(defrule multiplicative-op
  (fnp/alt op_mul
           op_div
           op_mod
           op_and
           op_in))

(defrule unary-expression
  (fnp/alt (fnp/conc unary-op unary-expression)
           primary-expression))

(defrule unary-op
  (fnp/alt op_add op_sub op_not))

(defrule primary-expression
  (fnp/alt variable
           unsigned-integer
           unsigned-real
           string
           tok_nil
           (fnp/conc funcid
                     delim_lparen expression-list delim_rparen)
           (fnp/conc delim_lbrack element-list delim_rbrack)
           (fnp/conc delim_lparen expression delim_rparen)))

(defrule element-list
  (fnp/alt (fnp/conc element delim_comma element-list)
           element
           fnp/emptiness))

(defrule element
  (fnp/alt (fnp/conc expression delim_dotdot expression)
           expression))

(defrule constid identifier)
(defrule typeid identifier)
(defrule funcid identifier)
(defrule procid identifier)
(defrule fieldid identifier)
(defrule varid identifier)
