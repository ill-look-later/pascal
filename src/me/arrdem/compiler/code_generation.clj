(ns me.arrdem.compiler.code-generation
  (:require [me.arrdem.compiler.symtab :refer [gensym!]]
            (me.arrdem.compiler.code-generation [data-segment :refer :all]
                                                [registers :refer :all])))

;;------------------------------------------------------------------------------
;; genarith and supporting functions

(declare genc genarith genop genc genderef gensub genmul genadd genfuncall
         genlabel gengoto genitof loadlit loadsym genaref genprogn genif
         genitof gennot genand genxor genor genlsh genrsh gendiv)

(defn genaddr [state sym-or-expr]
  (cond (string? sym-or-expr)
        (let [[state sym] (preamble-ensure-installed state sym-or-expr)]
          [state nil sym])

        (list? sym-or-expr)
            (genarith state sym-or-expr)))

(defn genlr
  "Utility function which wraps the normal case of processing arguments in which
   both the left and right hand sides of an expression are treated as
   expressions and computed via genarith."
  [state [lhs rhs]]
  (let [[state lhs-code lhs-dst] (genarith state lhs)
        state (-> state
                  (use-reg lhs-dst))
        [state rhs-code rhs-dst] (genarith state rhs)]
    [state [lhs-code lhs-dst]
           [rhs-code rhs-dst]]))

;; forward declare genarith's helpers...

(defn genarith
  "Fundimental arithmatic expression generator. Operates on typed expressions
   via the me.arrdem.compiler/ITyped protocol. For all cases, returns a tripple
   (new-state, code, save-register). All registers are assumed to be free save
   for the return register. Other registers may be meaningful, but such meaning
   is entirely optional and may be discarded at any time."
  [state expr]
  (cond
   (list? expr)
     ((case (first expr)
       (:=)            genc
       (deref)         genderef
       (aref)          genaref
       (progn)         genprogn
       (integer->real) genitof
       (real->integer) genftoi
       (funcall)       genfuncall
       (if)            genif
       (+)             genadd
       (-)             gensub
       (*)             genmul
       (/)             gendiv
       (<<)            genlsh
       (>>)            genrsh
       (and & &&)      genand
       (or ||)         genor
       (xor)           genxor
       (not)           gennot
       )
      state expr)
       ;; note that division and modulus are not implemented.

   (number? expr)
     (loadlit state expr)

   (string? expr)
     (loadsym state expr)
   ))

;;------------------------------------------------------------------------------
;; fragment generators used to simplify the genarith function.

(defn genc
  "Assignment operator generator which treats the left hand side differently,
   being an address generating expression if an expression, or performs an
   address computation (but not a load) if symbolic. Returns a tripple (state,
   code, dst) for dst being constantly nil."
  [state [_assignop lhs rhs]]
  (let [[state lhs-code lhs-dst] (genaddr state lhs)
        state (-> state
                  (use-reg lhs-dst))
        [state rhs-code rhs-dst] (genarith state rhs)]
    [state
     (concat [;['comment (format "[genc] %s" (list := lhs rhs))]
              ]
             lhs-code
             rhs-code
             [; ['comment "write back to target address"]
              ['mov ['addrof lhs-dst] rhs-dst]])
     nil]))

(defn genderef
  "Deref operation generator. Only takes on argument expression, computing it
   via (genaddr) and emits a single mov instruction to dereference the value
   in the returned register."
  [state [_deref expr]]
  (let [[state code dst] (genaddr state expr)]
    [state
     (concat [;['comment (format "[genderef] %s" (list 'deref expr))]
              ]
             code
             [;['comment "deref returned value..."]
              ['mov ['addrof dst] dst]])
     nil]))

;; declare the math functions in a block since they share a huge ammount of code

