(ns fhirplace.infra-test
  (:require
    [clojure.test :refer :all]
    [fhirplace.infra :as fi]))

(deftest test-h
  (is
    (= (fi/h :a) {:fn :a :mw []}))
  (is
    (= (fi/h :a :b :c) {:fn :c :mw [:a :b]})))

(defn mk-mw [msg]
  (fn [h]
    (fn [r]
      (h (conj r msg)))))

(deftest test-build-stack
  (let [st (fi/build-stack
             (fn [req] (conj req "handler"))
             [(mk-mw "inter-1")
              (mk-mw "inter-2")
              (mk-mw "inter-3")])]

    (is (= (st [])
           ["inter-1" "inter-2" "inter-3" "handler"]))))

(deftest test-get-cfg
  (is
    (= (fi/base-url {:scheme :http
                     :server-name "hostic"
                     :server-port 3000})
       "http://hostic:3000"))

  (= (fi/base-url {:scheme :https
                   :server-name "hostic"
                   :server-port 80})
     "https://hostic"))

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
