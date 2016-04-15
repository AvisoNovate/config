(ns user
  (:use clojure.repl)
  (:require [schema.core :as s]
            [speclj.config :as config]))

(alter-var-root #'config/default-config assoc :color true :reporters ["documentation"])

(s/set-compile-fn-validation! true)
(s/set-fn-validation! true)
