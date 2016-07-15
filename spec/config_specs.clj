(ns config-specs
  (:use io.aviso.config
        speclj.core)
  (:require [com.stuartsierra.component :as component]
            [clojure.spec :as s])
  (:import (clojure.lang ExceptionInfo)))


(s/def ::web-server-config (s/keys :req-un [::port ::pool-size]
                                   :opt-un [::paths]))
(s/def ::port (s/and int? pos?))
(s/def ::pool-size (s/and int? pos?))
(s/def ::paths (s/map-of simple-keyword? (s/cat :method #{:get :post} :path string?)))

(s/def ::database-config (s/keys :req-un [::hostname ::user ::password]))
(s/def ::hostname string?)
(s/def ::user string?)
(s/def ::password string?)

(defrecord Capturing [captured]

  Configurable

  (configure [this configuration]
    (assoc this :captured configuration)))

;; Note: doesn't implement Configurable, get a :configuration key instead.
(defrecord WebServer [configuration port pool-size]

  component/Lifecycle

  (start [this]
    (merge this (select-keys configuration [:port :pool-size :paths])))

  (stop [this] this))

(defn new-web-server
  []
  (-> (map->WebServer {})
      (with-config-spec :web-server ::web-server-config)))

(describe "io.aviso.config"

  (with-all home (System/getenv "HOME"))

  (it "can parse EDN"
      (->> (assemble-configuration {:profiles [:edn]})
           (should= {:web-server {:port      8080
                                  :pool-size 25}})))

  (it "overrides earlier variants with later variants"
      (->> (assemble-configuration {:variants [:order1 :local]
                                    :profiles [:web]})
           (should= {:web-server {:port      9090
                                  :pool-size 40}})))

  (it "mixes together multiple profiles and overrides"
      (->> (assemble-configuration {:profiles         [:mix]
                                    :additional-files ["dev-resources/mix-production-overrides.edn"]
                                    :overrides        {:web-server {:port 9999}}})
           (should= {:web-server {:port      9999
                                  :pool-size 100}
                     :database   {:hostname "prod-db"
                                  :user     "prod"
                                  :password "secret"}})))

  (context ":args options"
    (it "processes arguments"
        (->> (assemble-configuration {:profiles [:mix]
                                      :args     ["--load" "dev-resources/mix-production-overrides.edn"]})
             (should= {:web-server {:port      8000
                                    :pool-size 100}
                       :database   {:hostname "prod-db"
                                    :user     "prod"
                                    :password "secret"}})))

    (it "reports unexpected arguments"
        (let [e (should-throw ExceptionInfo
                              (assemble-configuration {:args ["--load" "override.edn" "xxx"]}))]
          (should= "Unexpected command line argument." (.getMessage e))
          (should= {:reason    :io.aviso.config/command-line-parse-error
                    :argument  "xxx"
                    :arguments ["--load" "override.edn" "xxx"]}
                   (ex-data e)))))

  (context "EDN reader macros"

    (context "#config/prop"

      (it "can expand a single string"
          (->> (assemble-configuration {:profiles   [:simple-reader]
                                        :properties {:single-string "katmandu"}})
               (should= {:just-string "katmandu"})))

      (it "can use a default value"
          (->> (assemble-configuration {:profiles [:envdef]})
               (should= {:use-default "default-plugh"
                         :use-env     @home})))

      (it "can expand explicitly provided properties"
          (->> (assemble-configuration {:profiles   [:vars]
                                        :properties {:special "an-option"}})
               (should= {:unmatched "ok"
                         :special   "an-option"
                         :home      @home})))

      (it "can expand JVM system properties"
          (->> (assemble-configuration {:profiles [:sysprops]})
               (should= {:user-home (System/getProperty "user.home")})))

      (it "can expand a string using a default"
          (->> (assemble-configuration {:profiles [:default-reader]})
               (should= {:default-value "totally handled"})))

      (it "will fail if unable to find property"
          (should-throw
            ExceptionInfo
            (assemble-configuration {:profiles [:simple-reader]}))))

    (context "#config/join"
      (it "can join things together"
          (->> (assemble-configuration {:profiles   [:join-reader]
                                        :properties {:magic "kazzam"}})
               (should= {:static-value "the number 2001"
                         :prop-value   "<kazzam>"})))))

  (context "configure-components"

    (with-all system (let [system        (component/system-map :web-server (new-web-server))
                           configuration (assemble-configuration {:variants [:paths :system :local]
                                                                  :profiles [:web-server]})]
                       (-> system
                           (configure-components configuration)
                           component/start-system)))


    (it "can build a system"
        (should= 9999
                 (-> @system :web-server :port)))

    (it "passes conformed configuration to component"
        ;; These start as vectors, but via s/cat, they become maps
        (should= {:home  {:method :get
                          :path   "/"}
                  :about {:method :get
                          :path   "/about"}}
                 (-> @system :web-server :paths)))

    (it "can associate configuration for a plain map component"
        (let [component-configuration {:foo :bar}
              component               (with-config-spec {:name :this-component} :this-component some?)
              system-map              (component/system-map :this-component component)
              configured-system       (configure-components system-map {:this-component component-configuration})]
          (should-be-same component-configuration (-> configured-system :this-component :configuration))))

    (it "will invoke Configurable/configure if implemented"
        (let [component-configuration {:foo :bar}
              component               (with-config-spec (map->Capturing {})
                                                        :that-component some?)
              system-map              (component/system-map :that-component component)
              configured-system       (configure-components system-map {:that-component component-configuration})]
          (should-be-same component-configuration (-> configured-system :that-component :captured))))

    (it "detects and reports configuration errors"
        (let [system        (component/system-map :web-server (new-web-server))
              configuration (assemble-configuration {:profiles  [:web-server]
                                                     :overrides {:web-server {:port "not-an-int"}}})
              e             (should-throw ExceptionInfo
                                          (-> system
                                              (configure-components configuration)
                                              component/start-system))]
          (should= :com.stuartsierra.component/component-function-threw-exception
                   (-> e ex-data :reason))
          (should= :io.aviso.config/invalid-component-configuration
                   (-> e .getCause ex-data :reason))))))

(run-specs)
