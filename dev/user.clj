(ns user
  (:require [fhirplace.core :as fc]))

(println
  "
  Your server is in srv Var
  To start server run (start)
  To stop eval (stop)
  ")

(def srv  (atom nil))

(defn start  []
  (if @srv
    (println "Server already started")
    (reset! srv  (fc/start-server))))

(defn stop  []
  (if @srv
    (.stop @srv)
    (println "No server started")))
