(defproject io.aviso/config "0.1.9"
  :description "Configure a system using YAML or EDN files"
  :url "https://github.com/AvisoNovate/config"
  :license {:name "Apache Sofware License 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :profiles {:dev
             {:dependencies [[speclj "3.3.1"]
                             [ch.qos.logback/logback-classic "1.1.3"]
                             [criterium "0.4.3"]]}}
  ;; List "resolved" dependencies first, which occur when there are conflicts.
  ;; We pin down the version we want, then exclude anyone who disagrees.
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [io.aviso/pretty "0.1.19"]
                 [io.aviso/tracker "0.1.7"]
                 [prismatic/schema "1.0.1"]
                 [com.stuartsierra/component "0.3.0"]
                 [medley "0.7.0"]
                 [clj-yaml "0.4.0"]]
  :plugins [[speclj "3.3.1"]
            [lein-shell "0.4.0"]
            [lein-codox "0.9.0"]]
  :shell {:commands {"scp" {:dir "target/doc"}}}
  :aliases {"deploy-doc" ["shell"
                          "scp" "-r" "." "hlship_howardlewisship@ssh.phx.nearlyfreespeech.net:io.aviso/config"]
            "release"    ["do"
                          "clean,"
                          "spec,",
                          "codox,"
                          "deploy-doc,"
                          "deploy" "clojars"]}
  :test-paths ["spec"]
  :codox {:source-uri "https://github.com/AvisoNovate/confiig/blob/master/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}})
