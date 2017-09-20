(defproject sharetribe/config "0.2.4-st1-SNAPSHOT"
  :description "Configure a Clojure system with EDN files"
  :url "https://github.com/sharetribe/config"
  :license {:name "Apache Sofware License 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :profiles {:dev
             {:dependencies [[speclj "3.3.2"]
                             [org.clojure/test.check "0.9.0"]
                             [io.aviso/logging "0.2.0"]
                             [criterium "0.4.4"]]}}
  :dependencies [[org.clojure/clojure "1.9.0-beta1"]
                 [com.stuartsierra/component "0.3.2"]]
  :plugins [[speclj "3.3.2"]
            [lein-codox "0.10.2"]]
  :aliases {"release"    ["do"
                          "clean,"
                          "spec,",
                          "deploy" "clojars"]}
  :test-paths ["spec"]
  :codox {:source-uri "https://github.com/AvisoNovate/config/blob/master/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}})
