(ns fhirplace.plugins
  (:require
    [clojure.java.io :as io]
    [clojure.java.shell :as cjs]
    [clojure.string :as cs]
    [clojure.data.json :as json])
  (:import [java.net.URL])
  )

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

(defn url-to-name [url]
  (-> (cs/split url #"/")
      last
      (cs/split #"\.")
      first))

(defn plugin-path [nm]
  (str (.getPath (io/resource "public")) "/" nm))

(defn url [s] (java.net.URL. s))

(defn pull-plugin-plan [url]
  (let [nm (url-to-name url)
        pth (plugin-path nm)
        clone-cmd ["git" "clone" url pth]]
    [["rm" "-rf" pth] clone-cmd]))

(defn exec-plan [plan]
  (doseq [cmd plan]
    (apply cjs/sh cmd)))

