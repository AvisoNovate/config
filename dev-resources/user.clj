(ns user
  (:use
    clojure.repl
    io.aviso.repl
    io.aviso.exception
    speclj.config)
  [:require [io.aviso.logging :as l]])

(install-pretty-exceptions)
(l/install-pretty-logging)
(l/install-uncaught-exception-handler)

(alter-var-root #'default-config assoc :color true :reporters ["documentation"])

(alter-var-root #'*default-frame-rules*
                conj [:name #"speclj\..*" :terminate])
