(ns spec.map
  (:require [clojure.string :as str]))

(defn map-init [] {})

;; ---------------- normalizaciones ----------------

(defn normalize-key
  "Normaliza claves:
   - nil → nil
   - string → trim
   - keyword → name
   - otro → tal cual"
  [k]
  (cond
    (nil? k)    nil
    (string? k) (str/trim k)
    (keyword? k) (name k)
    :else       k))

(defn normalize-val
  "Normaliza valores devueltos por el mapa:
   - nil → nil
   - \"null\" / \"nil\" → nil
   - string → trim
   - keyword → name
   - otro → tal cual"
  [v]
  (cond
    (nil? v) nil

    (string? v)
    (let [s (-> v str/trim)]
      (if (#{"null" "nil"} (str/lower-case s))
        nil
        s))

    (keyword? v)
    (let [s (name v)]
      (if (#{"null" "nil"} (str/lower-case s))
        nil
        s))

    :else v))

(defn parse-pair-string
  "Intenta parsear un string de la forma \"[k, v]\" o \"k, v\"
   a un par [k v]. Si no puede, lo usa todo como key y v=nil."
  [s]
  (let [s      (-> s str/trim)
        s      (cond
                 (and (.startsWith s "[") (.endsWith s "]"))
                 (subs s 1 (dec (count s)))
                 :else s)
        parts  (->> (str/split s #"," 2)
                    (map str/trim)
                    vec)]
    (if (= 2 (count parts))
      [(normalize-key (first parts))
       (normalize-val (second parts))]
      ;; fallback muy tolerante
      [(normalize-key s) nil])))

(defn normalize-kv-arg
  "Normaliza el arg de :put:
   - si es [k v] → [key-normalizada, val-normalizado]
   - si es string \"[k, v]\" → idem
   - otro tipo → [arg-normalizado, nil]"
  [arg]
  (cond
    (and (sequential? arg) (= 2 (count arg)))
    (let [[k v] arg]
      [(normalize-key k) (normalize-val v)])

    (string? arg)
    (parse-pair-string arg)

    :else
    [(normalize-key arg) nil]))

;; ---------------- especificación secuencial ----------------

(defn map-step
  "Especificación secuencial de un mapa estilo Java Map.

   q   : estado actual (map Clojure key→val)
   op  : :put / :get / :remove
   arg : para :put → [k v] o string \"[k, v]\"
         para :get/:remove → k
   res : resultado observado (valor anterior o nil)

   Devuelve:
   {:ok?  <bool>
    :res  <expected-result>
    :state <q'>}."
  [q op arg res]
  (let [res* (normalize-val res)]
    (case op

      ;; put(k,v) → oldVal (o nil si no había)
      :put
      (let [[k v] (normalize-kv-arg arg)
            old   (some-> (get q k) normalize-val)
            q'    (assoc q k v)]
        {:ok?  (= res* old)
         :res  old
         :state q'})

      ;; get(k) → currentVal (o nil si no hay)
      :get
      (let [k   (normalize-key arg)
            val (some-> (get q k) normalize-val)]
        {:ok?  (= res* val)
         :res  val
         :state q})

      ;; remove(k) → oldVal (o nil si no había)
      :remove
      (let [k   (normalize-key arg)
            old (some-> (get q k) normalize-val)
            q'  (dissoc q k)]
        {:ok?  (= res* old)
         :res  old
         :state q'})

      ;; Operación no soportada
      {:ok? false :res ::unsupported :state q})))