(ns fhirplace.app
  (:use ring.util.response
        ring.util.request)
  (:require [compojure.core :as cc]
            [compojure.route :as cr]
            [compojure.handler :as ch]
            [clojure.string :as cs]
            [fhir :as f]
            [fhirplace.category :as fc]
            [fhir.operation-outcome :as fo]
            [fhirplace.db :as db]
            [fhirplace.infra :as fi]
            [ring.adapter.jetty :as jetty]
            [clojure.data.json :as json]
            [hiccup.page :refer (html5 include-css include-js)]
            [environ.core :as env]))

(import 'org.hl7.fhir.instance.model.Resource)
(import 'org.hl7.fhir.instance.model.AtomFeed)

(defn- content-type-format
  [fmt bd]
  (let [mime (if (and (instance? AtomFeed bd) (= :xml fmt))
               "application/atom+xml"
               (get {:json "application/json+fhir"
                     :xml "application/xml+fhir"} fmt))]
    (str mime "; charset=UTF-8")))

(defn- responce-content-type
  [resp fmt body]
  (update-in resp [:headers] merge {"content-type" (content-type-format fmt body)}))


(defn- serializable? [bd]
  (and bd
       (or (instance? Resource bd)
           (instance? AtomFeed bd))))

;; TODO set right headers
(defn <-format [h]
  "formatting midle-ware
  expected body is instance of fhir reference impl"
  (fn [req]
    (let [{bd :body :as resp} (h req)
          fmt (fi/get-format req)]
      (println "Formating: " bd)
      (->
        (if (serializable? bd)
          (assoc resp :body (f/serialize fmt bd))
          resp)
        (responce-content-type fmt bd)))))


(defn- get-stack-trace [e]
  (let [sw (java.io.StringWriter.)]
    (.printStackTrace e (java.io.PrintWriter. sw))
    (println "ERROR: " sw)
    (str sw)))

(defn- outcome [status text & issues]
  {:status status
   :body (fo/operation-outcome
           {:text {:status "generated" :div (str "<div>" text "</div>")}
            :issue issues })})

(defn <-outcome-on-exception [h]
  (fn [req]
    (println "<-outcome-on-exception")
    (try
      (h req)
      (catch Exception e
        (println "Exception")
        (println (get-stack-trace e))
        (outcome 500 "Server error"
                 {:severity "fatal"
                  :details (str "Unexpected server error " (get-stack-trace e))})))))


(defn ->type-supported! [h]
  (fn [{{tp :type} :params :as req}]
    (println "TODO: ->type-supported!")
    (if tp
      (h req)
      (outcome 404 "Resource type not supported"
               {:severity "fatal"
                :details (str "Resource type [" tp "] isn't supported")}))))

(defn ->resource-exists! [h]
  (fn [{{tp :type id :id } :params cfg :cfg :as req}]
    (println "->resource-exists!")
    (if (db/-resource-exists? cfg tp id)
      (h req)
      (outcome 404 "Resource not exists"
               {:severity "fatal"
                :details (str "Resource with id: " id " not exists")}))))

;; TODO: move to fhir f/errors could do it
(defn- safe-parse [x]
  (try
    [:ok (f/parse x)]
    (catch Exception e
      [:error (str "Resource could not be parsed: \n" x "\n" e)])))

(defn ->parse-tags!
  "parse body and put result as :data"
  [h]
  (fn [req]
    (println "->parse-tags!")
    (if-let [c (get-in req [:headers "category"])]
      (h (assoc req :tags (fc/safe-parse c)))
      (h (assoc req :tags [])))))

(defn ->parse-body!
  "parse body and put result as :data"
  [h]
  (fn [{bd :body :as req}]
    (println "->parse-body!")
    (let [[st res] (safe-parse (slurp bd)) ]
      (if (= st :ok)
        (h (assoc req :data res))
        (outcome 400 "Resource could not be parsed"
                 {:severity "fatal"
                  :details res})))))

(defn ->valid-input! [h]
  "validate :data key for errors"
  (fn [{res :data :as req}]
    (println "->valid-input!")
    (let [errors (f/errors res)]
      (if (empty? errors)
        (h (assoc req :data res))
        (apply outcome 422
               "Resource Unprocessable Entity"
               (map
                 (fn [e] {:severity "fatal"
                          :details (str e)})
                 errors))))))

(defn ->check-deleted! [h]
  (fn [{{tp :type id :id} :params cfg :cfg :as req}]
    (println "->check-deleted!")
    (if (db/-deleted? cfg tp id)
      (outcome 410 "Resource was deleted"
               {:severity "fatal"
                :details (str "Resource " tp " with " id " was deleted")})
      (h req))))

;; TODO: fixme
(defn- check-latest-version [cfg cl]
  (println "check-latest-version " cl)
  (let [[_ cl-] (cs/split cl (re-pattern (:base cfg)))]
    (let [[_ tp id _ vid] (cs/split cl- #"/")]
      (println "check-latest " tp " " id " " vid)
      (db/-latest? cfg tp id vid))))

(defn ->latest-version! [h]
  (fn [{{tp :type id :id} :params cfg :cfg :as req}]
    (println "->latest-version!")
    (if-let [cl (get-in req [:headers "content-location"])]
      (if (check-latest-version cfg cl)
        (h req)
        (outcome 412 "Updating not last version of resource"
                 {:severity "fatal"
                  :details (str "Not last version")}))

      (outcome 401 "Provide 'Content-Location' header for update resource"
               {:severity "fatal"
                :details (str "No 'Content-Location' header")}))))

(def uuid-regexp
  #"[0-f]{8}-([0-f]{4}-){3}[0-f]{12}")

(defn =metadata [{cfg :cfg :as req}]
  {:body (db/-conformance cfg)})

(defn =profile [{{tp :type} :params cfg :cfg :as req}]
  {:body (db/-profile cfg tp)})

(defn html-layout [content]
  (html5
    {:lang "en"}
    [:head
     [:title "fhirbase"]
     (include-css "//netdna.bootstrapcdn.com/twitter-bootstrap/2.3.1/css/bootstrap-combined.min.css")
     (include-css "//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css")
     (include-css "/face.css")
     [:body
      [:div.wrap content]
      (include-js "/face.js")
      ]]))

(defn html-face [req]
  (-> (response
        (html-layout
          [:div
           [:h1.top
            [:span {:class "icon logo"}  "L"]
            "fhirplace "
            ]
           [:div.ann
            [:a {:href "https://github.com/fhirbase/fhirplace"} "Open Source " [:big.fa.fa-github]]
            " FHIR server backed by "
            [:a {:href "https://github.com/fhirbase/fhirbase"} "fhirbase"]]
           [:div.bot
            [:h2 "Applications:"]
            [:hr]
            [:h4
             [:a {:href "/fhirface/index.html"}
              [:big.fa.fa-star]
              " fhirface"]
             [:small "  generic fhir client"]]
            [:hr]
            [:h4
             [:a {:href "/regi/index.html"}
              [:big.fa.fa-star]
              " regi"]
             [:small "  sample application"]]]
           ]))
      (content-type "text/html; charset=UTF-8")
      (status 200)))

(defn =search-all [req]
  #_(throw (Exception. "search-all not implemented"))
  (html-face req))

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

(defn ->check-tags [h]
  (fn [{tags :tags :as req}]
    (if (seq tags)
      (h req)
      (outcome 422 "Tags"
               {:severity "fatal"
                :details (str "Expected not empty tags (i.e. Category header)")}))) )

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
      (let [item (db/-create cfg resource-type json jtags)]
        (-> (resource-resp item)
            (status 201)
            (header "Category" (fc/encode-tags tags))))
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
    (println res)
    (-> (resource-resp res)
        (status 200))))

(defn =transaction
  [{bd :body cfg :cfg :as req}]
  (let [bundle (f/parse (slurp bd))
        json (f/serialize :json bundle)]
    {:body (db/-transaction cfg json)}))
