(ns spec.priorityqueue
(:require [clojure.string :as str]
            [spec.queue :as qspec]))
            
(defn pqueue-init [] [])

(def normalize-nil qspec/normalize-nil)
(def normalize-bool qspec/normalize-bool)

(def queue-op-kind qspec/queue-op-kind) ;; si quieres la misma normalización

(defn insert-sorted [v x]
  (let [idx (->> v
                 (map-indexed vector)
                 (some (fn [[i e]]
                         (when (pos? (compare e x))
                           i))))]
    (if (nil? idx)
      (conj v x)
      (into (subvec v 0 idx)
            (cons x (subvec v idx))))))

(defn pqueue-step
  [q op arg res]
  (let [kind (queue-op-kind op)]
    (case kind

      :enqueue
      (let [res*     (normalize-bool res)
            success? true
            q'       (insert-sorted q arg)]
        {:ok?  (= res* success?)
         :res  success?
         :state q'})

      :dequeue
      (let [res* (normalize-nil res)]
        (if (seq q)
          (let [r  (first q)
                q' (vec (rest q))]
            {:ok?  (= res* r)
             :res  r
             :state q'})
          {:ok?  (= res* nil)
           :res  nil
           :state q}))

      {:ok? false :res ::unsupported :state q})))