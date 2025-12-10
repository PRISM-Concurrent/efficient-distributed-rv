(ns typelin
  (:gen-class)
  (:require [clojure.pprint :as pp]
            [jitlin :as jl]
            [spec.queue :as q]
            [spec.set :as s]
            [spec.map :as m]
            [spec.deque :as dq]))

;; Tabla de especificaciones por tipo de estructura
(def specs
  {;; Cola FIFO estándar
   :queue {:init q/queue-init
           :step q/queue-step}

   ;; Deque (offerFirst/offerLast/pollFirst/pollLast)
   :deque {:init dq/deque-init
           :step dq/deque-step}

   ;; Conjunto
   :set   {:init s/set-init
           :step s/set-step}

   ;; Mapa
   :map   {:init m/map-init
           :step m/map-step}})

(defn linearizable?
  "Returns true if XE is linearizable w.r.t. the given spec-type.
   spec-type: :queue, :deque, :set, :map"
  [spec-type xe]
  ;;(println "==================================================")
  ;;(println ">>> linearizable? called with X_E of" (count xe) "events\n")
  ;;(println ">>> Spec type:" spec-type)
  ;;(println ">>> X_E received (RAW):")
  ;;(doseq [ev xe]
  ;;  (pp/pprint ev))
  ;;(println "--------------------------------------------------")

  (let [{:keys [ops-by-id]} (jl/build-ops xe)
        {:keys [preds]}     (jl/build-precedence-graph ops-by-id)
        {:keys [init step]} (get specs spec-type)]

    (when (nil? init)
      (throw (ex-info (str "Unknown spec-type: " spec-type)
                      {:spec-type spec-type})))

    (let [step-fn   (fn [state {:keys [op arg res]}]
                      (step state op arg res))
          init-state (init)
          result     (jl/check-linearizable-dfs ops-by-id preds init-state step-fn)]

    ;; (if result
    ;;    (println ">>> RESULT:  The history IS LINEARIZABLE.\n")
    ;;    (println ">>> RESULT:  The history is NOT linearizable.\n"))

      result)))