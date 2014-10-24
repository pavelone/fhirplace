(ns fhirplace.app
  (:use ring.util.response
        ring.util.request)
  (:require [compojure.core :as cc]
            [compojure.route :as cr]
            [compojure.handler :as ch]
            [clojure.string :as cs]
            [fhir :as f]
            [fhirplace.category :as fc]
            [fhirplace.views :as fv]
            [fhir.operation-outcome :as fo]
            [fhirplace.db :as db]
            [clojure.stacktrace :as cst]
            [clojure.data.json :as json]))

;; TODO merge db here
(defn- get-stack-trace [e]
  (with-out-str (cst/print-stack-trace e)))

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
         (println ~nm)
         ~@body))))

(defn- outcome [error text & issues]
  (println "ERROR[" error "]" text)
  (let [status (get outcomes error)
        issues (or issues [{:severity "fatal" :details text}])]
    {:status status
     :body (fo/operation-outcome
             {:text {:status "generated"
                     :div (str "<div>" text "</div>")}
              :issue issues})}))

(defmw <-outcome-on-exception h [req]
  (try (h req)
       (catch Exception e
         (outcome :server-error
                  (str "Unexpected server error " (get-stack-trace e))))))


(defmw ->type-supported! h
  [{{tp :type} :params :as req}]
  (if tp
    (h req)
    (outcome :type-not-supported
             (str "Resource type [" tp "] isn't supported"))))

(defmw ->resource-exists! h
  [{{tp :type id :id } :params cfg :cfg :as req}]
  (if (db/-resource-exists? cfg tp id)
    (h req)
    (outcome :resource-not-exists
             (str "Resource with id: " id " not exists"))))

