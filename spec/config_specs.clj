(ns config-specs
  (:use io.aviso.config
        speclj.core)
  (:require [schema.core :as s]
            [com.stuartsierra.component :as component])
  (:import (clojure.lang ExceptionInfo)))

(s/defschema WebServerConfig {:web-server {:port      s/Int
                                           :pool-size s/Int}})

(s/defschema DatabaseConfig {:database
                             {:hostname s/Str
                              :user     s/Str
                              :password s/Str}})

(s/defschema Env {:home s/Str})

(defrecord Capturing [captured]

  Configurable

  (configure [this configuration]
    (assoc this :captured configuration)))

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

  (it "can parse EDN"
      (->> (assemble-configuration {:profiles [:edn]
                                    :schemas  [WebServerConfig]})
           (should= {:web-server {:port      8080
                                  :pool-size 25}})))

  (it "overrides earlier variants with later variants"
      (->> (assemble-configuration {:variants [:order1 :local]
                                    :schemas  [WebServerConfig]
                                    :profiles [:web]})
           (should= {:web-server {:port      9090
                                  :pool-size 40}})))

  (it "mixes together multiple profiles and schemas"
      (->> (assemble-configuration {:profiles         [:mix]
                                    :schemas          [WebServerConfig DatabaseConfig]
                                    :additional-files ["dev-resources/mix-production-overrides.edn"]
                                    :overrides        {:web-server {:port 9999}}})
           (should= {:web-server {:port      9999
                                  :pool-size 100}
                     :database   {:hostname "localhost"
                                  :user     "prod"
                                  :password "secret"}})))

  (it "processes arguments"
      (->> (assemble-configuration {:profiles [:mix]
                                    :schemas  [WebServerConfig DatabaseConfig]
                                    :args     ["--load" "dev-resources/mix-production-overrides.edn"
                                              "web-server/port=9999"
                                              "database/hostname=db"]})
           (should= {:web-server {:port      9999
                                  :pool-size 100}
                     :database   {:hostname "db"
                                  :user     "prod"
                                  :password "secret"}})))

  (context "expansions"
    (it "expands environment variables"

        (->> (assemble-configuration {:profiles [:env]
                                      :schemas  [Env]})
             (should= {:home @home})))

    (it "can use a default value on an environment variable"
        (->> (assemble-configuration {:profiles [:envdef]
                                      :schemas  [{s/Any s/Any}]})
             (should= {:use-default "default-plugh"
                       :use-env     @home})))

    (it "can expand non-environment variable properties"
        (->> (assemble-configuration {:profiles   [:vars]
                                      :properties {:special "an-option"}
                                      :schemas    [{s/Any s/Any}]})
             (should= {:unmatched "ok"
                       :special   "an-option"
                       :home      @home})))

    (it "can expand JVM system properties"
        (->> (assemble-configuration {:profiles [:sysprops]
                                      :schemas  [{s/Any s/Any}]})
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
                   (extend-system-map {:variants [:system :local]
                                       :profiles [:web-server]})
                   component/start-system
                   :web-server
                   :port)))

  (context "EDN readers"

    (context "#config/prop"

      (it "can expand a single string"
          (->> (assemble-configuration {:profiles   [:simple-reader]
                                        :properties {:single-string "katmandu"}
                                        :schemas    [s/Any]})
               (should= {:just-string "katmandu"})))

      (it "can expand a string using a default"
          (->> (assemble-configuration {:profiles [:default-reader]
                                        :schemas  [s/Any]})
               (should= {:default-value "totally handled"})))

      (it "will fail if unable to find property"
          (should-throw
            ExceptionInfo
            "Unable to find value for property `single-string'."
            (assemble-configuration {:profiles [:simple-reader]
                                     :schemas  [s/Any]}))))

    (context "#config/join"
      (it "can join things together"
          (->> (assemble-configuration {:profiles   [:join-reader]
                                        :properties {:magic "kazzam"}
                                        :schemas    [s/Any]})
               (should= {:static-value "the number 2001"
                         :prop-value   "<kazzam>"})))))

  (context "configure-components"
    (it "can associate configuration for a plain map component"
        (let [component-configuration {:foo :bar}
              component               (with-config-schema {:name :this-component} :this-component s/Any)
              system-map              (component/system-map :this-component component)
              configured-system       (configure-components system-map {:this-component component-configuration})]
          (should-be-same component-configuration (-> configured-system :this-component :configuration))))

    (it "can invoke Configurable/configure if implemented"
        (let [component-configuration {:foo :bar}
              component               (with-config-schema (map->Capturing {})
                                                          :that-component s/Any)
              system-map              (component/system-map :that-component component)
              configured-system       (configure-components system-map {:that-component component-configuration})]
          (should-be-same component-configuration (-> configured-system :that-component :captured))))))

(run-specs)
