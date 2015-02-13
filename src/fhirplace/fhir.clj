(ns fhirplace.fhir
  (:require
    [fhir.core :as f]))

(def idx (f/index "profiles/profiles-resources.json"
                       "profiles/profiles-types.json"))

(defn operation-outcome [o]
  (f/resource idx (assoc o :resourceType "OperationOutcome")))

(defn parse [s]
  (f/parse idx s))

(defn validate [res]
  nil)

(defn serializable? [res]
  (map? res))

(defn generate [fmt res]
  (f/generate idx fmt res))
