(ns logrAw
  (:gen-class)
  (:require [clojure.set :as set]
            [clojure.pprint :as pp])
  (:import [java.util ArrayList]))

;; ============================================================
;; Global state: dos listas Java de vectores de Clojure
;; ============================================================

(def invs-var
  "Java ArrayList: en la posición tid está un vector de invocaciones del hilo tid."
  (ArrayList.))

(def returns-var
  "Java ArrayList: en la posición tid está un vector de respuestas del hilo tid."
  (ArrayList.))

(defn init-logs!
  "Inicializa invs-var y returns-var con nthreads vectores vacíos."
  [nthreads]
  (.clear invs-var)
  (.clear returns-var)
  (dotimes [_ nthreads]
    (.add invs-var [])
    (.add returns-var []))
  nil)

;; ============================================================
;; 1. Escribir invocaciones y respuestas
;; ============================================================

(defn log-invoke!
  "Append INVOCATION event to invs-var[tid].
   Estructura:
   {:type :invoke, :op-id ..., :tid ..., :op ..., :arg ...}"
  [tid op-id op arg]
  (let [event   {:type  :invoke
                 :op-id op-id
                 :tid   tid
                 :op    op
                 :arg   arg}
        old-vec (.get invs-var tid)
        new-vec (conj old-vec event)]
    (.set invs-var tid new-vec)
    nil))

(defn log-return!
  "Append RESPONSE event to returns-var[tid].
   Toma automáticamente un snapshot de TODAS las invocaciones en invs-var
   y lo guarda en :view.

   Estructura:
   {:type :return, :op-id ..., :tid ..., :res ..., :view snapshot}"
  [tid op-id res]
  ;; snapshot = copia inmutable de las invocaciones de todos los hilos
  (let [snapshot (->> (seq invs-var)
                      (mapv vec))
        event    {:type :return
                  :op-id op-id
                  :tid   tid
                  :res   res
                  :view  snapshot}
        old-vec  (.get returns-var tid)
        new-vec  (conj old-vec event)]
    (.set returns-var tid new-vec)
    nil))
    
;; ============================================================
;; 2. Helpers para agrupar por op-id
;; ============================================================

(defn all-invs
  "Devuelve una secuencia de TODAS las invocaciones (events) en todos los hilos."
  []
  (mapcat identity (seq invs-var)))

(defn all-returns
  "Devuelve una secuencia de TODAS las respuestas (events) en todos los hilos."
  []
  (mapcat identity (seq returns-var)))

(defn inv-by-op-id
  "Mapea op-id ↦ evento de invocación (asumimos un inv por op-id)."
  []
  (into {}
        (for [ev (all-invs)]
          [(:op-id ev) ev])))
