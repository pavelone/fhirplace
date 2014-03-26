(ns fhirplace.repositories.resource
  (:require [clojure.java.jdbc :as sql])
  (:refer-clojure :exclude (delete)))

(def project
  "FHIRPlace version"
  (->> "project.clj"
       slurp
       read-string
       (drop 2)
       (cons :version)
       (apply hash-map)))

(defn resource-types [db-spec]
  (set
    (map :path
         (sql/query db-spec ["SELECT DISTINCT(path[1]) FROM meta.resource_elements"]))))

(defn insert [db-spec resource]
  (:insert_resource
    (first
      (sql/query db-spec [(str "SELECT fhir.insert_resource('"
                               resource
                               "'::json)::varchar")]))))

(defn clears [db-spec]
  (sql/execute! db-spec ["DELETE FROM fhir.resource"]))

(defn select [db-spec resource-type id]
  (:json
    (first
      (sql/query db-spec [(str "SELECT json::text"
                               " FROM fhir.view_" (.toLowerCase resource-type)
                               " WHERE _id = '" id "'"
                               " LIMIT 1")]))))

(defn delete [db-spec resource-id]
  (sql/execute! db-spec [(str "DELETE FROM fhir.resource WHERE _id = '" resource-id "'")]))

(defn update [db-spec resource-id resource]
  (sql/query db-spec [(str "SELECT fhir.update_resource('"
                           resource-id
                           "','"
                           resource
                           "'::json)::varchar")]))