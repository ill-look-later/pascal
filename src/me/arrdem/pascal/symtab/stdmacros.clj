(ns me.arrdem.pascal.symtab.stdmacros
  (:require [me.arrdem.compiler :refer [sizeof nameof]]
            [me.arrdem.compiler.symtab :refer [install! search]]
            [me.arrdem.compiler.macros :refer [->MacroType]]
            [me.arrdem.pascal.ast :refer [makefuncall]]
            [me.arrdem.pascal.semantics :refer [binop]]))

;;------------------------------------------------------------------------------

(defn p-new-macro
  "A macro function which serves to boostrap the equivalent of a malloc call.
Takes on argument: a type, and expands to a call to the trnew function which
actually allocates memory at runtime."
  [[t]]
  (let [T (search t)]
    (assert (not (nil? T)) (str "Failed to find type " t " in the symbol tbl"))
    (assert (not (string? T)) (str "got a string for " t " in the symbol tbl"))
    (list ':= t (makefuncall "trnew" (list (sizeof T))))))

;;------------------------------------------------------------------------------
(defn- progn? [form]
  (and (list? form)
       (= (first form) 'progn)))

(defn- _progn-inliner
  [body]
  (reduce (fn [prev form]
            (concat prev
                    (if (progn? form)
                      (_progn-inliner (rest form))
                      (list form))))
          nil body))

(defn progn-inliner
  "A macro function which serves to try and inline out nested progn groups.
   Derived from
   https://github.com/valeryz/MacroPHP/blob/master/special-forms.lisp#L20"
  [body]
  (if (list? body)
    (cons 'progn (_progn-inliner body))
    body))

;;------------------------------------------------------------------------------

(defn- arith? [expr]
  (and (list? expr)
       (contains? #{'+ '- '* '/ '%} (first expr))))

(defn arith-cleaner [init_ittr init_val op forms]
  (if (list? forms)
    (->> (eval init_ittr)
         (reduce (fn [state x]
                   (cond
                    ;; strip nested additions
                    ;; note that the macro system will take care of the recursive
                    ;; case for me here, I just need to perform one inlining
                    ;; transform in order to have made progress
                    (and (arith? x)
                         (= op (first x)))
                        (update-in state [:exprs] concat (next x))

                    ;; strip additions of zero
                    (= 0 x)
                        state

                    ;; maintain a partial sum
                    (number? x)
                        (update-in state [:partial] (eval op) x)

                   true
                        (update-in state [:exprs] concat (list x))))
                 {:partial (eval init_val) :exprs '()})
         ((juxt :partial :exprs))
         (apply cons)
         (cons op)
         ((fn [x] (if (= 2 (count x)) (second x) x))))
    forms))

(def addition-cleaner
  (partial arith-cleaner identity 0 '+))

(def multiplication-cleaner
  (partial arith-cleaner identity 1 '*))

(def subtraction-cleaner
  (partial arith-cleaner '(next forms) '(first forms) '-))

(def division-cleaner
  (partial arith-cleaner '(next forms) '(first forms) '/))

;;------------------------------------------------------------------------------
(defn aref-cleaner
  "A macro function which seeks first to remove (aref <> 0) groups, and second
   to optimize nested aref statements out to a single aref by taking the sum of
   their index values and eliminating the inner aref."
  ([[expr offset]]
     (cond
      (= 0 offset)
        expr ;; zero offset case
      (= 'aref (first expr))
        (list 'aref
              (second expr)
              (binop offset '+ (nth expr 2)))
      true
        (list 'aref expr offset))))

;;------------------------------------------------------------------------------
(defn init!
  "Function of no arguments, its sole purpose is to side-effect the symbol
table and install the standard macros used for pre-code generation type
ensuring and soforth."
  []
  (println "; installing standard macros...")
  (doseq [m [["new" p-new-macro]
             ["progn" progn-inliner]
             ["aref" aref-cleaner]
             ["+" addition-cleaner]
             ["*" multiplication-cleaner]
             ["-" subtraction-cleaner]
             ["/" division-cleaner]
             ]]
    (install! (apply ->MacroType m)))
  (println "; standard macros installed!"))
