(ns fhirplace.main
  (:require [fhirplace.core :as fc]))

(defn -main [& args]
  (fc/start-server))
