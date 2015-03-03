(ns fhirplace.app-test
  (:require [fhirplace.app :as subj]
            [fhirplace.pg :as fp]
            [clojure.test :refer :all]))

(defn is-ok? [resp]
  (is
    (= 200 (:status resp))))

(defn is-resource-type [resp exp]
  (is
    (= exp
       (get-in resp [:body :resourceType]))))


(deftest test-build-stack
  (def resp
    (subj/=metadata {:cfg {}}))
  (is-ok? resp)
  (is-resource-type resp "Conformance"))

(deftest  test-search
  (def resp
    (subj/=search {:cfg {} :params {:type "StructureDefinition"} :query-string "name=patient"}))

  (is-ok? resp)
  (is-resource-type resp "Bundle"))


(deftest  test-history
  (def resp
    (subj/=history {:cfg {} :params {:type "Profile"} :query-string "name=patient"}))

  (is-ok? resp)
  (is-resource-type resp "Bundle"))

(deftest  test-history-type
  (def resp
    (subj/=history-type {:cfg {} :params {:type "Profile"} :query-string "name=patient"}))
  (is-ok? resp)
  (is-resource-type resp "Bundle"))

(deftest  test-history-all
  (def resp
    (subj/=history-all {:cfg {}}))
  (is-ok? resp)
  (is-resource-type resp "Bundle"))
