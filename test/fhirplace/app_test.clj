(ns fhirplace.app-test
  (:require [fhirplace.app :as subj]
            [fhirplace.pg :as fp]
            [clojure.test :refer :all]))

(fp/call* :conformance.conformance "{}")
(print (subj/=metadata {:cfg {}}))
