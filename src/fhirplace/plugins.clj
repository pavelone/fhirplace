(ns fhirplace.plugins
  (:require
    [clojure.java.io :as io]
    [clojure.data.json :as json]))

(defn add-to-plugins [acc file]
  (let [plug (-> (io/reader file)
                 (java.io.PushbackReader.)
                 (json/read-json true))]
    (assoc acc (:name plug) (merge plug {:dir (.getName (.getParentFile file))}))))

(defn read-plugins []
  (->> (io/resource "public")
       (io/file)
       (.listFiles)
       (filter #(.isDirectory %))
       (map #(java.io.File. % "fhir.json"))
       (filter #(.exists %))
       (reduce add-to-plugins {})))

(read-plugins)
