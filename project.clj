(defproject io.aviso/config "0.1.13"
  :description "Configure a Clojure system using YAML or EDN files"
  :url "https://github.com/AvisoNovate/config"
  :license {:name "Apache Sofware License 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :profiles {:dev
             {:dependencies [[speclj "3.3.1"]
                             [ch.qos.logback/logback-classic "1.1.6"]
                             [criterium "0.4.4"]]}}
  ;; List "resolved" dependencies first, which occur when there are conflicts.
  ;; We pin down the version we want, then exclude anyone who disagrees.
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [io.aviso/pretty "0.1.24"]
                 [io.aviso/tracker "0.1.7"]
                 [prismatic/schema "1.1.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [medley "0.7.3"]
                 [clj-yaml "0.4.0"]]
  :plugins [[speclj "3.3.1"]
            [lein-codox "0.9.3"]]
  :aliases {"release"    ["do"
                          "clean,"
                          "spec,",
                          "deploy" "clojars"]}
  :test-paths ["spec"]
  :codox {:source-uri "https://github.com/AvisoNovate/config/blob/master/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}})
