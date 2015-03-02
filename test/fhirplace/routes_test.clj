(ns fhirplace.routes-test
  (:require
    [clojure.test :refer :all]
    [clojure.set :as cs]
    [fhirplace.app :as fa]
    [fhirplace.core :as fc]
    [fhirplace.routes :as fr]
    ))

(deftest test-h
  (is
    (= (fr/h :a) {:fn :a :mw []}))
  (is
    (= (fr/h :a :b :c) {:fn :c :mw [:a :b]})))

(defn match? [meth url handler]
  (is
    (= (get-in (fc/match-route meth url) [:match :fn])
       handler)))

(defn mws? [meth url & mws]
  (let [route (fc/match-route meth url)
        should-mws (into #{} mws)
        is-mws (into #{} (fc/collect-mw route))]
    (println is-mws)
    (is (cs/subset? should-mws is-mws))))

(deftest routes-test
  (match? :GET  "/" #'fa/=html-face)
  (match? :POST "/" #'fa/=transaction)
  (match? :GET  "/metadata" #'fa/=metadata)
  (match? :GET  "/Profile/Patient" #'fa/=read)
  (match? :GET  "/Patient" #'fa/=search)
  (match? :GET  "/Patient/_search" #'fa/=search)
  (match? :POST "/Patient" #'fa/=create)
  (match? :GET  "/Patient/_history" #'fa/=history-type))

(deftest middle-wares-test
  (mws? :GET  "/" #'fa/<-outcome-on-exception)
  (mws? :POST "/" #'fa/<-outcome-on-exception)
  (mws? :PUT "/Patient/5"
        #'fa/->parse-body!
        #'fa/->valid-input!
        #'fa/->latest-version!
        #'fa/<-outcome-on-exception))
