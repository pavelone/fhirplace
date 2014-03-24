(ns fhirplace.app
  (:use compojure.core)
  (:require [fhirplace.handler :as fhandler]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]))

(defroutes main-routes
  (GET "/" [] (fn [request]
                (str (:system request))))
  (POST "/:resource-type" [resource-type] fhandler/create-handler)
  (GET "/:resource-type/:id" [resource-type id] fhandler/read-handler)
  (DELETE "/:resource-type/:id" [resource-type id] fhandler/delete-handler)
  (PUT "/:resource-type/:id" [resource-type id] fhandler/update-handler))

(def app
  (handler/site main-routes))

(defn create-web-handler [system]
  (let [app (handler/site main-routes)]
    (fn [request]
      (app (assoc request :system system)))))
