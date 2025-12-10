(ns spec.deque
  (:require [spec.queue :as qspec]))

;; Estado inicial: deque vacía (vector [front ... back])
(defn deque-init [] [])

(def normalize-nil  qspec/normalize-nil)
(def normalize-bool qspec/normalize-bool)

;; ------------------------------------------------------------
;; Clasificación de operaciones de deque
;; ------------------------------------------------------------
(defn deque-op-kind
  "Clasifica el nombre bruto de la operación (keyword) en:
   - :enqueue-front / :enqueue-back
   - :dequeue-front / :dequeue-back
   - nil si no se reconoce."
  [op]
  (case op
    ;; Enqueue “normales” → al final (back)
    :enqueue     :enqueue-back
    :offer       :enqueue-back
    :add         :enqueue-back

    ;; Deque específicas (enqueue)
    :offerFirst  :enqueue-front
    :offerLast   :enqueue-back

    ;; Dequeue “normales” → desde el frente
    :dequeue     :dequeue-front
    :poll        :dequeue-front
    :remove      :dequeue-front

    ;; Deque específicas (dequeue)
    :pollFirst   :dequeue-front
    :pollLast    :dequeue-back

    ;; no reconocida
    nil))

;; Helpers para sacar frente / fondo de un vector
(defn- pop-front [v]
  (let [v (vec v)]
    (if (seq v)
      {:elem (first v)
       :rest (subvec v 1)}
      {:elem nil
       :rest v})))

(defn- pop-back [v]
  (let [v (vec v)]
    (if (seq v)
      (let [n (dec (count v))]
        {:elem (nth v n)
         :rest (subvec v 0 n)})
      {:elem nil
       :rest v})))

(defn deque-step
  "Especificación secuencial de una DEQUE:

   - offerFirst(x), offerLast(x)  → encolan en frente / fondo y devuelven true
   - pollFirst(),  pollLast()     → devuelven elemento en frente / fondo o nil"
  [q op arg res]
  (let [kind (deque-op-kind op)]
    (case kind

      ;; Enqueue al frente
      :enqueue-front
      (let [res*     (normalize-bool res)
            success? true
            q'       (into [arg] q)]      ;; arg :: frente
        {:ok?  (= res* success?)
         :res  success?
         :state q'})

      ;; Enqueue al fondo
      :enqueue-back
      (let [res*     (normalize-bool res)
            success? true
            q'       (conj q arg)]        ;; arg :: fondo
        {:ok?  (= res* success?)
         :res  success?
         :state q'})

      ;; Dequeue desde el frente
      :dequeue-front
      (let [res* (normalize-nil res)
            {:keys [elem rest]} (pop-front q)]
        (if (some? elem)
          {:ok?  (= res* elem)
           :res  elem
           :state rest}
          {:ok?  (= res* nil)
           :res  nil
           :state q}))

      ;; Dequeue desde el fondo
      :dequeue-back
      (let [res* (normalize-nil res)
            {:keys [elem rest]} (pop-back q)]
        (if (some? elem)
          {:ok?  (= res* elem)
           :res  elem
           :state rest}
          {:ok?  (= res* nil)
           :res  nil
           :state q}))

      ;; Operación no soportada
      {:ok? false :res ::unsupported :state q})))