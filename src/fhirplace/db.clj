(ns fhirplace.db
  (:require [clojure.java.jdbc :as cjj]
            [honeysql.core :as hc]
            [clojure.data.json :as json]
            [clojure.string :as cs]
            [fhir :as f]
            [honeysql.helpers :as hh]
            [environ.core :as env]))

(import ' org.postgresql.util.PGobject)

(def ^:dynamic *db*
  {:subprotocol (env/env :fhirplace-subprotocol)
   :subname (env/env :fhirplace-subname)
   :user (env/env :fhirplace-user)
   :stringtype "unspecified"
   :password (env/env :fhirplace-password)})


(defn cfg [x]
  (merge x
         {:identifier "http://fhirplace.org"
          :version :todo
          :description "FHIR open source server"
          :name "fhirplace"
          :publisher "fhirplace"
          :date "2014-08-30"
          :software {:name "fhirplace"
                     :version "0.0.1" }
          :telecom [{:system "url" :value "http://try-fhirplace.hospital-systems.com/fhirface/index.html#/" }]
          :acceptUnknown false
          :fhirVersion "integration build"
          :format ["json" "xml"]
          :cors true
          }))

(defn cfg-str [x]
  (json/write-str (cfg x)))

(defmacro with-db  [db & body]
  `(binding  [*db* ~db]
     ~@body))

(defmacro transaction  [& body]
  `(cjj/with-db-transaction  [t-db# *db*]
     (with-db t-db# ~@body)))

(defmacro rollback-transaction  [& body]
  `(cjj/with-db-transaction  [t-db# *db*]
     (cjj/db-set-rollback-only! t-db#)
     (with-db t-db# ~@body)))


(defn uuid  [] (java.util.UUID/randomUUID))

(defn- tbl-name [tp]
  (keyword (.toLowerCase (name tp))))

(defn- htbl-name [tp]
  (keyword (str (.toLowerCase (name tp)) "_history")))

(extend-protocol cjj/IResultSetReadColumn
  PGobject
  (result-set-read-column  [pgobj metadata idx]
    (let  [type  (.getType pgobj)
           value  (.getValue pgobj)]
      (case type
        "json" value
        "jsonb" value
        :else value))))

(defn q* [sql]
  (println "SQL:" (pr-str sql))
  (cjj/query *db* sql))

(defn call* [proc & args]
  (let [proc-name (name proc)
        params (cs/join "," (map (constantly "?") args))
        sql (str "SELECT " proc-name "(" params ")")]
    (get (first (q* (into [sql] args))) proc)))

(defn qcall* [proc & args]
  (let [proc-name (name proc)
        params (cs/join "," (map (constantly "?") args))
        sql (str "SELECT * FROM " proc-name "(" params ")")]
    (q* (into [sql] args))))

(defn q [hsql]
  (let [sql (hc/format hsql)]
    (println "SQL:" sql)
    (cjj/query *db* sql)))

(defn q-one [hsql]
  (first (q hsql)))

(defn e [sql]
  (let [sql sql]
    (println "SQL:" sql)
    (cjj/execute! *db* sql)))

(defn i [tbl attrs]
  (first
    (cjj/insert! *db* tbl attrs)))

(import 'java.sql.Timestamp)

(defn -create [cfg tp json tags]
  (call* :fhir_create (cfg-str cfg) tp json tags))

(defn -update [cfg tp id vid json tags]
  (call* :fhir_update (cfg-str cfg) tp id vid json tags))

(defn -delete [cfg tp id]
  (call* :fhir_delete (cfg-str cfg) tp id))

(defn -deleted? [cfg tp id]
  (and
    (not (q-one {:select [:logical_id]
                 :from [(tbl-name tp)]
                 :where [:= :logical_id id]}))
    (q-one {:select [:logical_id]
            :from [(htbl-name tp)]
            :where [:= :logical_id id]})))

(defn -latest? [cfg tp id vid]
  {:pre [(not (nil? tp))]}
  (println "-latest?" tp " " id " " vid)
  (q-one {:select [:*]
          :from [(tbl-name tp)]
          :where [:and
                  [:= :logical_id id]
                  [:= :version_id vid] ]
          :limit 1}))

(defn -read [cfg tp id]
  (call* :fhir_read (cfg-str cfg) tp id))

(defn -vread [cfg tp vid]
  (call* :fhir_vread (cfg-str cfg) tp vid))

(defn -resource-exists? [cfg tp id]
  (->
    (q {:select [:logical_id]
        :from   [(tbl-name tp)]
        :where  [:= :logical_id (java.util.UUID/fromString id)]
        :limit 1})
    first
    nil?
    not))

(defn -conformance [cfg]
  (f/parse
    (call* :fhir_conformance (cfg-str cfg))))

(defn -profile [cfg tp]
  (f/parse
    (call* :fhir_profile (cfg-str cfg) tp)))

(defn -search [cfg tp q]
  (f/parse
    (call* :fhir_search (cfg-str cfg) tp q)))

(defn -transaction [cfg bundle]
  (f/parse
    (call* :fhir_transaction (cfg-str cfg) bundle)))

(defn -history
  ([cfg]
   (f/parse
     (call* :fhir_history (cfg-str cfg) "{}")))
  ([cfg tp]
   (f/parse
     (call* :fhir_history (cfg-str cfg) tp "{}")))
  ([cfg tp id]
   (f/parse
     (call* :fhir_history (cfg-str cfg) tp id "{}"))))

;; TODO: bug report
(defn -tags
  ([cfg ] (call* :fhir_tags (cfg-str cfg)))
  ([cfg tp] (call* :fhir_tags (cfg-str cfg) tp))
  ([cfg tp id] (call* :fhir_tags (cfg-str cfg) tp id))
  ([cfg tp id vid] (call* :fhir_tags (cfg-str cfg) tp id vid)))

(defn -affix-tags
  ([cfg tp id tags] (call* :fhir_affix_tags (cfg-str cfg) tp id (json/write-str tags)   ))
  ([cfg tp id vid tags] (call* :fhir_affix_tags (cfg-str cfg) tp id vid (json/write-str tags))))

(defn -remove-tags
  ([cfg tp id] (call* :fhir_remove_tags (cfg-str cfg) tp id))
  ([cfg tp id vid] (call* :fhir_remove_tags (cfg-str cfg) tp id vid)))
