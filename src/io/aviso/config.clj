(ns io.aviso.config
  "A system for reading and coercing configuration data.

  Configuration data is in the form of a *set* of files (mostly on the classpath) that follow a naming convention:

      <prefix>-<profile>-configuration.<extension>

  The <prefix> is specific to the application; the profile is provided by the application.

  Currently, the extensions \"yaml\" and \"edn\" are supported.

  The configuration data is read from an appropriate set of such files, and merged together.
  The configuration is then passed through a Schema for validation and coercion.

  Validation helps ensure that simple typos are caught early.
  Coercion helps ensure that the data is both valid and in a format ready to be consumed."
  (:require [schema.coerce :as coerce]
            [schema.utils :as su]
            [clj-yaml.core :as yaml]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [io.aviso.tracker :as t]
            [io.aviso.toolchest.macros :refer [cond-let]]
            [clojure.java.io :as io]))

(defn- resources
  "For a given resource name on the classpath, provides URLs for all the resources that match, in
  no specific order."
  [name]
  (-> (Thread/currentThread) .getContextClassLoader (.getResources name) enumeration-seq))

(defn- expand-env-vars
  [source env-map]
  (str/replace source
               #"\$\{((?!\$\{).*)\}"
               (fn [[expansion env-var]]
                 (or (get env-map env-var)
                     (throw (ex-info (format "Unable to find expansion for `%s'." expansion)
                                     {:env-var env-var
                                      :source  source}))))))

(defn- read-single
  "Reads a single configuration file from a URL, expanding environment variables, and
  then parsing the resulting string."
  [url parser env-map]
  (when url
    (t/track
      #(format "Reading configuration from `%s'." url)
      (-> (slurp url)
          (expand-env-vars env-map)
          parser))))

(defn- read-each
  "Read all resources matching a given path into a vector of parsed
  configuration data, ready to merge"
  [path parser env-map]
  (let [urls (resources path)]
    (keep #(read-single % parser env-map) urls)))

(defn- deep-merge
  "Merges maps, recursively. Collections accumulate, otherwise later values override
  earlier values."
  [existing new]
  (cond
    (map? existing) (merge-with deep-merge existing new)
    (coll? existing) (concat existing new)
    :else new))

(def default-extensions
  "The default mapping from file extension to a parser for content from such a file.

  Provides parsers for the \"yaml\" and \"edn\" extensions."
  {"yaml" #(yaml/parse-string % true)
   "edn"  edn/read-string})

(defn default-resource-path
  "Default mapping of a resource path from prefix, profile, and extension.

  prefix - string
  : prefix applied to all resource paths

  profile - keyword
  : profile to add to path, or nil

  extension - string
  : extension (e.g., \"yaml\").

  The result is a either \"prefix-profile-configuration.ext\" or \"prefix-configuration.ext\" when profile
  is nil."
  [prefix profile extension]
  (str prefix "-"
       (if profile
         (str (name profile) "-"))
       "configuration."
       extension))

(defn- get-parser [^String path extensions]
  (let [dotx      (.lastIndexOf path ".")
        extension (subs path (inc dotx))]
    (or (get extensions extension)
        (throw (ex-info "Unknown extension for configuration file."
                        {:path       path
                         :extensions extensions})))))

(defn merge-value
  "Merges a command-line argument into a map. The command line argument is of the form:

       path=value

   where path is the path to value; it is split at slashes and the key converted to keywords.

   e.g.

       web-server/port=8080

   is equivalent to

       (assoc-in m [:web-server :port] \"8080\")

   "
  {:since "0.1.1"}
  [m arg]
  (cond-let
    [[_ path value] (re-matches #"([^=]+)=(.*)" arg)]

    (not path)
    (throw (IllegalArgumentException. (format "Unable to parse argument `%s'." arg)))

    [keys (map keyword (str/split path #"/"))]

    :else
    (assoc-in m keys value)))

(defn- parse-args
  [args]
  (loop [remaining-args   args
         additional-files []
         overrides        {}]
    (cond-let
      (empty? remaining-args)
      [additional-files overrides]

      [arg (first remaining-args)]

      (= "--load" arg)
      (let [[_ file-name & more-args] remaining-args]
        (recur more-args (conj additional-files file-name) overrides))

      :else
      (recur (rest remaining-args)
             additional-files
             (merge-value overrides arg)))))

(defn assemble-configuration
  "Reads the configuration, as specified by the options.

  Inside each configuration file, the content is scanned for environment variables; these are substituted
  from environment. Such substitution occurs before any parsing takes place.

  Environment variables are of the form `${ENV_VAR}` and are simply replaced (with no special quoting) in
  the string.

  The :args option is passed command line arguments (as from a -main function). The arguments
  are used to add further additional files to load, and provide additional overrides.

  Arguments are either \"--load\" followed by a path name, or \"path=value\".

  In the second case, the path and value are as defined by [[merge-value]].

  :prefix (required)
  : The prefix to place at the start of each configuration file read.

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

  :resource-path
  : A function that builds resource paths from prefix, profile, and extension.

  :extensions
  : Maps from extension (e.g., \"yaml\") to an appropriate parsing function.

  The profile :default is always loaded first.
  The profile :local is second to last (it usually contains local user overrides for testing).
  The nil profile is always loaded last.
  Any additional files will then be loaded.

  The contents of each file are deep-merged together; later files override earlier files."
  [{:keys [prefix schemas overrides profiles resource-path extensions additional-files args]
    :or   {extensions    default-extensions
           resource-path default-resource-path}}]
  (assert prefix ":prefix option not specified")
  (let [env-map       (into {} (System/getenv))
        [arg-files arg-overrides] (parse-args args)
        raw           (for [profile (concat [:default] profiles [:local nil])
                            [extension parser] extensions
                            :let [path (resource-path prefix profile extension)]]
                        (read-each path parser env-map))
        flattened     (apply concat raw)
        extras        (for [path (concat additional-files arg-files)
                            :let [parser (get-parser path extensions)]]
                        (read-single (io/file path) parser env-map))
        conj'         (fn [x coll] (conj coll x))
        merged        (->> (concat flattened extras)
                           vec
                           (conj' overrides)
                           (conj' arg-overrides)
                           (apply merge-with deep-merge))
        merged-schema (apply merge-with deep-merge schemas)
        coercer       (coerce/coercer merged-schema coerce/string-coercion-matcher)
        config        (coercer merged)]
    (if (su/error? config)
      (throw (ex-info (str "The configuration is not valid: " (-> config su/error-val pr-str))
                      {:schema  merged-schema
                       :config  merged
                       :failure (su/error-val config)}))
      config)))

(defn with-config-schema
  "Adds metadata to the component to define the configuration schema for the component."
  [component schema]
  (vary-meta component assoc ::schema schema))

(defn extract-schemas
  "For a seq of components, extracts the schemas associated via [[with-config-schema]], returning
  a seq of schemas."
  [components]
  (keep (comp ::schema meta) components))
