(ns datalog-rules.api
  "Utilities for managing Datomic Datalog rules in Clojure.

  A *ruleset* is a global (mutable) service upon which you can register rules (analogous to a multimethod).
  It's completely possible for an application to have only one ruleset.

  We distinguish between 2 sorts of rules:

  * *unirules*, which are meant only to encapsulate datalog clauses (analoguous to ordinary functions).
  You register a unirule simply by registering its body.
  * *plurirules*, which are polymorphic (analoguous to protocols).
  You register a plurirule by declaring its signature, then by registering implementations separately.
   When registering an implementation, you must always supply an implementation key.

  After having registered rules to a ruleset `rs`,
  you can obtain the Datalog data structure for these rules by calling `(rules rs)`.
  (This call will be memoized, so don't worry about the performance of compiling the list of rules.)

  The API is designed so that you can declare a ruleset as a global mutable object,
  then register rules from client code by mutating this object
  (in the spirit of Clojure's defmulti/defmethod).

  Optionally, you can configure a ruleset for *auto-reversion*.
  When the `:auto-reverse` option is `true`, *reversed rules* will be generated in addition to the original registered rules.
  A reversed rule has the same signature as the original rule,
  a name altered by appending '-' at the end, and its Datalog clauses are in the opposite order.
  This enables you to have Datalog clauses which make for the best performance in the current query,
  without duplicating rule definitions.")


;; CHECKLIST (Val, 24 Sep 2016)
;; 1. Performance impact of big ruleset -> seems ok!
;; 2. namespaced rules ? -> yeah, they work
;; 3. registering rules in a ruleset, via add and implement -> check
;; 4. reverse rules -> check
;; 5. ensuring reloading of rules works well -> check
;; * TODO tree shaking (Val, 25 Sep 2016)
;; 6. write tests
;; 7. write docs

;; DESIGN NOTES
;; - (default) options to register in implicit ruleset ? May ve less tedious for most apps.
;; - store vs StoreRuleset is a bit ugly, may need some refactoring.
;; possible enhancements:
;; * static analysis of rules (check for dependencies)

(defn rule-head [rule] (first rule))
(defn rule-name [rule] (ffirst rule))
(defn rule-body [rule] (rest rule))

(defn reversed [rule rev-suffix]
  (let [rname (rule-name rule)
        rev-name (symbol (namespace rname) (str (name rname) rev-suffix))]
    (into [(cons rev-name (rest (rule-head rule)))]
      (reverse (rule-body rule)))))

(defn add-unirule
  [store doc-string rule]
  (let [sig (rule-head rule)
        rname (rule-name rule)]
    (-> store
      (assoc-in [:headers rname] {:sig sig :doc doc-string :type :simple})
      (assoc-in [:uni-impl rname] rule))))

;; NOTE except for documentation, add-multi-rule is basically a noop currently (Val, 24 Sep 2016)
(defn add-plurirule
  [store doc-string sig]
  (let [rname (first sig)]
    (-> store
      (assoc-in [:headers rname] {:sig sig :doc doc-string :type :multi})
      )))

(defn add-pluriimpl
  [store impl-key rule]
  ;; TODO signature check? (Val, 26 Sep 2016)
  (-> store
    (assoc-in [:pluri-impl (rule-name rule) impl-key] rule)
    ))

(defn store-rules [store]
  (let [{:keys [auto-reverse rev-suffix]
         :or {auto-reverse false
              rev-suffix "-"}} store]
    (as-> [] rs
      (into rs (-> store :uni-impl vals))
      (into rs (mapcat vals) (-> store :pluri-impl vals))
      (if auto-reverse
        (into rs (map #(reversed % rev-suffix)) rs)
        rs))))

(defrecord RulesetV
  [store rules-fn])

(defn- make-rulesetv [store]
  (->RulesetV store
    (memoize #(store-rules store))
    ))

(defn ruleset
  "Creates a ruleset upon which rules can be registered."
  [{:as opts,
    :keys [auto-reverse rev-suffix]}]
  (let [store opts]
    (atom (make-rulesetv store))))

(defn- update-rs [rs f-store & args]
  (swap! rs (fn [{:as rsv, :keys [store]}]
              (make-rulesetv (apply f-store store args))
              )))

(defn unirule
  [rs doc-string rule]
  (update-rs rs add-unirule doc-string rule))

(defn plurirule
  [rs doc-string sig]
  (update-rs rs add-plurirule doc-string sig))

(defn pluriimpl
  [rs impl-key rule]
  (update-rs rs add-pluriimpl impl-key rule))

(defn rules [rs]
  ((-> @rs :rules-fn)))

(defn rule-doc-str [rs rname]
  (when-let [h (-> @rs :store :headers (get rname))]
    (str "-------------------------\n"
      (pr-str (:sig h)) "\n\n" (:doc h))))

#?(:clj
(defmacro rule-doc [rs rname]
  `(println (rule-doc-str ~rs (quote ~rname))))
)

(defn rule-source* [rs rname]
  (into []
    (filter (fn [rule]
              (= (rule-name rule) rname)))
    (rules rs)))

#?(:clj
(defmacro rule-source [rs rname]
  `(rule-source* ~rs (quote ~rname)))
)
