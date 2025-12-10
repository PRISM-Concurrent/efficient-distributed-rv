(ns spec.set
  (:require [clojure.string :as str]))

;; Estado inicial: conjunto vacío
(defn set-init [] #{})

(defn normalize-bool
  "Normaliza valores booleanos tipo Set/Java:
   - true / false → se quedan igual
   - nil, \"nil\", \"null\", :nil, :null → false
   - \"true\" / \"false\" → true / false
   - :true / :false → true / false
   Cualquier otra cosa → se devuelve tal cual (para debug)."
  [v]
  (cond
    ;; casos explícitos
    (true? v)  true
    (false? v) false

    ;; nil-like → interpretamos como false
    (nil? v)   false

    (string? v)
    (let [s (-> v str/trim str/lower-case)]
      (cond
        (#{"nil" "null"} s) false
        (= s "true")        true
        (= s "false")       false
        :else               v))

    (keyword? v)
    (let [s (-> v name str/lower-case)]
      (cond
        (#{"nil" "null"} s) false
        (= s "true")        true
        (= s "false")       false
        :else               v))

    :else v))

(defn set-step
  "Especificación secuencial de un conjunto estilo Java Set.

   q   : estado actual (set)
   op  : :add / :remove / :contains
   arg : elemento (para add/remove/contains)
   res : resultado observado (boolean-like)

   Devuelve:
   {:ok?  <bool>   ; si res coincide con la especificación
    :res  <value>  ; resultado esperado según la especificación
    :state <q'>}   ; siguiente estado del conjunto."
  [q op arg res]
  (let [res* (normalize-bool res)]
    (case op

      ;; add(x): true si x no estaba, false si ya estaba
      :add
      (let [present? (contains? q arg)
            success? (not present?)
            q'       (if success? (conj q arg) q)]
        {:ok?  (= res* success?)
         :res  success?
         :state q'})

      ;; remove(x): true si x estaba, false si no
      :remove
      (let [present? (contains? q arg)
            success? present?
            q'       (if success? (disj q arg) q)]
        {:ok?  (= res* success?)
         :res  success?
         :state q'})

      ;; contains(x): true si x ∈ q, false en otro caso
      :contains
      (let [present? (contains? q arg)]
        {:ok?  (= res* present?)
         :res  present?
         :state q})

      ;; Operación no soportada
      {:ok? false :res ::unsupported :state q})))