(defn ret-info
  "Mapea op-id ↦ {:ret <evento-return> :inv-set #{op-ids vistos en :view}}.

   Soporta dos formatos de :view:
   1) Mapa: {op-id op-id, ...}
   2) Vector de vectores de invocaciones: [[{:op-id ...} ...] [...]]"
  []
  (into {}
        (for [ret-ev (all-returns)
              :let [op-id (:op-id ret-ev)
                    view  (:view ret-ev)
                    inv-set
                    (cond
                      ;; Caso 1: mapa {op-id op-id}
                      (map? view)
                      (set (keys view))

                      ;; Caso 2: vector de vectores de eventos
                      (sequential? view)
                      (->> view
                           (mapcat identity)
                           (map :op-id)
                           set)

                      :else
                      #{})]]
          [op-id {:ret     ret-ev
                  :inv-set inv-set}])))
                  
(defn build-edges-as-events
  "Construye un *vector* de aristas, donde cada arista es
   [evento-desde evento-hasta].

   - (a) invX → retX para cada op-id completo.
   - (b) ret_i → inv_j si view(i) ⊂ view(j), view(j) ≠ view(i)
         y op-id de inv_j NO está en view(i).
   - (c) inv_k → ret_i si el op-id k aparece en la vista (:inv-set) de ret_i."
  []
  (let [inv-map (inv-by-op-id)   ;; op-id ↦ evento de invocación
        ret-map (ret-info)]      ;; op-id ↦ {:ret ret-event :inv-set #{...}}

    (let [;; (a) invX -> retX
          edges-own
          (for [[op-id inv-ev] inv-map
                :let [{:keys [ret]} (ret-map op-id)]
                :when ret]
            [inv-ev ret])

          ;; (b) ret_i -> inv_j usando containment de vistas
          edges-ac
          (for [[id-i info-i] ret-map
                [id-j info-j] ret-map
                :when (not= id-i id-j)
                :let [Si    (:inv-set info-i)
                      Sj    (:inv-set info-j)
                      inv-j (inv-map id-j)]
                :when (and (set/subset? Si Sj)
                           (not (set/subset? Sj Si))
                           (not (contains? Si id-j))
                           inv-j)]
            [(:ret info-i) inv-j])

          ;; (c) inv_k -> ret_i si k está en la vista de ret_i
          edges-view
          (for [[id-i {:keys [ret inv-set]}] ret-map
                op-id-k inv-set
                :let [inv-k (inv-map op-id-k)]
                ;; opcional: evitar duplicar la arista propia invX->retX
                :when (and inv-k (not= op-id-k id-i))]
            [inv-k ret])]

      ;; devolvemos todo como vector (no set) para conservar orden;
      ;; topo-order-events ya aplica (distinct) a las aristas
      (vec (concat edges-own edges-ac edges-view)))))

;; ============================================================
;; 4. Orden topológico sobre eventos
;; ============================================================

(defn topo-order-events
  "Recibe un vector de aristas `edges`, donde cada arista es
   [evento-desde evento-hasta].

   Regresa un vector con los eventos en un orden topológico
   compatible con todas las aristas.

   Si hay un ciclo, imprime un warning y regresa el orden parcial."
  [edges]
  (let [edges (distinct edges)

        ;; conjunto de nodos = todos los eventos que aparecen en edges
        nodes (->> edges
                   (mapcat identity)
                   set)

        ;; sucesores y predecesores por nodo
        succs (reduce (fn [m [u v]]
                        (update m u (fnil conj #{}) v))
                      (zipmap nodes (repeat #{}))
                      edges)

        preds (reduce (fn [m [u v]]
                        (update m v (fnil conj #{}) u))
                      (zipmap nodes (repeat #{}))
                      edges)

        ;; cola de nodos listos (sin predecesores)
        init-q (into clojure.lang.PersistentQueue/EMPTY
                     (filter #(empty? (get preds %)) nodes))]

    (loop [q     init-q
           preds preds
           order []]
      (if (empty? q)
        ;; ya no hay nodos listos
        (if (= (count order) (count nodes))
          ;; todo bien
          (vec order)
          ;; ciclo: algunos nodos nunca quedaron sin predecesores
          (do
            (println "WARNING: ciclo detectado en las aristas; devolviendo orden parcial de"
                     (count order) "eventos de" (count nodes))
            (vec order)))
        ;; hay al menos un nodo listo
        (let [n   (peek q)
              q1  (pop q)
              ns  (get succs n)]
          ;; para cada sucesor, borramos n de sus predecesores
          (let [[preds' q']
                (reduce
                 (fn [[preds* q*] m]
                   (let [ps    (disj (get preds* m) n)
                         preds*' (assoc preds* m ps)
                         q*'   (if (empty? ps)
                                 (conj q* m)
                                 q*)]
                     [preds*' q*']))
                 [preds q1]
                 ns)]
            (recur q' preds' (conj order n))))))))


;; ============================================================
;; 6. XE for JITLin (flattened, clean events)
;; ============================================================
(defn xe-for-jit
  "Convierte X_E (vector de eventos, típicamente salido de topo-order-events)
   a un formato plano para el verificador:
   - Mantiene :op tal cual viene de Java (sin normalizar nombres).
   - Elimina el campo :view de los eventos de tipo :return.
   - Conserva :type, :op-id, :tid, :arg, :res."
  [xe]
  (mapv
   (fn [{:keys [type op-id tid op arg res] :as ev}]
     (case type
       :invoke
       {:type  :invoke
        :op-id op-id
        :tid   tid
        :op    op
        :arg   arg}

       :return
       {:type  :return
        :op-id op-id
        :tid   tid
        :res   res}

       ;; fallback (por si hubiera algún tipo raro)
       ev))
   xe))

(defn xe-for-jit-from-logs []
  (-> (build-edges-as-events)
      (topo-order-events)
      (xe-for-jit)))
      
;; (defn xe-for-jit-from-logs []
;;   ;; ============================================================
;;   ;; 1. PRINT invs-var y returns-var
;;   ;; ============================================================
;;   (println "\n=== invs-var ===")
;;   (doseq [i (range (.size invs-var))]
;;     (println "tid" i ":" (.get invs-var i)))

;;   (println "\n=== returns-var ===")
;;   (doseq [i (range (.size returns-var))]
;;     (println "tid" i ":" (.get returns-var i)))


;;   ;; ============================================================
;;   ;; 2. Build edges
;;   ;; ============================================================
;;   (let [edges (build-edges-as-events)]
;;     (println "\n=== EDGES ===")
;;     (doseq [e edges] (println e))

;;     ;; ============================================================
;;     ;; 3. Topological order
;;     ;; ============================================================
;;     (let [topo (topo-order-events edges)]
;;       (println "\n=== TOPO X_E ===")
;;       (doseq [ev topo] (println ev))

;;       ;; ============================================================
;;       ;; 4. Normalize to JIT format
;;       ;; ============================================================
;;       (let [xe (xe-for-jit topo)]
;;         (println "\n=== XE for JIT ===")
;;         (doseq [ev xe] (println ev))
;;         xe))))