(defn genop [iformatstr fformatstr state [op l r]]
  (let [[state coder dstr] (genarith state r)
        state (-> state
                  (use-reg dstr))
        [state codel dstl] (genarith state l)]
    ;; it is an error guranteed by the AST system to invoke genop with a float
    ;; to nonfloat conversion implicitly.
    (assert (let [floats? (map (partial = '[st 0]) [l r])]
              (or (every? identity floats?)
                  (not (some identity floats?)))))

    (if (and (= dstr '[st 0])
             (= dstl '[st 0]))
      ;; float case
      [state
       (concat codel
               coder
               [fformatstr])
       '[st 0]]

      ;; integer case
      (list (-> state
                (free-reg dstr)
                (use-reg dstl))
            (concat codel
                    coder
                    [[iformatstr dstl dstr]])
            dstl))))

(defn genadd [state arg]
  ((partial genop 'add
                  '[fadd [st 0] [st 1]])
   state arg))

(defn gensub [state arg]
  ((partial genop 'sub
                  '[fsub [st 0] [st 1]])
   state arg))

(defn genmul [state arg]
  ((partial genop 'mul
                  '[fmul [st 0] [st 1]])
   state arg))

(defn gendiv [state arg]
  ((partial genop 'div
                  '[fdiv [st 0] [st 1]])
   state arg))

(defmacro definstr-trivial [sym opcode]
  `(defn ~sym [state0# [_# lhs# rhs#]]
     (let [[state1# lcode# ldst#] (genarith state0# lhs#)
           state1# (-> state1# (use-reg ldst#))
           [state2# rcode# rdst#] (genarith state1# rhs#)]
       (assert (not (= "st(0)" ldst#)))
       (assert (not (= "st(0)" rdst#)))
       [(-> state2#
            (use-reg ldst#)
            (free-reg rdst#))
        (concat lcode# rcode#
                [[(quote ~opcode) ldst# rdst#]])
        ldst#])))

(definstr-trivial genrsh shr)
(definstr-trivial genlsh shl)
(definstr-trivial genand and)
(definstr-trivial genor  or)
(definstr-trivial genxor xor)
(definstr-trivial gennot not)

(defn genfuncall
  "Generates a function call for a rather silly calling convention. Single
   argument functions only, taking arguments from the RAX register, returning
   values via the RAX register. If RAX is already used, then we have to swap the
   value of RAX out and preserve it with a temp register, otherwise we just
   clobber RAX and yield RAX as our destination register."
  [state [_funcall fn arg]]
  (let [[state code dst] (genarith state arg)]
    (if (contains? (used-regs state) '%rax)
      ;; rax is used case
      (let [[state tmp] (reg-alloc state)]
        [(-> state (free-reg tmp))
         (concat [;['comment (format "[genfuncall] %s %s" fn arg)]
                  ;['comment "compute the argument.."]
                  ]
                 code
                  [['mov tmp 'rax]
                   ['mov 'rax dst]
                   ['call fn]
                   ['mov dst 'rax]
                   ['mov 'rax tmp]])
         dst])

      ;; rax is unused case
        [(-> state
             (free-reg dst)
             (use-reg '%rax))
         (concat [;['comment (format "[genfuncall] %s %s\n" fn arg)]
                  ;["    ;; compute the argument..\n"]
                  ]
                 code
                  [['mov 'rax dst]
                   ['call fn]])
         "%rax"])))

(defn genlabel
  "A simple but essential function for emitting ASM labels"
  [state [_label id]]
  [state
   ['.label (format "l_%s:\n" id)]
   nil])

(defn gengoto
  "A simple but essential function for emitting toto statements"
  [state [_goto id]]
  [state
   ['jml (format "l_%s" id)]
   nil])

(defn loadlit
  "Generates the appropriate literal loading code for both floating point and
   integer values. Used to encode AST literals in to ASM, always consuming
   either a float or integer register. As with the gen* function series yields
   a (state code dst) tripple."
  [state lit]
  (case (typeof lit)
    ("float" "real") ;; both because my type naming is bad..
        (let [[state label] (preamble-install-float state lit)]
          [state
           [['fld ['addrof label]]]
         '[st 0]])

    ("integer" nil)
        (let [[state dst] (reg-alloc state)]
          [(-> state
               (free-reg dst))
           [['mov dst lit]]
           dst])))

(defn loadsym
  "Loads a symbol's value in memory to a register, returning the usual tripple
   for continuity with the other state manipulation functions."
  [state sym-id]
  (let [[state label] (preamble-ensure-installed state sym-id)]
    (case (typeof sym-id)
      ("real" "float")
        [state
         [['fld ['addrof label]]]
         '[st 0]]

    ("string")
      (let [[state reg] (reg-alloc state)
            [state label] (preamble-install-string state sym-id)]
        [state
         [['mov reg ['addrof label]]]
         reg])

      (let [[state dst] (reg-alloc state)]
        [(-> state
             (free-reg dst))
         [['mov dst ['addrof label]]]
         dst]))))

(defn genaref
  "Generates an array reference, being another case of having to call genaddr on
   the left hand side rather than simply being (deref (+ <base> <offset>)) which
   is what I was hoping to transform this into. Generates the usual tripple."
  [state [_aref base-sym offset]]
  (let [[state label] (preamble-ensure-installed state base-sym)
        [state code dst] (genarith state offset)]
    [state
     (concat [;['comment (format "aref of %s by %s\n" base-sym offset)]
              ]
             code
              [['add dst label]
               ['mov ['addrof dst] dst]])
     dst]))

(defn genprogn
  "Generates a progn group, being simply the accumulation of genarith carrying
   state over the argument expressions. Returns the same tripple as every other
   genarith helper function does."
  [state [_progn & exprs]]
  (reduce (fn [[state code] expr]
            (let [[state new-code] (genarith state expr)]
              [state (concat code new-code) nil]))
          [state []] exprs))

(defn genitof
  "Generates an integer to floating point push."
  [state expr]
  (let [[_itof sym] expr
        [state code dst] (genarith state sym)]
    [state
     (concat code
             [;['comment (format "int -> float %s" expr)]
              ['fild dst]])
     '[st 0]]))

(defn genftoi
  "Generates a floating point to integer conversion. Note that this conversion
   is naive and provides no protection against errors resulting from conversion
   failures."
  [state [_ftoi expr]]
  (let [[state ecode fdest] (genarith state expr)
        [state rcode dst] (reg-alloc state)]
    [state
     (concat ecode
             rcode
             [['fist dst]])
     dst]))

(defn genconditional
  "Generates a conditional expression preface, suitable for use in choosing
   \"if\" causes (hint hint). Takes a state, two expressions and two labels
   as arguments. The position of the labels is arbitrary, but if the generated
   code will jump to the first label if the condition is true."
  [state [comparison casel caser] true-l false-l]
  (let [[state [codel dstl]
               [coder dstr]]
            (genlr state [casel caser])]
    [state
     (concat [;['comment (format "(if %s %s %s)\n" comparison true-l false-l)]
              ;['comment "left expr"]
              ]
             codel
             [;['comment "right expr"]
              ]
             coder
             [;['comment "and compare"]
              ['test dstl dstr]
              [(case comparison
                 (<=) 'jle
                 (<)  'jl
                 (>=) 'jge
                 (>)  'jg
                 (==) 'je
                 (<>) 'jne)
                true-l]
              ['jmp false-l]])
     nil]))

(defn genif
  "Has the unfortunate job of generating code for conditional expressions. Some
   sort of helper function like (genconditional state true-target false-target)
   could really help me out here."
  [state [_if predicate true-case false-case]]
  (let [true-l (gensym!)
        false-l (gensym!)
        end-l (gensym!)
        [state pred-code _] (genconditional predicate true-l false-l)
        [state true-case-code _] (genarith state true-case)
        [state false-case-code _] (genarith state false-case)
        [_ true-l _]  (genlabel state true-l)
        [_ false-l _] (genlabel state false-l)
        [_ end-l _]   (genlabel state end-l)
        [_ goto-end-l _] (gengoto state end-l)]
    [state
     (concat pred-code
             true-l
             true-case-code
             goto-end-l
             false-l
             false-case-code
             end-l)
     nil]))

;;------------------------------------------------------------------------------
;; main entry point function which is used to compute the .s output for an
;; entire compiler AST. Note that it is not mutually recursive with genarith
;; and that it simply returns a list of asm forms & discards the state record.

(defn ir->code [forms]
  (let [[state code _]
        (genarith {:free-regs x86-regs
                   :used-regs #{}
                   :preamble {}}
                  forms)]
    (concat code
            (buid-preamble state))))

(defn gencode
  "A function designed to take an IR AST exactly as generated & defined by the
   rest of the compiler. Note that the last of the (program) group's forms is
   the (progn) for which I am required to emit code, conseqently it is the only
   form whic1h is extracted from the (program) sequence argument."
  [[_program name & forms]]
  (ir->code (last forms)))
