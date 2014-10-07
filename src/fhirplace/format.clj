(ns fhirplace.format
  (:require [clojure.string :as cs]))

(defn mime
  [content-type]
  (first (cs/split content-type #"\;")))

(def formats
  {"json" :json
   "application/json" :json
   "application/json+fhir" :json
   "xml" :xml
   "text/xml" :xml
   "application/xml" :xml
   "application/atom+xml" :xml
   "application/xml+fhir" :xml})

(def default-format :json)

(defn get-format [req]
  (let [fmt (get-in req [:params :_format])
        content-type (get-in req [:headers "content-type"])
        mime (and content-type (mime content-type))]
    (or (get formats (or fmt mime))
        default-format)))
