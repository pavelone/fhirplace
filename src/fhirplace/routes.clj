(ns fhirplace.routes
  (:require
    [fhirplace.app :refer :all]))

(defn h
  "mk handler hash by convention"
  [& hnds]
  {:fn (last hnds)
   :mw (into [] (butlast hnds))})

;; /api
(def api
  {"app" {:GET (h #'=list-apps)
          :POST (h #'=upload-app)
          [:app] {:DELETE  (h #'=rm-app)}}})

;; /Patient/:id/_history
(def instance-hx-routes
  {:GET     (h #'=history)
   [:vid]   {:GET    (h #'=vread)}})

;; /Patient/:id/
(def instance-level-routes
  {:mw [#'->resource-exists! #'->check-deleted!]
   :GET       (h #'=read)
   :DELETE    (h #'=delete)
   :PUT       (h #'->parse-body!
                 #'->latest-version!
                 #'->valid-input!
                 #'=update)
   "_history" instance-hx-routes })

;; /Patient/_validate
(def validate-routes
  {:mw [#'->parse-body! #'->valid-input!]
   :POST (h #'=validate-create)
   [:id] {:POST (h #'->latest-version! #'=validate-update)}})

;; /Patient/
(def type-level-routes
  {:mw [#'->type-supported!]
   :POST       (h #'->parse-body!
                  #'->valid-input!
                  #'=create)
   :GET        (h #'=search)
   "_search"   {:GET (h #'=search)}
   "_history"  {:GET (h #'=history-type)}
   "_validate" validate-routes
   [:id]       instance-level-routes})

;; /
(def routes
  {:mw [#'<-outcome-on-exception]
   "api" api
   :GET        (h #'=html-face)
   :POST       (h #'=transaction)
   "metadata" {:GET (h #'=metadata)}
   "_history" {:GET (h #'=history-all)}
   [:type]    type-level-routes})
