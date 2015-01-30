(ns fhirplace.format
  (:require
    [fhirplace.fhir :as ff]
    [clojure.string :as cs]))

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

(def response-formats
  {:xml "application/xml+fhir"
   :json "application/json+fhir" })


(def default-format :json)

(defn get-format [req]
  (let [fmt          (get-in req [:params :_format])
        content-type (get-in req [:headers "content-type"])
        mime         (and content-type (mime content-type))]
    (or (get formats fmt)
        (get formats mime)
        default-format)))

(defn content-type-format
  [fmt bd]
  (-> (or (get response-formats fmt) (get response-formats :json))
      (str "; charset=UTF-8")))

(defn response-content-type
  [resp fmt body]
  (update-in resp [:headers]
             merge {"content-type" (content-type-format fmt body)}))


(defn <-format [h]
  "formatting midle-ware
  read incoming content-type
  inspect body and if it is instance of fhir reference impl
  use fhir java searivalizer"
  (fn [req]
    (let [fmt (get-format req)
          {bd :body :as resp} (h req)
          bd (if (ff/serializable? bd)(ff/generate fmt bd) bd)]
      #_(println "Formating " fmt ": " bd "")
      (-> (assoc resp :body bd)
          (response-content-type fmt bd)))))