(defn- safe-parse [x]
  (try
    [:ok (f/parse x)]
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
  (let [errors (f/errors res)]
    (if (empty? errors)
      (h (assoc req :data res))
      (outcome :resource-not-valid
               "Resource Unprocessable Entity"
               (map #({:severity "fatal" :details (str %)}) errors)))))

(defmw ->check-deleted! h
  [{{tp :type id :id} :params cfg :cfg :as req}]
  (if (db/-deleted? cfg tp id)
    (outcome :resource-deleted (str "Resource " tp " with " id " was deleted"))
    (h req)))

;; TODO: fixme
(defn- check-latest-version [cfg cl]
  (println "check-latest-version " cl)
  (let [[_ cl-] (cs/split cl (re-pattern (:base cfg)))]
    (let [[_ tp id _ vid] (cs/split cl- #"/")]
      (println "check-latest " tp " " id " " vid)
      (db/-latest? cfg tp id vid))))

(defmw ->latest-version! h
  [{{cl "content-location"} :headers cfg :cfg :as req}]
  (cond
    (not cl) (outcome :no-version-info "Provide 'Content-Location' header for update resource")
    (not (check-latest-version cfg cl)) (outcome :not-last-version "Updating not last version of resource")
    :else (h req)))

(defmw ->check-tags h
  [{tags :tags :as req}]
  (if (seq tags)
    (h req)
    (outcome :no-tags "Expected not empty tags (i.e. Category header)")))

;; ACTIONS

(defn =metadata [{cfg :cfg :as req}]
  {:body (db/-conformance cfg)})

(defn =profile [{{tp :type} :params cfg :cfg :as req}]
  {:body (db/-profile cfg tp)})

(defn =html-face [req]
  (->
    (fv/html-face req)
    (response)
    (content-type "text/html; charset=UTF-8")
    (status 200)))

(defn =search [{{rt :type :as param} :params cfg :cfg :as req}]
  (println "QUERY-STRING: " (:query-string req))
  (let [query (or (:query-string req) "")]
    {:body (db/-search cfg rt query)}))

(defn =tags-all [{cfg :cfg}]
  {:body (db/-tags cfg)})

(defn =resource-type-tags [{{rt :type} :params cfg :cfg}]
  {:body (db/-tags cfg rt)})

(defn =resource-tags [{{rt :type id :id} :params cfg :cfg}]
  {:body (db/-tags cfg rt id)})

(defn =resource-version-tags [{{rt :type id :id vid :vid} :params cfg :cfg}]
  {:body (db/-tags cfg rt id vid)})


;;TODO make as middle ware
(defn =affix-resource-tags [{{rt :type id :id} :params tags :tags cfg :cfg}]
  (db/-affix-tags cfg rt id tags)
  {:body (db/-tags cfg rt id)})

(defn =affix-resource-version-tags [{{rt :type id :id vid :vid} :params tags :tags cfg :cfg}]
  (db/-affix-tags cfg rt id vid tags)
  {:body (db/-tags cfg rt id vid)})

(defn =remove-resource-tags [{{rt :type id :id} :params cfg :cfg}]
  (let [num (db/-remove-tags cfg rt id)]
    {:body (str num " tags was removed")}))

(defn =remove-resource-version-tags [{{rt :type id :id vid :vid} :params cfg :cfg}]
  (let [num (db/-remove-tags cfg rt id vid)]
    {:body (str num " tags was removed")}))

(defn =history [{{rt :type id :id} :params cfg :cfg}]
  {:body (db/-history cfg rt id)})

(defn =history-type [{{rt :type} :params cfg :cfg}]
  {:body (db/-history cfg rt)})

(defn =history-all [{cfg :cfg}]
  {:body (db/-history cfg)})

(defn resource-resp [res]
  (let [bundle (json/read-str res :key-fn keyword)
        entry (first (:entry bundle))
        loc (:href (first (:link entry)))
        tags (:category entry)
        last-modified (:updated entry)
        fhir-res (f/parse (json/write-str (:content entry)))]

    (-> {:body fhir-res}
        (header "Location" loc)
        (header "Content-Location" loc)
        (header "Category" (fc/encode-tags tags))
        (header "Last-Modified" last-modified))))

(defn =create
  [{{rt :type} :params res :data tags :tags cfg :cfg :as req}]
  {:pre [(not (nil? res))]}
  (println "=create " (keys req))
  (let [json (f/serialize :json res)
        jtags (json/write-str tags)
        resource-type (str (.getResourceType res))]
    (if (= rt resource-type)
      (-> (db/-create cfg resource-type json jtags)
          (resource-resp)
          (status 201)
          (header "Category" (fc/encode-tags tags)))
      (throw (Exception. (str "Wrong resource type '" resource-type "' for '" rt "' endpoint"))))))

(defn =update
  [{{rt :type id :id} :params res :data tags :tags cfg :cfg :as req}]
  {:pre [(not (nil? res))]}
  (let [json (f/serialize :json res)
        jtags (json/write-str tags)
        resource-type (str (.getResourceType res))]
    (if (= rt resource-type)
      (let [cl (get-in req [:headers "content-location"])
            item (db/-update cfg rt id cl json jtags)]
        (-> (resource-resp item)
            (status 200)))
      (throw (Exception. (str "Wrong resource type '" resource-type "' for '" rt "' endpoint"))))))

(defn- validate-resource-type
  [cfg rt res]
  (let [json (f/serialize :json res)
        resource-type (str (.getResourceType res))]
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
  (-> (response (str (db/-delete cfg rt id)))
      (status 204)))

;;TODO add checks
(defn =read [{{rt :type id :id} :params cfg :cfg}]
  (let [res (db/-read cfg rt id)]
    (-> (resource-resp res)
        (status 200))))

(defn =vread [{{rt :type id :id vid :vid} :params cfg :cfg}]
  (let [res (db/-vread cfg rt (str id "/_history/" vid))]
    (-> (resource-resp res)
        (status 200))))

(defn =transaction
  [{bd :body cfg :cfg :as req}]
  (let [bundle (f/parse (slurp bd))
        json (f/serialize :json bundle)]
    {:body (db/-transaction cfg json)}))
