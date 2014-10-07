(ns fhirplace.format-test
  (:require
    [clojure.test :refer :all]
    [fhirplace.format :as fi]))

(defn match-format? [req fmt]
  (is (= (fi/get-format req) fmt)))

(deftest test-get-format
  (match-format? {} :json)
  (match-format? {:params {:_format "json"}} :json)
  (match-format? {:params {:_format "application/json"}} :json)
  (match-format? {:params {:_format "application/json+fhir"}} :json)

  (match-format? {:headers {"content-type" "json"}} :json)
  (match-format? {:headers {"content-type" "application/json"}} :json)
  (match-format? {:headers {"content-type" "application/json+fhir"}} :json)

  (match-format? {:params {:_format "xml"}} :xml)
  (match-format? {:params {:_format "application/xml"}} :xml)
  (match-format? {:params {:_format "application/atom+xml"}} :xml)
  (match-format? {:params {:_format "application/xml+fhir"}} :xml)

  (match-format? {:headers {"content-type" "xml"}} :xml)
  (match-format? {:headers {"content-type" "application/xml"}} :xml)
  (match-format? {:headers {"content-type" "application/atom+xml"}} :xml)
  (match-format? {:headers {"content-type" "application/xml+fhir"}} :xml))
