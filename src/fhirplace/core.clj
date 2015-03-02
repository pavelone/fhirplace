(ns fhirplace.core
  (:require [route-map :as rm]
            [compojure.handler :as ch]
            [ring.middleware.file :as rmf]
            [fhirplace.app :refer :all]
            [fhirplace.format :as ff]
            [fhirplace.cors :as fc]
            [fhirplace.routes :as fr]
            [ring.adapter.jetty :as jetty]
            [environ.core :as env]
            [clojure.string :as cs]))

(defn build-stack
  "wrap h with middlewares mws"
  [h mws]
  ((apply comp mws) h))

(defn base-url [{{header-host :host} :headers
                 :keys [scheme server-name server-port]}]
  (let [header-hostname (when header-host
                          (re-find #"^[^:/]+" header-host))

        hostname (or header-hostname server-name)

        port (if header-hostname
               (re-find #"(?<=:)[0-9]+" header-host)
               server-port)]

    (format "%s://%s%s"
            (name scheme) hostname (if (or (not port) (= port 80))
                                     "" (str ":" port)))))

(defn wrap-cfg
  "wrap with config; extract base url from request"
  [h]
  (fn [req] (h (assoc req :cfg {:base (base-url req)}))))

(defn match-route [meth path]
  (rm/match [meth path] fr/routes))

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
    (println "\n\nDispatching " (:request-method req) " " (:uri req) " to " (pr-str handler))
    (println "Middle-wares: "   (pr-str mws))
    ((build-stack handler mws) req)))

(def app (-> dispatch
             (resolve-route)
             (ff/<-format)
             (wrap-cfg)
             (fc/<-cors)
             (ch/site)
             (rmf/wrap-file "resources/public")))

(defn start-server []
  (let [port (if (env/env :fhirplace-web-port)
               (Integer. (env/env :fhirplace-web-port))
               8080)]
    (println "Starting web server on " port)
    (jetty/run-jetty #'app {:port port :join? false})))

(defn stop-server [server] (.stop server))

(comment
  (stop-server srv)
  (def srv (start-server)))
