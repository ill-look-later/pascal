(ns me.arrdem.compiler.macros
  (:require [me.arrdem.compiler.symbols :refer [typeof]]
            [me.arrdem.compiler.symtab :refer [search]]))

(defrecord MacroType [name expander]
  me.arrdem.compiler.symbols.ISymbol
    (typeof [self] "macro")
    (nameof [self] (.name self))
    (sizeof [self] nil)
    (addrof [self] nil))

(defn macro? [obj]
  (try
    (= "macro" (typeof obj))
    (catch Exception e false)))

(defn pmacroexpand
  "An \"outermost first\" macro implementation. Looks up macros from the symbol
table, and applies them if possible. Note that in the two arguments case, the
second argument is the key used for pulling transformation functions out of
symbol table entries. This exists so that the macro system can be employed first
 at AST generation time to do type conversion and soforth, and later at code
generation time so that I'm not writing two macro systems when one will do."
  [expr]
  (if (and (seq? expr)
           (not (map? expr)))
    (let [expander (pmacroexpand (first expr))
          expander (cond
                    (string? expander) (search expander)
                    true               nil)
          expander (when (macro? expander)
                     (.expander expander))
          res (if (fn? expander)
                (expander (rest expr))
                expr)
          res (cons (first res)
                    (map pmacroexpand
                         (next res)))]
      (if (fn? expander)
        (pmacroexpand res)
        res))
    expr))
