(ns fhirplace.integration-test
  (:require [fhirplace.core :as fc]
            [fhirplace.fhir :as ff]
            [fhirplace.pg :as fp]
            [clojure.test :refer :all]
            [plumbing.core :refer [fnk]]
            [plumbing.graph :as pg]
            [ring.mock.request :as mock]
            [clojure.string :as cs]))

(defmacro def-scenario [nm m]
  `(def ~nm  (pg/lazy-compile ~m)))


(defn url [& parts]
  (apply str "/" (interpose "/" parts)))

(defn fixture [nm]
  (slurp (str "test/fhirplace/fixtures/" nm)))

(defn GET [url]
  (fc/app (mock/request :get url)))

(defn POST [url body]
  (println "POST: " url)
  (fc/app (-> (mock/request :post url)
              (mock/body body))))

(defn PUT [url body]
  (println "PUT: " url)
  (fc/app (-> (mock/request :put url)
              (mock/body body))))

(defn DELETE [url]
  (println "DELETE: " url)
  (fc/app (mock/request :delete url)))

(defn get-header
  [h res]
  (get-in res [:headers h]))

(defn mime-type
  [fmt]
  ({"xml" "application/xml+fhir"
    "json" "application/json+fhir"} fmt))

(defn get-resource-ids [content-loc]
  (let [[vid _ id rt] (reverse (cs/split content-loc #"/"))]
    [rt id vid]))

(def-scenario simple-crud
  {:metadata (fnk [] (GET (url "metadata")))
   :conformance (fnk [metadata] (ff/parse (:body metadata)))

   :pt_profile (fnk [] (GET (url "Profile" "Patient")))

   :search (fnk [] (GET (url "Patient" "_search")))
   :search_atom (fnk [search] (ff/parse (:body search)))

   :new_resource (fnk [] (POST
                           (url "Patient")
                           (fixture "patient.json")))

   :new_resource_loc (fnk [new_resource]
                          (println "HEADER: " (get-header "Location" new_resource))
                          (get-header "Location" new_resource))

   :get_new_resource (fnk [new_resource_loc] (GET new_resource_loc))

   :update_resource  (fnk [new_resource_loc new_resource]
                          (let [[rt id vid] (get-resource-ids new_resource_loc)]
                            (PUT (url rt id) (:body new_resource))))

   :updated_version  (fnk [update_resource]
                          (GET (get-header "Location" update_resource)))


   :history_of_resource  (fnk [update_resource]
                              (let [[rt id vid] (get-resource-ids (get-header "Location" update_resource))]
                                (GET (url rt id "_history"))))

   :delete_resource  (fnk [new_resource_loc]
                          (let [[rt id vid] (get-resource-ids new_resource_loc)]
                            (DELETE (url rt id))))
   })

(def subj (simple-crud {}))

(defmacro status? [status response]
  `(is (= (:status ~response) ~status)))

(defn is-type? [subj tp]
  (is tp
      (:resourceType subj)))

(deftest test-simple-crud
  (fp/rollback-transaction
    (status? 200 (:metadata subj))
    (is-type? "Conformance" (:conformance subj))

    (status? 200 (:pt_profile subj))
    (is-type? "Profile"
              (ff/parse (:body (:pt_profile subj))))

    (status? 200 (:search subj))
    (is-type? "Bundle"
              (:search_atom subj))

    (status? 201 (:new_resource subj))

    (is (not (nil? (:new_resource_loc subj))))

    (status? 200 (:get_new_resource subj))

    (status? 200 (:update_resource subj))

    (status? 200 (:updated_version subj))

    (is-type? "Patient"
              (ff/parse (:body (:updated_version subj))))

    (status? 200 (:history_of_resource subj))

    (is-type? "Bundle"
              (ff/parse (:body (:history_of_resource subj))))

    (status? 204 (:delete_resource subj))))
