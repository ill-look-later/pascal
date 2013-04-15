(ns me.arrdem.compiler.symbols)

;; Primitives
;;    Primitives are dirt easy. They just resolve to themselves but they're
;;    typed and sized differently.
;;
;; Pointers
;;   Pointers are easy
;;   :name <name> - note that this will be "^<pointed type>"
;;   :type :reference
;;   :reference <type of pointed value>
;;   :size 4
;;
;; Records
;;   :name <name>
;;   :type :record
;;   :type/data <sequence of the types>
;;   :children <map of names to pairs [offset, type]>
;;
;; Arrays
;;   :name <name>
;;   :type :record
;;   :type/data <string being the number of indices concatenated with the type
;;               of the value at each index>
;;   :children <map of index to pairs [offset, type]>
;;
;;   Dealing with multi-dimensional arrays, we define new subtypes for the
;;   nested arrays, but note that we do not have to define symbol table recors
;;   for such sub-arrays. For an array such as a[1..5, 1..10, 1..15]:integer
;;
;;   type tree:
;;       {:name "integer" :size 8}
;;           ^- {:name "integer-15" :size (* 15 8)}
;;                  ^- {:name "integer-15-10" :size (* 10 15 8)}
;;                         ^- {:name "integer-15-10-5" :size (* 5 10 15 8)}
;;
;;   and the record:
;;       {:name "a" :type :record :type/data "integer-15-10-5" ...}

(defprotocol ISymbol
  (typeof [_] "Returns the type of the symbol")
  (nameof [_] "Returns the qualified name of the symbol")
  (sizeof [_] "Returns the size in bytes of the symbol")
  (addrof [_] "Returns an expression for the address of the symbol"))

(defprotocol IIndexable
  (field-offset [_ field] "Returns an expression for the address of the field")
  (fields [_] "Enumerates all fields as full ISymbols"))

(defprotocol IPointer
  (reftype [_] "Enumerates the type of the value to which it points")
  (follow [_] "Enumerates the targeted value as a full ISymbol"))

;;------------------------------------------------------------------------------
;; record types for type records
(defrecord PrimitiveType [name size-field]
  ISymbol
    (typeof [self] (.name self))
    (nameof [self] (.nams self))
    (sizeof [self] (.size-field self))
    (addrof [self] nil))

(defrecord PointerType [name size-field reftype]
  ISymbol
    (typeof [self] (.name self))
    (nameof [self] (.nams self))
    (sizeof [self] (.size-field self))
    (addrof [self] nil)
  IPointer
    (reftype [self] (.reftype self))
    (follow [_] nil))

(defrecord ArrayType [name size-field children]
  ISymbol
    (typeof [self] (.name self))
    (nameof [self] (.nams self))
    (sizeof [self] (.size-field self))
    (addrof [self] nil)
  IIndexable
    (field-offset [self name]
      (get (.children self) name))
    (fields [self] (.children self)))