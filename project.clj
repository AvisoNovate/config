(defproject io.aviso/config "0.1.13"
  :description "Configure a Clojure system using YAML or EDN files"
  :url "https://github.com/AvisoNovate/config"
  :license {:name "Apache Sofware License 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :profiles {:dev
             {:dependencies [[speclj "3.3.2"]
                             [org.clojure/test.check "0.9.0"]
                             [ch.qos.logback/logback-classic "1.1.7"]
                             [criterium "0.4.4"]]}}
  ;; List "resolved" dependencies first, which occur when there are conflicts.
  ;; We pin down the version we want, then exclude anyone who disagrees.
  :dependencies [[org.clojure/clojure "1.9.0-alpha9"]
                 [prismatic/schema "1.1.2"]
                 [com.stuartsierra/component "0.3.1"]]
  :plugins [[speclj "3.3.2"]
            [lein-codox "0.9.3"]]
  :aliases {"release"    ["do"
                          "clean,"
                          "spec,",
                          "deploy" "clojars"]}
  :test-paths ["spec"]
  :codox {:source-uri "https://github.com/AvisoNovate/config/blob/master/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}})
