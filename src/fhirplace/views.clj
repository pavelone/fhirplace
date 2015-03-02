(ns fhirplace.views
  (:require
    [hiccup.page :refer (html5 include-css include-js)]
    [hiccup.core :as hc]
    ))

(defn html-layout [content]
  (html5
    {:lang "en"}
    [:head
     [:title "fhirbase"]
     (include-css "//netdna.bootstrapcdn.com/twitter-bootstrap/2.3.1/css/bootstrap-combined.min.css")
     (include-css "//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css")
     (include-css "/face.css")
     [:body
      [:div.wrap content]
      (include-js "/face.js")]]))

(defn html-face []
  (html-layout
    [:div
     [:h1.top
      [:span {:class "icon logo"}  "L"]
      "fhirplace "]
     [:div.ann
      [:a {:href "https://github.com/fhirbase/fhirplace"} "Open Source " [:big.fa.fa-github]]
      " FHIR server backed by "
      [:a {:href "https://github.com/fhirbase/fhirbase"} "fhirbase"]]
     [:div.bot
      [:h2 "Operations"]
      [:hr]]]))
