(defproject explore-http-kit "0.1.0-SNAPSHOT"
  :description "Explore http-kit callback/thread behavior."
  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [debugger "0.1.7"]
    [http-kit "2.1.18"]]
  :main ^:skip-aot explore-http-kit.explore-thread-use
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

