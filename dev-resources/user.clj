(ns user
  (:use clojure.repl)
  (:require [speclj.config :as config]))

(alter-var-root #'config/default-config assoc :color true :reporters ["documentation"])

