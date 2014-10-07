(ns fhirplace.db
  (:require [clojure.data.json :as json]
            [fhirplace.pg :refer :all]
            [fhir :as f]))

(defn cfg  [x]
  (merge x
         {:identifier "http://fhirplace.org"
          :version :todo
          :description "FHIR open source server"
          :name "fhirplace"
          :publisher "fhirplace"
          :date "2014-08-30"
          :software  {:name "fhirplace"
                      :version "0.0.1" }
          :telecom  [{:system "url" :value "http://try-fhirplace.hospital-systems.com/fhirface/index.html#/" }]
          :acceptUnknown false
          :fhirVersion "integration build"
          :format  ["json" "xml"]
          :cors true
          }))

(defn cfg-str  [x] (json/write-str  (cfg x)))

(defn -deleted? [cfg tp id]
  (call* :fhir_is_deleted_resource (cfg-str cfg) tp id))

(defn -latest? [cfg tp id vid]
  (call* :fhir_is_latest_resource (cfg-str cfg) tp id vid))

(defn -resource-exists? [cfg tp id]
  (call* :fhir_is_resource_exists (cfg-str cfg) tp id))

(defn -create [cfg tp json tags]
  (call* :fhir_create (cfg-str cfg) tp json tags))

(defn -update [cfg tp id vid json tags]
  (call* :fhir_update (cfg-str cfg) tp id vid json tags))

(defn -delete [cfg tp id]
  (call* :fhir_delete (cfg-str cfg) tp id))

(defn -read [cfg tp id]
  (call* :fhir_read (cfg-str cfg) tp id))

(defn -vread [cfg tp vid]
  (call* :fhir_vread (cfg-str cfg) tp vid))

(defn -conformance [cfg]
  (f/parse (call* :fhir_conformance (cfg-str cfg))))

(defn -profile [cfg tp]
  (f/parse (call* :fhir_profile (cfg-str cfg) tp)))

(defn -search [cfg tp q]
  (f/parse (call* :fhir_search (cfg-str cfg) tp q)))

(defn -transaction [cfg bundle]
  (f/parse (call* :fhir_transaction (cfg-str cfg) bundle)))

(defn -history
  ([cfg]
   (f/parse (call* :fhir_history (cfg-str cfg) "{}")))
  ([cfg tp]
   (f/parse (call* :fhir_history (cfg-str cfg) tp "{}")))
  ([cfg tp id]
   (f/parse (call* :fhir_history (cfg-str cfg) tp id "{}"))))

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
