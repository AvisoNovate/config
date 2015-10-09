(ns config-specs
  (:use io.aviso.config
        speclj.core)
  (:require [schema.core :as s]))

(s/defschema WebServer {:web-server {:port      s/Int
                                     :pool-size s/Int}})

(s/defschema Database {:database
                       {:hostname s/Str
                        :user     s/Str
                        :password s/Str}})

(s/defschema Env {:home s/Str})

(describe "io.aviso.config"

  (with-all home (System/getenv "HOME"))

  (context "merge-value"

    (it "throws exception when the argument can't be parsed"
        (should-throw IllegalArgumentException "Unable to parse argument `foo/bar'."
                      (merge-value nil "foo/bar")))

    (it "builds a map from the path and value"
        (->> (merge-value nil "foo/bar=baz")
             (should= {:foo {:bar "baz"}})))

    (it "merges into an existing map"
        (->>
          (merge-value {:foo {:bar "baz"}} "foo/gnip=gnop")
          (should= {:foo {:bar  "baz"
                          :gnip "gnop"}}))))

  (it "can parse YAML"
      (->> (assemble-configuration {:prefix  "yaml"
                                    :schemas [WebServer]})
           ;; note: coercion to s/Int occurred
           (should= {:web-server {:port      8080
                                  :pool-size 50}})))

  (it "can parse EDN"
      (->> (assemble-configuration {:prefix  "edn"
                                    :schemas [WebServer]})
           (should= {:web-server {:port      8080
                                  :pool-size 25}})))

  (it "overrides default profile with other profiles"
      (->> (assemble-configuration {:prefix   "order1"
                                    :schemas  [WebServer]
                                    :profiles [:testing]})
           (should= {:web-server {:port      9090
                                  :pool-size 40}})))

  (it "overrides default profile with nil profile"
      (->> (assemble-configuration {:prefix   "order2"
                                    :schemas  [WebServer]
                                    :profiles [:testing]})
           (should= {:web-server {:port      9090
                                  :pool-size 40}})))

  (it "mixes together multiple profiles and schemas"
      (->> (assemble-configuration {:prefix           "mix"
                                    :schemas          [WebServer Database]
                                    :additional-files ["dev-resources/mix-production-overrides.yaml"]
                                    :overrides        {:web-server {:port 9999}}})
           (should= {:web-server {:port      9999
                                  :pool-size 100}
                     :database   {:hostname "localhost"
                                  :user     "prod"
                                  :password "secret"}})))

  (it "processes arguments"
      (->> (assemble-configuration {:prefix  "mix"
                                    :schemas [WebServer Database]
                                    :args    ["--load" "dev-resources/mix-production-overrides.yaml"
                                              "web-server/port=9999"
                                              "database/hostname=db"]})
           (should= {:web-server {:port      9999
                                  :pool-size 100}
                     :database   {:hostname "db"
                                  :user     "prod"
                                  :password "secret"}})))

  (it "expands environment variables"

      (->> (assemble-configuration {:prefix  "env"
                                    :schemas [Env]})
           (should= {:home @home})))

  (it "expands environment variables on edn files"
      (->> (assemble-configuration {:prefix "envedn"
                                    :schemas [Env]})
           (should= {:home @home})))

  (it "can use a default value on an environment variable"
      (->> (assemble-configuration {:prefix "envdef"
                                    :schemas [{s/Any s/Any}]})
           (should= {:use-default "default-plugh"
                     :use-env     @home})))

  (it "can expand non-environment variable properties"
      (->> (assemble-configuration {:prefix     "vars"
                                    :properties {:special "an-option"}
                                    :schemas    [{s/Any s/Any}]})
           (should= {:unmatched "ok"
                     :special   "an-option"
                     :home      @home})))

  (it "can associate and extract schemas"
      (->> [(with-config-schema {} WebServer)
            {}
            (with-config-schema {} Database)]
           extract-schemas
           (should= [WebServer Database]))))
