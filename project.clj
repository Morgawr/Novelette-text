(defproject novelette-text "0.1.2-SNAPSHOT"
  :description "Clojurescript library for flexible text rendering on canvas."
  :license "MIT License"
  :url "https://github.com/Morgawr/Novelette-text"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [prismatic/schema "1.0.4"]
                 [lein-doo "0.1.6"]]
  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-doo "0.1.6"]]
  :hooks [leiningen.cljsbuild]
  :clean-targets ["runtime/js/*"]
  :cljsbuild
  {
   :builds
   [
    {:id "novelette-text"
     :source-paths ["src/"]
     :compiler
     {:optimizations :advanced
      :output-dir "runtime/js"
      :output-to  "runtime/js/novelette-text.js"
      :pretty-print true
      :source-map "runtime/js/novelette-text.js.map"
      }}]})
