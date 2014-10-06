(ns fhirplace.core
  (:require [route-map :as rm]
            [compojure.handler :as ch]
            [ring.middleware.file :as rmf]
            [fhirplace.app :refer :all]
            [fhirplace.cors :as fc]
            [fhirplace.infra :as fi :refer [h]]
            [ring.adapter.jetty :as jetty]
            [environ.core :as env]
            [clojure.string :as cs]))

;; /Patient/:id/_history/_tags
(def instance-hx-tag-routes
  {:GET       (h =resource-version-tags)
   :POST      (h ->parse-tags!
                 ->check-tags
                 =affix-resource-version-tags)
   "_delete" (:POST (h =remove-resource-version-tags))})

;; /Patient/:id/_history
(def instance-hx-routes
  {:GET      (h =history)
   "_tags"  instance-hx-tag-routes
   [:vid]   {:GET     (h =vread)
             "_tags" {:GET (h =resource-version-tags)}}})

;; /Patient/:id/_tags
(def instance-tag-routes
  {:GET       (h =resource-tags)
   :POST      (h ->parse-tags!
                 ->check-tags
                 =affix-resource-tags)
   "_delete" {:POST (h =remove-resource-tags)}})

;; /Patient/:id/
(def instance-level-routes
  {:mw ['->resource-exists! ->check-deleted!]
   :GET        (h =read)
   :DELETE     (h =delete)
   :PUT        (h ->parse-tags!
                  ->parse-body!
                  ->latest-version!
                  ->valid-input!
                  =update)
   "_tags"    instance-tag-routes
   "_history" instance-hx-routes })

;; /Patient/_validate
(def validate-routes
  {:mw [->parse-body! ->valid-input!]
   :POST (h ->parse-tags! =validate-create)
   [:id] {:POST (h ->latest-version! =validate-update)}})

;; /Patient/
(def type-level-routes
  {:mw [->type-supported!]
   :POST        (h ->parse-tags!
                   ->parse-body!
                   ->valid-input!
                   =create)
   :GET         (h =search)
   "_search"   {:GET (h =search)}
   "_tags"     {:GET (h =resource-type-tags)}
   "_history"  {:GET (h =history-type)}
   "_validate" validate-routes
   [:id]       instance-level-routes})

;; /
(def routes
  {:mw [<-outcome-on-exception]
   :GET        (h =search-all)
   :POST       (h =transaction)
   "metadata" {:GET (h =metadata)}
   "_tags"    {:GET (h =tags-all)}
   "_history" {:GET (h =history-all)}
   "Profile"  {[:type] {:GET (h =profile)}}
   [:type]    type-level-routes})

(defn match-route [meth path]
  (rm/match [meth path] routes))

(defn resolve-route [h]
  (fn [{uri :uri meth :request-method :as req}]
    (if-let [route (match-route meth uri)]
      (h (assoc req :route route))
      {:status 404 :body (str "No route " meth " " uri)})))

(defn collect-mw [match]
  (->> (conj (:parents match) (:match match))
       (mapcat :mw)
       (filterv (complement nil?))))

(defn dispatch [{handler :handler route :route :as req}]
  (let [mws     (collect-mw route)
        handler (get-in route [:match :fn])
        req     (update-in req [:params] merge (:params route))]
    (println "PARAMS: " (:params route))
    (println "\n\nDispatching " (:request-method req) " " (:uri req) " to " (pr-str handler))
    (println "Middle-wares: " (pr-str mws))
    ((fi/build-stack handler mws) req)))

(def app (-> dispatch
             (resolve-route)
             (fhirplace.app/<-format)
             (fi/wrap-cfg)
             (fc/<-cors)
             (ch/site)
             (rmf/wrap-file "resources/public")))

(defn start-server []
  (jetty/run-jetty #'app {:port (env/env :fhirplace-web-port) :join? false}))

(defn stop-server [server] (.stop server))
