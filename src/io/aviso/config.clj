(ns io.aviso.config
  "A system for reading and coercing configuration data.

  Configuration data is in the form of a *set* of files (mostly on the classpath) that follow a naming convention:

      conf/<profile>-<variant>.edn

  The list of profiles and variants is provided by the application.

  The configuration data is read from an appropriate set of such files, and merged together.

  Each component's individual configuration is validated and conformed against a spec that is specific to the component."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [clojure.spec :as s])
  (:import (java.io PushbackReader)))

(defn ^:private join-reader
  "An EDN reader macro used to join together a vector of values.

  Exposed as `#config/join`."
  [values]
  {:pre [(vector? values)]}
  (apply str values))

(defn ^:private get-property
  [source properties property-key default-value]
  (or (get properties property-key)
      default-value
      (throw (ex-info (format "Unable to find value for property `%s'." property-key)
                      {:reason ::unknown-property
                       :property-key property-key
                       :property-keys (keys properties)
                       :source source}))))

(defn ^:private keyword-reader
  [v]
  {:pre [(string? v)]}
  (keyword v))

(defn ^:private long-reader
  [v]
  {:pre [(string? v)]}
  (Long/parseLong v))

(defn ^:private property-reader
  "An EDN reader macro used to convert either a string, or a vector of two strings, into a single
  string, using the properties assembled for the current invocation of
  [[assemble-configuration]].

  This is used as the EDN-compliant way to do substitutions, as per `${...}` syntax.

  A partial of this is exposed as `#config/prop`."
  [url env-map value]
  {:pre [(or (string? value)
             (and (vector? value)
                  (= 2 (count value))))]}
  (if (string? value)
    (get-property url env-map value nil)
    (let [[property-key default-value] value]
      (get-property url env-map property-key default-value))))

(defn ^:private resources
  "For a given resource name on the classpath, provides URLs for all the resources that match, in
  no specific order."
  [name]
  (-> (Thread/currentThread) .getContextClassLoader (.getResources name) enumeration-seq))

