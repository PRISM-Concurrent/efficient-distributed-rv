(ns spec.queue
  (:require [clojure.string :as str]))

(defn queue-init [] [])

(defn normalize-nil
  [v]
  (cond
    (nil? v) nil
    (and (string? v)
         (#{"nil" "null"} (-> v str/trim str/lower-case))) nil
    (and (keyword? v)
         (#{"nil" "null"} (-> v name str/lower-case))) nil
    :else v))

(defn normalize-bool
  [v]
  (cond
    (true? v) true
    (false? v) false
    (string? v)
    (let [s (-> v str/trim str/lower-case)]
      (cond
        (= s "true")  true
        (= s "false") false
        :else v))
    (keyword? v)
    (let [s (-> v name str/lower-case)]
      (cond
        (= s "true")  true
        (= s "false") false
        :else v))
    :else v))

(defn queue-op-kind
  "Dado un nombre de operación tal como llega de Java,
   lo mapea a :enqueue / :dequeue o nil si no aplica."
  [op]
  (case op
    ;; enqueue-like (offer, add, put en colas tipo blocking)
    :enqueue           :enqueue
    :offer             :enqueue
    :add               :enqueue

    ;; dequeue-like (poll, remove)
    :dequeue           :dequeue
    :poll              :dequeue
    :remove            :dequeue

    ;; por ahora no soportamos más (take, etc.)
    nil))

(defn queue-step
  "Especificación secuencial de una cola (FIFO).

   q   : estado actual (vector)
   op  : nombre ‘bruto’ de la operación (:offer, :poll, :add, ...)
   arg : argumento (para enqueue/offer/add) — lo dejamos tal cual (p.ej. [3])
   res : resultado observado (para poll/remove)

   Devuelve:
   {:ok?  bool
    :res  resultado esperado
    :state nuevo-estado}."
  [q op arg res]
  (let [kind (queue-op-kind op)]
    (case kind

      ;; ===================== ENQUEUE =====================
      :enqueue
      (let [res*     (normalize-bool res)
            success? true
            ;; Importante: guardamos arg TAL CUAL (p.ej. [3])
            q'       (if success? (conj q arg) q)]
        {:ok?  (= res* success?)
         :res  success?
         :state q'})

      ;; ===================== DEQUEUE =====================
      :dequeue
      (let [res-norm (normalize-nil res)]
        (if (seq q)
          ;; Cola NO vacía
          (let [r       (first q)      ;; lo que hay en la cabeza, p.ej. [3] o 3
                q'      (vec (rest q))
                ;; Ajuste clave: comparamos tanto con res como con [res]
                ;; para aceptar 3 vs [3] y [3] vs 3.
                res-as-vec [res-norm]
                ok?     (or (= res-norm r)
                            (= res-as-vec r))]
            {:ok?  ok?
             :res  r         ;; valor “esperado” según la cola
             :state q'})
          ;; Cola vacía: esperamos nil (sin corchetes)
          (let [ok? (= res-norm nil)]
            {:ok?  ok?
             :res  nil
             :state q})))

      ;; ===================== OPERACIÓN NO SOPORTADA =====
      {:ok? false :res ::unsupported :state q})))