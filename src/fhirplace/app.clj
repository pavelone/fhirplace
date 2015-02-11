(ns fhirplace.app
  (:require [ring.util.response :as rur]
            [clojure.string :as cs]
            [fhirplace.fhir :as ff]
            [fhirplace.pg :as fp]
            [fhirplace.plugins :as fpl]
            [fhirplace.category :as fc]
            [fhirplace.views :as fv]
            [hiccup.core :as hc]
            ; [fhir.operation-outcome :as fo]
            [clojure.stacktrace :as cst]
            [clojure.data.json :as json]))

;; TODO merge db here
(defn- get-stack-trace [e]
  (with-out-str (cst/print-stack-trace e)))

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

(def outcomes
  {:server-error         500
   :type-not-supported   404
   :resource-not-exists  404
   :resource-not-parsed  400
   :resource-not-valid   422
   :resource-deleted     410
   :not-last-version     412
   :no-version-info      401
   :no-tags              422})

(defmacro  defmw [nm h prms & body]
  `(defn ~(symbol nm) [h#]
     (let [~h h#]
       (fn ~prms
         (println "MW: " ~(name nm))
         ~@body))))

(defn- outcome [error-key text & issues]
  (println "ERROR[" error-key "]" text)
  (let [status (get outcomes error-key)
        issues (or issues [{:severity "fatal" :details text}])]
    {:status status
     :body (ff/operation-outcome
             {:text {:status "generated"
                     :div (str "<div>" text "</div>")}
              :issue issues})}))

(defmw <-outcome-on-exception h [req]
  (try (h req)
       (catch Exception e
         (println "EXEPTION:" (get-stack-trace e))
         (outcome :server-error
                  (str "Unexpected server error " (hc/h (get-stack-trace e)))))))


(defmw ->type-supported! h
  [{{tp :type} :params :as req}]
  (if tp
    (h req)
    (outcome :type-not-supported
             (str "Resource type [" tp "] isn't supported"))))

(defmw ->resource-exists! h
  [{{tp :type id :id } :params cfg :cfg :as req}]
  (if (fp/call* :crud.is_exists (cfg-str cfg) tp id)
    (h req)
    (outcome :resource-not-exists
             (str "Resource with id: " id " not exists"))))

(defn- safe-parse [x]
  (try
    [:ok (ff/parse x)]
    (catch Exception e
      [:error (str "Resource could not be parsed: \n" x "\n" e)])))

;; "parse body and put result as :data"
(defmw ->parse-body! h
  [{bd :body :as req}]
  (let [[st res] (safe-parse (slurp bd)) ]
    (if (= st :ok)
      (h (assoc req :data res))
      (outcome :resource-not-parsed res))))

;"validate :data key for errors"
(defmw ->valid-input! h
  [{res :data :as req}]
  (let [errors (ff/validate res)]
    (if (empty? errors)
      (h (assoc req :data res))
      (outcome :resource-not-valid
               "Resource Unprocessable Entity"
               (map #({:severity "fatal" :details (str %)}) errors)))))

(defmw ->check-deleted! h
  [{{tp :type id :id} :params cfg :cfg :as req}]
  (if (fp/call* :crud.is_deleted (cfg-str cfg) tp id)
    (outcome :resource-deleted (str "Resource " tp " with " id " was deleted"))
    (h req)))

;; TODO: fixme
(defn- check-latest-version [cfg id tp vid]
  (println "check-latest " tp " " id " " vid)
  (fp/call* :crud.is_latest (cfg-str cfg) tp id vid)    )

(defmw ->latest-version! h
  [{{tp :type id :id} :params res :data cfg :cfg :as req}]
  (cond
    (not (get-in res [:meta :versionId])) (outcome :no-version-info "Provide 'Content-Location' header for update resource")
    (not (check-latest-version cfg id tp (get-in res [:meta :versionId]))) (outcome :not-last-version "Updating not last version of resource")
    :else (h req)))

(defmw ->check-tags h
  [{tags :tags :as req}]
  (if (seq tags)
    (h req)
    (outcome :no-tags "Expected not empty tags (i.e. Category header)")))


;; ACTIONS

(defn respond [bd]
  {:body bd})

(defn =metadata [{cfg :cfg :as req}]
  (-> (fp/call* :conformance.conformance (cfg-str cfg))
      ff/parse
      rur/response))

(defn =profile [{{tp :type} :params cfg :cfg}]
  (-> (fp/call* :conformance.profile (cfg-str cfg) tp)
      ff/parse
      rur/response))

(defn =html-face [req]
  (-> (fv/html-face {:plugins (fpl/read-plugins)})
      (rur/response)
      (rur/content-type "text/html; charset=UTF-8")
      (rur/status 200)))

(defn =search [{{tp :type} :params cfg :cfg q :query-string}]
  (-> (fp/call* :search.fhir_search (cfg-str cfg) tp (or q ""))
      ff/parse
      rur/response))

(defn =tags-all [{cfg :cfg}]
  (-> (fp/call* :fhir_tags (cfg-str cfg))
      rur/response))

(defn =resource-type-tags [{{rt :type} :params cfg :cfg}]
  (-> (fp/call* :fhir_tags (cfg-str cfg) rt)
      rur/response))

(defn =resource-tags [{{rt :type id :id} :params cfg :cfg}]
  (-> (fp/call* :fhir_tags (cfg-str cfg) rt id)
      rur/response))

(defn =resource-version-tags [{{rt :type id :id vid :vid} :params cfg :cfg}]
  (-> (fp/call* :fhir_tags (cfg-str cfg) rt id vid)
      rur/response))

(defn =affix-resource-tags [{{tp :type id :id} :params tags :tags cfg :cfg}]
  (fp/call* :fhir_affix_tags (cfg-str cfg) tp id (json/write-str tags)   )
  (-> (fp/call* :fhir_tags (cfg-str cfg) tp id)
      rur/response))

(defn =affix-resource-version-tags [{{tp :type id :id vid :vid} :params tags :tags cfg :cfg}]
  (fp/call* :fhir_affix_tags (cfg-str cfg) tp id vid (json/write-str tags)   )
  (-> (fp/call* :fhir_tags (cfg-str cfg) tp id vid)
      rur/response))

(defn =remove-resource-tags [{{tp :type id :id} :params cfg :cfg}]
  (-> (fp/call* :fhir_remove_tags (cfg-str cfg) tp id)
      (str " tags was removed")
      rur/response))

(defn =remove-resource-version-tags [{{tp :type id :id vid :vid} :params cfg :cfg}]
  (-> (fp/call* :crud.remove_tags (cfg-str cfg) tp id vid)
      (str " tags was removed")
      rur/response))

(defn =history [{{tp :type id :id} :params cfg :cfg}]
  (-> (fp/call* :crud.history (cfg-str cfg) tp id)
      ff/parse
      rur/response))

(defn =history-type [{{tp :type} :params cfg :cfg}]
  (-> (fp/call* :crud.history (cfg-str cfg) tp)
      ff/parse
      rur/response))

(defn =history-all [{cfg :cfg}]
  (-> (fp/call* :crud.history (cfg-str cfg))
      ff/parse
      rur/response))

;(rur/header "Category" (fc/encode-tags tags))

(defn resource-resp [res]
  (let [res (ff/parse res)]
    (-> {:body res}
        (rur/header "Content-Location" (get-in res [:meta :versionId]))
        (rur/header "Last-Modified" (get-in res [:meta :lastUpdated])))))

(defn =create
  [{{rt :type} :params res :data tags :tags cfg :cfg :as req}]
  {:pre [(not (nil? res))]}
  (println "=create " (keys req))
  (let [json (ff/generate :json res)
        jtags (json/write-str tags)
        resource-type (:resourceType res)]
    (if (= rt resource-type)
      (-> (fp/call* :crud.create (cfg-str cfg) json)
          (resource-resp)
          (rur/status 201)
          (rur/header "Category" (fc/encode-tags tags)))
      (throw (Exception. (str "Wrong resource type '" resource-type "' for '" rt "' endpoint"))))))

(defn =update
  [{{rt :type id :id} :params res :data cfg :cfg :as req}]
  {:pre [(not (nil? res))]}
  (let [json (ff/generate :json res)
        resource-type (:resourceType res)]
    (if (= rt resource-type)
      (let [cl (get-in req [:headers "content-location"])
            item (fp/call* :crud.update (cfg-str cfg) json)]
        (-> (resource-resp item)
            (rur/status 200)))
      (throw (Exception. (str "Wrong resource type '" resource-type "' for '" rt "' endpoint"))))))

(defn- validate-resource-type
  [cfg rt res]
  (let [json (ff/generate :json res)
        resource-type (:resourceType res)]
    (if (= rt resource-type)
      {:status 200}
      (throw (Exception. (str "Wrong resource type '" resource-type "' for '" rt "' endpoint"))))))

(defn =validate-create
  [{{rt :type} :params res :data tags :tags cfg :cfg}]
  #_{:pre [(not (nil? res))]}
  (validate-resource-type cfg rt res))

(defn =validate-update
  [{{rt :type} :params res :data cfg :cfg}]
  #_{:pre [(not (nil? res))]}
  (validate-resource-type cfg rt res))

(defn =delete
  [{{rt :type id :id} :params body :body cfg :cfg}]
  (-> (fp/call* :crud.delete (cfg-str cfg) rt id)
      (str)
      (rur/response)
      (rur/status 204)))

(defn =read [{{rt :type id :id} :params cfg :cfg}]
  (-> (fp/call* :crud.read (cfg-str cfg) id)
      (resource-resp)
      (rur/status 200)))

(defn =vread [{{rt :type id :id vid :vid} :params cfg :cfg}]
  (-> (fp/call* :crud.vread (cfg-str cfg) id)
      (resource-resp)
      (rur/status 200)))

(defn =transaction
  [{bd :body cfg :cfg :as req}]
  (let [bundle (ff/parse (slurp bd))
        json (ff/generate :json bundle)]
    (-> (fp/call* :transaction.transaction (cfg-str cfg) json)
        (ff/parse)
        (rur/response))))
;; api

(defn =list-apps [req]
  (-> (fpl/read-plugins)
      (json/write-str)
      (rur/response)))

(defn =upload-app [{form :multipart-params :as req}]
  (let [tmpfile (get-in form ["file" :tempfile])
        plugin-name (get form "app")
        res (fpl/upload plugin-name tmpfile)]
    (if (= (:exit res) 0)
      (-> (fpl/read-plugin plugin-name)
          (json/write-str)
          (rur/response))
      {:status 500
       :body (pr-str res)})))

(defn =rm-app [{{nm :app} :params :as req}]
  (let [res (fpl/rm nm)]
    (if (= (:exit res) 0)
      (-> {:status "removed" :name nm :message (str "app [" nm "] successfully removed")}
          (json/write-str)
          (rur/response))
      {:status 500
       :body (pr-str res)})))