(defn ^:private read-edn-configuration-file
  "Reads a single configuration file from a URL with several reader macros enabled.

  Returns the configuration map read from the file."
  [url properties]
  (try
    (with-open [r (-> url
                      io/input-stream
                      io/reader
                      PushbackReader.)]
      (edn/read {:readers {'config/join join-reader
                           'config/keyword keyword-reader
                           'config/long long-reader
                           'config/prop (partial property-reader url properties)}}
                r))
    (catch Throwable t
      (throw (ex-info "Unable to read configuration file."
                      {:url url}
                      t)))))

(defn ^:private deep-merge
  "Merges maps, recursively. Collections accumulate, otherwise later values override
  earlier values."
  [existing new]
  (cond
    (map? existing) (merge-with deep-merge existing new)
    (coll? existing) (concat existing new)
    :else new))

(defn ^:private map-keys
  [f m]
  (reduce-kv (fn [m k v]
               (assoc m (f k) v))
             (empty m)
             m))

(defn default-resource-path
  "Default mapping of a resource path from profile and variant.

  profile - keyword
  : profile to add to path

  variant - keyword
  : variant to add to the path, or nil

  The result is typically \"conf/profile-variant.edn\".

  However, the \"-variant\" portion is omitted when variant is nil.

  Returns a seq of files.
  Although this implementation only returns a single value, supporting a seq of values
  makes it easier to extend (rather than replace) the default behavior with an override."
  [profile variant]
  [(str "conf/"
        (->> [profile variant]
             (remove nil?)
             (mapv name)
             (str/join "-"))
        ".edn")])

(def ^{:added "0.1.9"} default-variants
  "The default list of variants. The combination of profile and variant is the main way
  that resource file names are created (combined with a fixed prefix and a supported
  extension).

  The order of the variants determine load order, which is relevant.

  A nil variant is always prefixed to this list; this represents loading default
  configuration for the profile.

  Typically, a library creates a component or other entity that is represented within
  config as a profile.

  The local variant may be used for test-specific overrides, or overrides for a user's
  development (say, to redirect a database connection to a local database), or even
  used in production."
  [:local])

(defn ^:private parse-args
  [args]
  (loop [remaining-args   args
         additional-files []]
    (if (empty? remaining-args)
      additional-files
      (let [arg (first remaining-args)]
        (if (= "--load" arg)
          (let [[_ file-name & more-args] remaining-args]
            (recur more-args (conj additional-files file-name)))
          (throw (ex-info "Unexpected command line argument."
                          {:reason ::command-line-parse-error
                           :argument arg
                           :arguments args})))))))

(defn assemble-configuration
  "Reads the configuration, as specified by the options.

  The :args option is passed command line arguments (as from a -main function). The arguments
  are used to add further additional files to load, and provide additional overrides.

  When the EDN files are read, a set of reader macros are enabled to allow for some
  dynamicism in the parsed content: this represents overrides from either shell
  environment variables, JVM system properties, or the :properties option.

  Arguments are a sequence of \"--load\" followed by a path name.

  :additional-files
  : A seq of absolute file paths that, if they exist, will be loaded last, after all
    normal resources.
    This is typically used to provide an editable (outside the classpath) file for final
    production configuration overrides.

  :args
  : Command line arguments to parse; these yield yet more additional files to load.

  :overrides
  : A map of configuration data that is overlayed (using a deep merge)
    on the configuration data read from the files, before validation and coercion.

  :profiles
  : A seq of keywords that identify which profiles should be loaded and in what order.
    The default is an empty list.

  :properties
  : An optional map of additional properties that may be substituted (using
    the `#config/prop` reader macro). Explicit properties have higher precendence than JVM
    system properties, which have higher precendence than environment
    variables; however the convention is that environment variable names
    are all upper case, and properties are all lower case, so actual conflicts
    should not occur.
  : The keys of the properties map are converted to strings via `name`, so they
    may be strings or symbols, or more frequently, keywords.
  : Most often the properties map is used for specific overrides in testing, or
    to expose some bit of configuration that cannot be directly extracted
    from environment variables or JVM system properties.

  :variants
  : The variants searched for, for each profile.
  : [[default-variants]] provides the default list of variants.  A nil variant
    is always prefixed on the provided list.

  :resource-path
  : A function that builds resource paths from profile and variant.
  : The default is [[default-resource-path]], but this could be overridden
    to (for example), use a directory structure to organize configuration files
    rather than splitting up the different components of the name using dashes.

  Any additional files are loaded after all profile and variant files.

  Files specified via `--load` arguments are then loaded.

  The contents of each file are deep-merged together; later files override earlier files.

  Overrides via the :overrides key are applied last; these are typically used only for testing purposes."
  [options]
  (let [{:keys [overrides profiles variants
                resource-path additional-files
                args properties]
         :or {variants default-variants
              profiles []
              resource-path default-resource-path}} options
        full-properties (-> (sorted-map)
                            (into (System/getenv))
                            (into (System/getProperties))
                            (into (map-keys name properties)))
        arg-files       (parse-args args)
        variants'       (cons nil variants)
        raw             (for [profile profiles
                              variant variants'
                              path    (resource-path profile variant)
                              url     (resources path)
                              :when url]
                          (read-edn-configuration-file url full-properties))
        extras          (for [path (concat additional-files arg-files)]
                          (read-edn-configuration-file (io/file path) full-properties))
        conj'           (fn [x coll] (conj coll x))]
    (->> (concat raw extras)
         vec
         (conj' overrides)
         (apply merge-with deep-merge))))

(defprotocol Configurable
  "Optional (but preferred) protocol for components.

  When a component declares its configuration with [[with-config-spec]], but
  does *not* implement this protocol, it will instead have a :configuration
  key associated with it."

  (configure [this configuration]
    "Passes a component's individual configuration to the component,
    as defined by [[with-config-spec]].

    The component's configuration is extracted from the merged system
    configuration and conformed; the conformed configuration is
    passed to the component.

    When this is invoked (see [[configure-components]]),
    a component's dependencies *will* be available, but in an un-started
    state."))

(defn with-config-spec
  "Adds metadata to the component to define the configuration spec for the component.

  This defines a top-level configuration key (e.g., :web-service)
  and a spec for just that key.

  The component's specific configuration is extracted from the merged configuration,
  and the component's config spec is used to conform it.
  The conformed configuration is passed to the component.

  The component will receive *just* it's individual, conformed configuration in its
  :configuration key.

  Alternately, the component may implement the
  [[Configurable]] protocol. It will be passed the conformed configuration."
  [component config-key config-spec]
  (vary-meta component assoc
             ::config-key config-key
             ;; Used to conform the component's configuration
             ::config-spec config-spec))

(defn ^:private apply-configuration
  [component full-configuration]
  (let [{:keys [::config-key ::config-spec]} (meta component)]
    ;; Not all components have configuration
    (if (nil? config-key)
      component
      (let [component-configuration (get full-configuration config-key)
            conformed               (s/conform config-spec component-configuration)]
        (when (= ::s/invalid conformed)
          (throw (ex-info "Component configuration invalid."
                          (assoc (s/explain-data config-spec component-configuration)
                            :reason ::invalid-component-configuration))))
        ;; TODO: Pass the conformed configuration?
        (if (satisfies? Configurable component)
          (configure component conformed)
          (assoc component :configuration conformed))))))

(defn configure-components
  "Configures the components in the system map, returning an updated system map.

  Typically, this should be invoked *before* the system is started, as most
  components are expected to need configuration in order to start."
  {:added "0.1.9"}
  [system-map configuration]
  (component/update-system system-map
                           (keys system-map)
                           apply-configuration
                           configuration))
