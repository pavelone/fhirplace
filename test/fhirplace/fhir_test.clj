(ns fhirplace.fhir-test
  (:require [fhirplace.fhir :as ff]
            [fhirplace.pg :as fp]
            [clojure.test :refer :all]))

(def conf-json (fp/call* :conformance.conformance "{}"))

(def conf
  (ff/parse conf-json))


