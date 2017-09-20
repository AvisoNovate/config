## Unreleased

* Change dependency on Clojure to 1.9.0-beta1 and support clojure.spec.alpha ns.

## 0.2.4 - 14 Apr 2017

Ok, back to semantic numbering.

This downgrades config to Clojure 1.8, and uses clojure-future-spec to
fill the gaps.

## v20161206

Added `configure-using` to assemble a configuration, including default profiles,
and then apply it to a system.

## 0.2.2 - 2 Dec 2016

Remove the :args option, and add basic clojure.spec support for the options passed to 
`assemble-configuration`.

## 0.2.1 - 26 Aug 2016

Added #config/long and #config/keyword reader macros.

## 0.2.0 - 16 Jul 2016

A major pivot, with **incompatible changes**:

* Use Clojure 1.9 spec instead of Prismatic Schema
* Remove support for YAML
* No more "expansions", just the #config/prop and #config/join reader macros
* No longer place a :configuration component into the system map

## 0.1.13 - 15 Apr 2016

Fix a mis-named key in the ResourcePathSelector schema (was :variable, but should be
:variant).

## 0.1.12 - 25 Mar 2016

The signature of parser callbacks has changed with this release.

Added support for #config/prop and #config/join readers, when parsing EDN content.

## 0.1.11 - 11 Mar 2016

Some simplifications (that are also **incompatible changes**):

* The :prefix option has been removed.

* Configuration files are now stored on the classpath as `conf/<profile>-<variant>.<ext>`.

* The `nil` profile is no longer added automatically; you may need to get similar behavior
  by adding a :default variant.

## 0.1.10 - 4 Mar 2016

The `default-resource-path` function (or its :resource-path override in the options)
must now return a seq of paths.

The default list of variants is now just `[:local]`, and the variants are always
prefixed with a nil variant, to load defaults for the profile.

Added Schema types for the options argument to `assemble-configuration`.

## 0.1.9 - 22 Nov 2015

New :properties option, which specifies properties that can be substituted using
the same syntax as environment variable substitution.

JVM system properties may now be expanded in source files, using the same
syntax as environment variables.

The :prefix option is now allowed to be nil.

Config now includes component as a dependency, and includes a new function,
`extend-system-map` as an easy integration point. 

A new option for components to receive *just* their individual configuration
has been added: the Configurable protocol and the `configure-components` function.

Incompatible changes:

* The `default-resource-path` function (the default for
  the :resource-path option) has changed to accept a single map rather
  than four individual parameters.
* The :default profile is no longer added to the front of the list of profiles,
  as was done previously.
* For each profile, a set of variants (based on the :variant option) is now loaded.

The above changes are meant to break apart large configuration files into
a set of per-profile configuration files. Projects might previously have had
a single `myapp-default-configuration.yaml` file with configuration data
for a number of individual components; this tended to be brittle when only
a subset of the components might actually be enabled (the excess data
for non-enabled components could result in a schema validation exception).

Instead, each component should provide a schema and default
configuration file, e.g., `myapp-webserver-configuration.yaml`.

## 0.1.8 - 29 Sep 2015

Update a bunch of dependencies to latest versions.

## 0.1.7 - Some time in between

Update a bunch of dependencies to latest versions.

## 0.1.5, 0.1.6 - 15 Jul 2015

Reconcile Clojars and repository version numbers.

## 0.1.4 - 24 Jun 2015

Fixed a bug where a default value for an environment variable would always be selected,
even when the environment variable was defined.

## 0.1.3 - 2 Jun 2015

No changes (necessary due to administrative snafu).

## 0.1.2 - 2 Jun 2015

Environment variable references may now have a default value, e.g., `${HOST:localhost}`.

## 0.1.1 - 6 May 2015

Allow additional configuration files and overrides to be provided on the command line. 

## 0.1.0 - 6 May 2015

Initial release.
