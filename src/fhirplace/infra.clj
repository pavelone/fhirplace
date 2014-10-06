(ns fhirplace.infra
  (:require [clojure.string :as cs]))

(defn h
  "mk handler hash by convention"
  [& hnds]
  {:fn (last hnds)
   :mw (into [] (butlast hnds))})

(defn build-stack
  "wrap h with middlewares mws"
  [h mws]
  ((apply comp mws) h))

(defn base-url [{:keys [scheme server-name server-port]}]
  (str (name scheme) "://"
       server-name
       (if (= server-port 80)
         "" (str ":" server-port))))

(defn wrap-cfg
  "wrap with config; extract base url from request"
  [h]
  (fn [req] (h (assoc req :cfg {:base (base-url req)}))))

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
