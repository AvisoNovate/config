(ns io.aviso.config.spec
  "Utilities related to clojure.spec.

  This namespace defines two resuable specs:

  ::unqualified-keyword
  : A keyword that has no namespace.

  ::qualified-keyword
  : A keyword that must have a namespace."
  {:added "0.2.0"}
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(s/def ::unqualified-keyword
  (s/with-gen
    (s/and keyword?
           #(-> % namespace nil?))
    gen/keyword))

(s/def ::qualified-keyword
  (s/with-gen
    (s/and keyword?
           #(-> % namespace some?))
    gen/keyword-ns))

(defmacro dictionary
  "A dictionary is a spec that maps arbitrary keywords to a particular spec.

  By default, keys in the dictionary map conform to ::unqualified-keyword."
  ([value-spec]
   `(dictionary ::unqualified-keyword ~value-spec))
  ([key-spec value-spec]
   `(s/every-kv ~key-spec ~value-spec)))
