(ns config-specs
  (:use io.aviso.config
        speclj.core)
  (:require [schema.core :as s]
            [com.stuartsierra.component :as component]))

(s/defschema WebServerConfig {:web-server {:port s/Int
                                     :pool-size s/Int}})

(s/defschema DatabaseConfig {:database
                       {:hostname s/Str
                        :user     s/Str
                        :password s/Str}})

(s/defschema Env {:home s/Str})

(defrecord WebServer [configuration port pool-size]

  component/Lifecycle

  (start [this]
    (assoc this :port (get-in configuration [:web-server :port])))

  (stop [this] this))

(defn new-web-server
  []
  (-> (map->WebServer {})
      (component/using [:configuration])
      (with-config-schema WebServerConfig)))

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
                                    :schemas [WebServerConfig]})
           ;; note: coercion to s/Int occurred
           (should= {:web-server {:port      8080
                                  :pool-size 50}})))

  (it "can parse EDN"
      (->> (assemble-configuration {:prefix  "edn"
                                    :schemas [WebServerConfig]})
           (should= {:web-server {:port      8080
                                  :pool-size 25}})))

  (it "overrides default profile with other profiles"
      (->> (assemble-configuration {:prefix   "order1"
                                    :schemas  [WebServerConfig]
                                    :profiles [:web]})
           (should= {:web-server {:port      9090
                                  :pool-size 40}})))

  (it "overrides default profile with nil profile"
      (->> (assemble-configuration {:prefix   "order2"
                                    :schemas  [WebServerConfig]
                                    :profiles [:testing]})
           (should= {:web-server {:port      9090
                                  :pool-size 40}})))

  (it "mixes together multiple profiles and schemas"
      (->> (assemble-configuration {:prefix           "mix"
                                    :schemas          [WebServerConfig DatabaseConfig]
                                    :additional-files ["dev-resources/mix-production-overrides.yaml"]
                                    :overrides        {:web-server {:port 9999}}})
           (should= {:web-server {:port      9999
                                  :pool-size 100}
                     :database   {:hostname "localhost"
                                  :user     "prod"
                                  :password "secret"}})))

  (it "processes arguments"
      (->> (assemble-configuration {:prefix  "mix"
                                    :schemas [WebServerConfig DatabaseConfig]
                                    :args    ["--load" "dev-resources/mix-production-overrides.yaml"
                                              "web-server/port=9999"
                                              "database/hostname=db"]})
           (should= {:web-server {:port      9999
                                  :pool-size 100}
                     :database   {:hostname "db"
                                  :user     "prod"
                                  :password "secret"}})))

  (context "expansions"
    (it "expands environment variables"

        (->> (assemble-configuration {:prefix  "env"
                                      :schemas [Env]})
             (should= {:home @home})))

    (it "expands environment variables on edn files"
        (->> (assemble-configuration {:prefix  "envedn"
                                      :schemas [Env]})
             (should= {:home @home})))

    (it "can use a default value on an environment variable"
        (->> (assemble-configuration {:prefix  "envdef"
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

    (it "can expand JVM system properties"
        (->> (assemble-configuration {:prefix  "sysprops"
                                      :schemas [{s/Any s/Any}]})
             (should= {:user-home (System/getProperty "user.home")}))))

  (it "can associate and extract schemas"
      (->> [(with-config-schema {} WebServerConfig)
            {}
            (with-config-schema {} DatabaseConfig)]
           extract-schemas
           (should= [WebServerConfig DatabaseConfig])))

  (it "can build a system"
      (should= 9999
               (-> (component/system-map :web-server (new-web-server))
                   (extend-system-map {:prefix   "system"
                                       :profiles [:web-server]})
                   component/start-system
                   :web-server
                   :port))))

(run-specs)