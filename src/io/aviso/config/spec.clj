(ns io.aviso.config.spec
  "Utilities related to clojure.spec."
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

(defn dictionary
  "A dictionary is a spec that maps arbitrary keywords to a particular spec."
  ([value-spec]
   (dictionary ::unqualified-keyword value-spec))
  ([key-spec value-spec]
   (s/every-kv key-spec value-spec)))
