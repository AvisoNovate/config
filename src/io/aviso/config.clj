(ns io.aviso.config
  "A system for reading and coercing configuration data.

  Configuration data is in the form of a *set* of files (mostly on the classpath) that follow a naming convention:

      conf/<profile>-<variant>.edn

  The list of profiles and variants is provided by the application.

  The configuration data is read from an appropriate set of such files, and merged together.
  The configuration is then passed through a Schema for validation and value coercion
  (for example, to convert strings into numeric types).

  Validation helps ensure that simple typos are caught early.
  Coercion helps ensure that the data is both valid and in a format ready to be consumed."
  (:require [schema.coerce :as coerce]
            [schema.utils :as su]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [medley.core :as medley]
            [com.stuartsierra.component :as component]
            [schema.core :as s])
  (:import (java.io PushbackReader)))

(defn- join-reader
  "An EDN reader used to join together a vector of values.

  Exposed as `#config/join`."
  [values]
  {:pre [(vector? values)]}
  (apply str values))

(defn- get-property
  [source properties property-key default-value]
  (or (get properties property-key)
      default-value
      (throw (ex-info (format "Unable to find value for property `%s'." property-key)
                      {:property-key  property-key
                       :property-keys (keys properties)
                       :source        source}))))

(defn- property-reader
  "An EDN reader used to convert either a string, or a vector of two strings, into a single
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


(defn- resources
  "For a given resource name on the classpath, provides URLs for all the resources that match, in
  no specific order."
  [name]
  (-> (Thread/currentThread) .getContextClassLoader (.getResources name) enumeration-seq))

(defn- read-edn-configuration-file
  "Reads a single configuration file from a URL with `#config/join` and `#config/prop`
  reader macros enabled.

  Returns the configuration map read from the file."
  [url properties]
  (try
    (with-open [r (-> url
                      io/input-stream
                      io/reader
                      PushbackReader.)]
      (edn/read {:readers {'config/join join-reader
                           'config/prop (partial property-reader url properties)}}
                 r))
    (catch Throwable t
      (throw (ex-info "Unable to read configuration file."
                      {:url url}
                      t)))))

(defn- deep-merge
  "Merges maps, recursively. Collections accumulate, otherwise later values override
  earlier values."
  [existing new]
  (cond
    (map? existing) (merge-with deep-merge existing new)
    (coll? existing) (concat existing new)
    :else new))

(s/defschema ^{:added "0.1.10"} ResourcePathSelector
  "Map of values passed to a [[ResourcePathGenerator]]."
  {:profile s/Keyword
   :variant (s/maybe s/Keyword)})

(s/defschema ^{:added "0.1.10"} ResourcePathGenerator
  "A callback that converts a configuration file [[ResourcePathSelector]]
  into some number of resource path strings.

  The standard implementation is [[default-resource-path]]."
  (s/=> [String] ResourcePathSelector))

(s/defn default-resource-path :- [String]
  "Default mapping of a resource path from profile, variant, and extension.
  A single map is passed, with the following keys:

  :profile - keyword
  : profile to add to path

  :variant - keyword
  : variant to add to the path, or nil

  The result is typically \"conf/profile-variant.edn\".

  However, \"-variant\" is omitted when variant is nil.

  Since 0.1.10, returns a seq of files.
  Although this implementation only returns a single value, supporting a seq of values
  makes it easier to extend (rather than replace) the default behavior with an override."
  [selector :- ResourcePathSelector]
  (let [{:keys [profile variant]} selector]
    [(str "conf/"
          (->> [profile variant]
               (remove nil?)
               (mapv name)
               (str/join "-"))
          ".edn")]))

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

(defn merge-value
  "Merges a command-line argument into a map. The command line argument is of the form:

       path=value

   where path is the path to value; it is split at slashes and the
   individual strings converted to keywords.

   e.g.

       web-server/port=8080

   is equivalent to

       (assoc-in m [:web-server :port] \"8080\")

   "
  {:since "0.1.1"}
  [m arg]
  (let [[_ path value] (re-matches #"([^=]+)=(.*)" arg)]
    (when-not path
      (throw (IllegalArgumentException. (format "Unable to parse argument `%s'." arg))))
    (let [keys (map keyword (str/split path #"/"))]
      (assoc-in m keys value))))

(defn- parse-args
  [args]
  (loop [remaining-args   args
         additional-files []
         overrides        {}]
    (if (empty? remaining-args)
      [additional-files overrides]
      (let [arg (first remaining-args)]
        (if (= "--load" arg)
          (let [[_ file-name & more-args] remaining-args]
            (recur more-args (conj additional-files file-name) overrides))
          (recur (rest remaining-args)
                 additional-files
                 (merge-value overrides arg)))))))

(s/defschema ^{:added "0.1.10"} AssembleOptions
  "Defines the options passed to [[assemble-configuration]]."
  {(s/optional-key :schemas)          [s/Any]
   (s/optional-key :additional-files) [s/Str]
   (s/optional-key :args)             [s/Str]
   (s/optional-key :overrides)        s/Any
   (s/optional-key :profiles)         [s/Keyword]
   (s/optional-key :properties)       {s/Any s/Str}
   (s/optional-key :variants)         [(s/maybe s/Keyword)]
   (s/optional-key :resource-path)    ResourcePathGenerator})

(s/defn assemble-configuration
  "Reads the configuration, as specified by the options.

  Expansions allow environment variables, JVM system properties, or explicitly specific properties

  The :args option is passed command line arguments (as from a -main function). The arguments
  are used to add further additional files to load, and provide additional overrides.

  When the EDN files are read, a set of reader macros are enabled to allow for some
  dynamicism in the parsed content: this represents overrides from either shell
  environment variables, JVM system properties, of the :properties option.

  Arguments are either \"--load\" followed by a path name, or \"path=value\".

  In the second case, the path and value are as defined by [[merge-value]].

  :schemas
  : A seq of schemas; these will be merged to form the full configuration schema.

  :additional-files
  : A seq of absolute file paths that, if they exist, will be loaded last, after all
    normal resources.
    This is typically used to provide an editable (outside the classpath) file for final
    production configuration overrides.

  :args
  : Command line arguments to parse; these yield yet more additional files and
    the last set of overrides.

  :overrides
  : A map of configuration data that is overlayed (using a deep merge)
    on the configuration data read from the files, before validation and coercion.

  :profiles
  : A seq of keywords that identify which profiles should be loaded and in what order.
    The default is an empty list.

  :properties
  : An optional map of properties that may be substituted, just as environment
    variable or System properties can be. Explicit properties have higher precendence than JVM
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

  Overrides via the :overrides key are applied, then overrides from command line arguments
  (provided in the :args option) are applied."
  [options :- AssembleOptions]
  (let [{:keys [schemas overrides profiles variants
                resource-path additional-files
                args properties]
         :or   {variants      default-variants
                profiles      []
                resource-path default-resource-path}} options
        full-properties (-> (sorted-map)
                            (into (System/getenv))
                            (into (System/getProperties))
                            (into (medley/map-keys name properties)))
        [arg-files arg-overrides] (parse-args args)
        variants'       (cons nil variants)
        raw             (for [profile profiles
                              variant variants'
                              path    (resource-path {:profile profile
                                                      :variant variant})
                              url     (resources path)
                              :when url]
                          (read-edn-configuration-file url full-properties))
        extras          (for [path (concat additional-files arg-files)]
                          (read-edn-configuration-file (io/file path) full-properties))
        conj'           (fn [x coll] (conj coll x))
        merged          (->> (concat raw extras)
                             vec
                             (conj' overrides)
                             (conj' arg-overrides)
                             (apply merge-with deep-merge))
        merged-schema   (apply merge-with deep-merge schemas)
        coercer         (coerce/coercer merged-schema coerce/string-coercion-matcher)
        config          (coercer merged)]
    (if (su/error? config)
      (throw (ex-info (str "The configuration is not valid: " (-> config su/error-val pr-str))
                      {:schema  merged-schema
                       :config  merged
                       :failure (su/error-val config)}))
      config)))

(defprotocol Configurable
  (configure [this configuration]
    "Passes a component's individual configuration to the component,
    as defined by the three-argument version of [[with-config-schema]].

    When this is invoked (see [[configure-components]]),
    a component's dependencies *will* be available, but in an un-started
    state."))

(defn with-config-schema
  "Adds metadata to the component to define the configuration schema for the component.

  This defines a top-level configuration key (e.g., :web-service)
  and a schema for just that key.

  The component will receive *just* that configuration in its
  :configuration key.

  Alternately, the component may implement the
  [[Configurable]] protocol. It will be passed just its own configuration."
  [component config-key schema]
  (vary-meta component assoc ::config-key config-key
             ;; This is what's merged to form the master schema
             ::schema {config-key schema}))

(defn extract-schemas
  "For a seq of components (the values of a system map),
   extracts the schemas associated via [[with-config-schema]], returning a seq of schemas."
  [components]
  (keep (comp ::schema meta) components))

(defn system-schemas
  "A convienience for extracting the schemas from a system."
  {:added "0.1.14"}
  [system]
  (-> system vals extract-schemas))

(defn- apply-configuration
  [component full-configuration]
  (let [config-key (-> component meta ::config-key)]
    (if (nil? config-key)
      component
      (let [component-configuration (get full-configuration config-key)]
        (if (satisfies? Configurable component)
          (configure component component-configuration)
          (assoc component :configuration component-configuration))))))

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
