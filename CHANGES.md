## 0.1.9 - UNRELEASED

New :properties option, which specifies properties that can be substituted using
the same syntax as environment variable substitution.

JVM system properties may now be expanded in source files, using the same
syntax as environment variables.

Incompatible changes:

* The `default-resource-path` function (the default for
  the :resource-path option) has changed to accept an additional
  parameter, variant.
* The :default profile is no longer added to the front of the list of profiles,
  as was done previously.
* For each profile, a set of variants (based on the :variant option) is now loaded.

The above changes are meant to break apart large configuration files into
a set of per-profile configuration files. Projects might previously have had
a single `myapp-default-configuration.yaml` file with configuration data
for a number of individual components; this tended to be brittle when only
a subset of the components might actually be enabled (the excess data
for non-enabled components could result in a schema validation exception).

Instead, each component should provide a schema and a :default variant
configuration file, e.g., `myapp-webserver-default-configuration.yaml`.

Config now includes component as a dependency, and includes a new function,
`extend-system-map` as an easy integration point.

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
