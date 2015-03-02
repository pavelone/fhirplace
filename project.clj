(defproject fhirplace "0.1.0-SNAPSHOT"
  :description "FHIR server backed by fhirbase"
  :url "https://github.com/fhirbase/fhirplace"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[com.jakemccrary/lein-test-refresh "0.5.2"] ]

  :source-paths  ["lib/route-map/src"
                  "lib/route-map/test"
                  "lib/fhir.clj/src"
                  "lib/fhir.clj/test"
                  "src"]
  :ring {:handler fhirplace.core/app}

  :resource-paths    ["resources"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.4"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.xml "0.0.7"]
                 [clojure-saxon "0.9.3"]
                 [honeysql "0.4.3"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [org.postgresql/postgresql "9.3-1101-jdbc41"]
                 [ring-mock "0.1.5"]
                 [compojure "1.1.6"]
                 [ring "1.2.1"]
                 [cheshire "5.4.0"]
                 [clj-http "0.9.2"]
                 [environ  "0.5.0"]
                 [hiccup "1.0.5"]]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [midje "1.6.0"]
                                  [prismatic/plumbing "0.3.7"]
                                  [leiningen "2.3.4"]
                                  [org.clojure/java.classpath "0.2.0"]]
                   :plugins [[lein-kibit "0.0.8"]] }}

  :main fhirplace.main)
