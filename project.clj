(defproject io.aviso/config "0.1.6"
            :description "Configure a system using YAML or EDN files"
            :url "https://github.com/AvisoNovate/config"
            :license {:name "Apache Sofware License 2.0"
                      :url  "http://www.apache.org/licenses/LICENSE-2.0.html"}
            :profiles {:dev
                       {:dependencies [[io.aviso/pretty "0.1.18"]
                                       [speclj "3.2.0"]
                                       [ch.qos.logback/logback-classic "1.1.3"]
                                       [criterium "0.4.3"]]}}
            ;; List "resolved" dependencies first, which occur when there are conflicts.
            ;; We pin down the version we want, then exclude anyone who disagrees.
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [io.aviso/tracker "0.1.6"]
                           [prismatic/schema "0.4.0"]
                           [clj-yaml "0.4.0"]]
            :plugins [[speclj "3.2.0"]
                      [lein-shell "0.4.0"]]
            :shell {:commands {"scp" {:dir "doc"}}}
            :aliases {"deploy-doc" ["shell"
                                    "scp" "-r" "." "hlship_howardlewisship@ssh.phx.nearlyfreespeech.net:io.aviso/config"]
                      "release"    ["do"
                                    "clean,"
                                    "spec,",
                                    "doc,"
                                    "deploy-doc,"
                                    "deploy" "clojars"]}
            :test-paths ["spec"]
            :codox {:src-dir-uri               "https://github.com/AvisoNovate/config/blob/master/"
                    :src-linenum-anchor-prefix "L"
                    :defaults                  {:doc/format :markdown}})
