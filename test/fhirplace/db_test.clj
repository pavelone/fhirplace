(ns fhirplace.external-test
  (:require
    [clojure.test :refer :all]
    [fhirplace.db :as db]
    [fhir :as f]))


(def cfg (db/cfg-str {}) )

(deftest integration-test
   ; (def pt (db/-create cfg "Patient" (th/fixture "patient.json") "[]"))

   ; (is (not= nil pt))

   ; (is (db/-latest? cfg "Patient"
   ;                  (:logical_id pt)
   ;                  (:version_id pt)))

   ; (let [res (db/-search cfg  "Patient" {:_sort ["name"]})]
   ;   (is (not= nil pt)))

   ; (is (> 0 (count (.getEntryList
   ;                   (db/-search cfg "Patient" {:name "Pete" :_sort ["name"]})))))

   ; (db/-update cfg "Patient" (:logical_id pt) (th/fixture "patient.json") "[]")
   ; (db/-delete cfg "Patient" (:logical_id pt))
   )

