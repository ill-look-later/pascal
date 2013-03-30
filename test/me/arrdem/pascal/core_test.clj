(ns me.arrdem.pascal.core-test
  (:require [clojure.test :refer :all]

            [me.arrdem.pascal.test-util :refer [symtab-wrapper]]
            [me.arrdem.pascal.test-text :as data]

            [me.arrdem.pascal :refer [process-string]]
            [me.arrdem.pascal.parser :refer [build-ast]]
            [me.arrdem.pascal.symtab :refer [search]]))

(defmacro full-test-case [sym val]
  `(deftest ~sym
     (testing
         (symtab-wrapper
          (let [result# (process-string (:text ~val))]
          ;; check AST result...
          (is (= result# (:ast ~val)))
          ;; check symbol table contents...
          (doseq [s# (:symbols ~val)]
            (is (search (:name s#))
                (str "symbol " (:qname s#) " was not defined!"))))))))

;;------------------------------------------------------------------------------
;; the big test cases over assignment inputs...

(full-test-case triv-pas-test data/triv-pas)    ;; parser assignment 1
(full-test-case trivb-pas-test data/trivb-pas)  ;; half of parser assignment 2

;;------------------------------------------------------------------------------
;; TODO: partial test cases over subsets of the grammar
