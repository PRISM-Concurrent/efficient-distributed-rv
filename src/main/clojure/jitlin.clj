(ns jitlin
  (:require [clojure.set :as set]))

;; ------------------------------------------------------------
;; 1) Event model (ya parseado)
;; ------------------------------------------------------------
;; {:type  :invoke/:return
;;  :op-id <unique id>
;;  :tid   <thread id>
;;  :op    <operation>
;;  :arg   <argument>
;;  :res   <result>}

;; ------------------------------------------------------------
;; 2) Build operations (invoke + return)
;; ------------------------------------------------------------
(defn build-ops
  [xe]
  (let [by-id (reduce-kv
               (fn [m i {:keys [type op-id tid op arg res] :as ev}]
                 (update m op-id
                         (fn [o]
                           (case type
                             :invoke (-> (or o {})
                                         (assoc :id    op-id
                                                :tid   tid
                                                :op    op
                                                :arg   arg
                                                :i-inv i))
                             :return (-> (or o {})
                                         (assoc :id    op-id
                                                :tid   tid
                                                :res   res
                                                :i-ret i))
                             o))))
               {}
               xe)
        complete-ids (->> by-id
                          (filter (fn [[_ {:keys [i-inv i-ret]}]]
                                    (and (some? i-inv) (some? i-ret))))
                          (map first)
                          set)]
    {:ops-by-id    (select-keys by-id complete-ids)
     :complete-ids complete-ids}))

(defn build-precedence-graph
  [ops-by-id]
  (let [ids   (keys ops-by-id)
        pairs (for [a-id ids
                    b-id ids
                    :when (not= a-id b-id)
                    :let [{a-ret :i-ret} (ops-by-id a-id)
                          {b-inv :i-inv} (ops-by-id b-id)]
                    :when (< a-ret b-inv)]
                [a-id b-id])
        succs (reduce (fn [m [a b]] (update m a (fnil conj #{}) b))
                      (zipmap ids (repeat #{}))
                      pairs)
        preds (reduce (fn [m [a b]] (update m b (fnil conj #{}) a))
                      (zipmap ids (repeat #{}))
                      pairs)]
    {:nodes (set ids)
     :preds preds
     :succs succs}))

(defn ready-ops [preds done]
  (set (for [[id ps] preds
             :when (and (not (done id))
                        (set/subset? ps done))]
         id)))

(defn check-linearizable-dfs
  [ops-by-id preds init-state step-fn & {:keys [limit]}]
  (letfn [(dfs [state done]
            (let [n-ops (count ops-by-id)]
              (cond
                (= (count done) n-ops) true
                (and limit (> (count done) limit)) false
                :else
                (let [cands (ready-ops preds done)]
                  (if (empty? cands)
                    false
                    (some
                     (fn [op-id]
                       (let [op  (ops-by-id op-id)
                             res (step-fn state op)]
                         (when (:ok? res)
                           (dfs (:state res) (conj done op-id)))))
                     cands))))))]
    (dfs init-state #{})))