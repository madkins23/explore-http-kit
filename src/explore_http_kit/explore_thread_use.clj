(ns explore-http-kit.explore-thread-use
  "Explore http-kit use of threads.

   Demonstrate some interesting quirks in http-kit and how to work around them.

   (c) 2015 Marc M. Adkins)"
  (:gen-class)
  (:require [clojure.string :refer [trim]]
            [org.httpkit.client :as http])
  (:import  [java.lang.Thread]
            [java.util.concurrent ThreadPoolExecutor LinkedBlockingQueue TimeUnit]
            [org.httpkit PrefixThreadFactory]))

(def all-tests      #{:simple :nested :chain :pool :multi})
(def default-tests  #{:simple :nested :chain :pool})
(def multi-count    5)
(def secret-url     "https://www.random.org/strings/")
(def test-url       "http://www.secureone.com")
(def timeout-short  3000)
(def timeout-quick  1000)
(def timeout-long   (+ timeout-short timeout-quick))
(def timeout-string "<timeout>")
(def secret-options {
  :query-params {
    :num        1
    :len        5
    :digits     "on"
    :upperalpha "on"
    :loweralpha "on"
    :unique     "on"
    :format     "plain"
    :rnd        "new"
    }})

(def my-thread-pool
  "Thread pool defined here using exactly the same code as http-kit
   (https://github.com/http-kit/http-kit/blob/master/src/org/httpkit/client.clj)
   initializes slightly different for some reason (version difference?)."
  (let [cpus    (.availableProcessors (Runtime/getRuntime))
        queue   (LinkedBlockingQueue.)
        factory (PrefixThreadFactory. "client-worker-")]
  (ThreadPoolExecutor. cpus cpus 60 TimeUnit/SECONDS queue factory)))

;;; Miscellaneous support functions

(defn- dump-stack-traces []
  (let [all-stack-traces (Thread/getAllStackTraces)]
    (doseq [thread (keys all-stack-traces)]
      (println "\n>>>" (.getId thread) ":" thread)
      (doseq [stack-frame (get all-stack-traces thread)]
        (println ">>>" (.toString stack-frame))))))

(defn- short-delay []
  (Thread/sleep timeout-quick))

;;; Output formatting functions

(defn- show-item [what item result]
  (printf "%-15s %-6s %5s%n" what item result))

(defn- show-pool
  ([]
    (show-pool "Default" http/default-pool))
  ([what pool]
   (println "------------- Thread Pool -------------------")
   (let [what (str what " pool")]
     (show-item what "core" (.getCorePoolSize pool))
     (show-item what "size" (.getPoolSize pool))
     (show-item what "high" (.getLargestPoolSize pool))
     (show-item what "max"  (.getMaximumPoolSize pool)))))

(defn- show-status [what promised]
  (let [status (deref promised timeout-long timeout-string)]
    (printf
      (if (integer? status) "%-15s status %5s%n" "%-15s status %5s%n")
      what status)))

(defn- show-thread [what]
  (printf "%-15s thread %5d%n" what (.getId (Thread/currentThread))))

;;; Simple callback

(defn- simple-callback
  "Simple callback hangs a single retry call and exits handler thread.
   The second call invokes this function with the retry flag set so
   the result is returned immediately.

   This models a callback due to a page redirect."
  [{:keys [error opts status]}]
  (show-thread "Simple callback")
  (when error
    (throw (Exception. error)))
  (let [retry (:retry opts)]
    (show-item "Simple" "flag" retry)
    (if retry
      (deliver (:promise opts) status)
      (http/get test-url (assoc opts :retry true) simple-callback))))

;;; Nested callback

(defn- get-secret-now
  "Synchronous acquisition of access key.
   Normally this will wait forever, but that would tie up the handler thread
   forever and ruin the rest of the tests so return `timeout-string` instead."
  []
  (let [response (deref (http/get secret-url secret-options)
                        timeout-short timeout-string)]
    (if (= timeout-string response)
      response
      (let [status (:status response)]
        (if (= 200 status)
          (trim (:body response))
          (throw (Exception. "Bad status getting secret" status)))))))

(defn- nested-callback
  "Nested callback attempts to make a call from within the handler.
   Since the handler is tying up the handler thread, the synchronous
   sub-call will only return if there is another thread waiting.

   This models an interim callback to negotiate access."
  [{:keys [error opts status]}]
  (show-thread "Nested callback")
  (when error
    (throw (Exception. error)))
  (let [retry  (:retry opts)
        secret (:secret opts)]
    (show-item "Nested" "retry"  retry)
    (show-item "Nested" "secret" secret)
    (if retry
      (deliver (:promise opts) status)
      (let [secret (get-secret-now)] ; get access key
        (show-item "Nested" "secret" secret)
        (if (= timeout-string secret)
          (deliver (:promise opts) 401) ; no access key, return 401
          (http/get test-url ; retry same as simple case
            (assoc opts :retry true :secret secret)
            nested-callback))))))

;;; Chained callback

(defn- chain-secret
  "Asynchronous acquisition of access key."
  [callback parent-opts]
  (let [options (merge secret-options {:parent-opts parent-opts})
        ;; For multi/my test keep using my worker pool from parent-opts
        options (if-let [pool (:worker-pool parent-opts)]
                  (merge options {:worker-pool pool})
                  options)]
    (http/get secret-url options callback)))

(defn- secret-callback
  "Secret callback handles response from access key query.
   At the end this callback re-invokes the initial call with the access key,
   passing along the original `chain-callback` handler."
  [chained-callback {:keys [body error opts status]}]
  (show-thread "Secret callback")
  (when error
    (throw (Exception. error)))
  (let [secret (trim body)]
    (show-item "Secret" "secret" secret)
    (when-not (= 200 status)
      (throw (Exception. "Bad status getting secret" status)))
    (let [parent-opts (:parent-opts opts)]
      (http/get (:url parent-opts)
        (assoc parent-opts :retry true :secret secret)
        chained-callback))))

(defn- chain-callback
  "Chained callback attempts to make an asynchronous call from within the handler.
   The handler thread is released and the callback for the nested call invokes
   this function again.

   This models an interim callback to negotiate access."
  [{:keys [error opts status]}]
  (show-thread "Chain callback")
  (when error
    (throw (Exception. error)))
  (let [retry  (:retry  opts)
        secret (:secret opts)]
    (show-item "Chain" "flag"   retry)
    (show-item "Chain" "secret" secret)
    (if retry
      (deliver (:promise opts) status)
      (chain-secret (partial secret-callback chain-callback) opts))))

;;; Main routine

(defn main [tests]
  (println "============= Starting ======================")
  (show-thread "Main")
  (show-item "Runtime" "cpus" (.availableProcessors (Runtime/getRuntime)))
  (show-pool)
  (show-pool "My" my-thread-pool)

  (when (contains? tests :simple)
    (println "------------- Simple Retry ------------------")
    (let [promised (promise)]
      (http/get test-url {:promise promised} simple-callback)
      (show-status "Simple" promised)))

  (when (contains? tests :nested)
    (println "------------- Nested Retry ------------------")
    (let [promised (promise)]
      (http/get test-url {:promise promised} nested-callback)
      (show-status "Nested" promised)
      (short-delay)))

  (when (contains? tests :chain)
    (println "------------- Chain Retry -------------------")
    (let [promised (promise)]
      (http/get test-url {:promise promised} chain-callback)
      (show-status "Chain" promised)))

  (show-pool)

  (when (contains? tests :multi)
    (println "------------- Multi Retry -------------------")
    (let [promises (repeatedly multi-count promise)]
      (doseq [promised promises]
        (http/get test-url {:promise promised} chain-callback))
      (doseq [promised promises]
        (show-status "Multi" promised)))
    (show-pool))

  (when (contains? tests :pool)
    (println "------------- Nested Retry My Pool ----------")
    (let [promised (promise)]
      (http/get test-url {
          :promise promised
          :worker-pool my-thread-pool}
        nested-callback)
      (show-status "Nested" promised)
      (show-pool "My" my-thread-pool)))

  (when (and (contains? tests :multi) (contains? tests :pool))
    (println "------------- Multi Retry My Pool -----------")
    (let [promises (repeatedly multi-count promise)]
      (doseq [promised promises]
        (http/get test-url {
          :promise promised
          :worker-pool my-thread-pool}
        chain-callback))
      (doseq [promised promises]
        (show-status "Multi" promised)))
    (show-pool "My" my-thread-pool))
  )

(defn -main [& args]
  (let [tests (into #{}(map keyword args))]
    (cond
      (some #(= :all %) tests)
        (main all-tests)
      (some #(contains? all-tests %) tests)
        (main tests)
      :default
        (main default-tests))))

