(ns logtAs
  (:gen-class)
  (:require [clojure.pprint :as pp]
                [spec.queue :as q])
  (:import [java.util ArrayList]))

;; ============================================================
;; Global state: Java ArrayList of Clojure vectors
;; ============================================================

(def logs-var
  (ArrayList.))

(defn init-logs!
  "Initialize logs-var to a Java ArrayList of nthreads empty vectors."
  [nthreads]
  (.clear logs-var)
  (dotimes [_ nthreads]
    (.add logs-var []))
  logs-var)

(defn write-log-tAs!
  "Append event to logs-var[tid].
   Each thread owns its own vector."
  [type tid op-id op arg count]
  (let [event {:type  type
               :op-id op-id
               :tid   tid
               :op    op
               :arg   arg
               :count count}
        old-vec (.get logs-var tid)
        new-vec (conj old-vec event)]
    (.set logs-var tid new-vec)
    nil))


;; ============================================================
;; Build XE (global ordering)
;; ============================================================

(defn build-xe
  "Builds a flattened, globally ordered execution (XE) from logs-var
   (a Java ArrayList of per-thread Clojure vectors).

   Returns a vector of maps:
     {:type :invoke :op-id ... :tid ... :op ... :arg ...}
     {:type :return :op-id ... :tid ... :res ...}."
  []
  (let [flat          (apply concat (seq logs-var))
        sorted-events (sort-by (juxt :count :tid) flat)]
    (mapv
     (fn [{:keys [type op-id tid op arg] :as ev}]
       (case type
         (:invoke :inv)
         {:type  :invoke
          :op-id op-id
          :tid   tid
          :op    op
          :arg   arg}

         (:return :ret)
         {:type  :return
          :op-id op-id
          :tid   tid
          :res   arg}

         ;; fallback (deja el evento intacto)
         ev))
     sorted-events)))
;; ============================================================
;; XE for JITLin
;; ============================================================
(defn normalize-null
  "Convert \"null\"-like values into Clojure nil."
  [v]
  (cond
    (nil? v) nil
    (= v :null) nil
    (and (string? v)
         (#{"null" "nil"} v)) nil
    :else v))

(defn xe-for-jit
  "Normalize XE produced by build-xe:
   - keeps all events
   - normalizes arg/res null-like values
   - leaves op names untouched"
  []
  (let [xe (build-xe)]
    (mapv
     (fn [{:keys [type op-id tid op arg res] :as ev}]
       (case type
         :invoke
         {:type  :invoke
          :op-id op-id
          :tid   tid
          :op    op
          :arg   (normalize-null arg)}

         :return
         {:type  :return
          :op-id op-id
          :tid   tid
          :res   (normalize-null res)}

         ev))
     xe)))