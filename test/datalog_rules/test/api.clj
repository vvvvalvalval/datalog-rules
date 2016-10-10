(ns datalog-rules.test.api
  (:require [clojure.test :refer :all]
            [datalog-rules.api :as dr]))

(deftest reversed-example
  (is (=
        (dr/reversed '[(some.ns/rule1 ?b ?ax)
                       [?b :b/a ?a]
                       [?a :a/x ?ax]] "-")
        '[(some.ns/rule1- ?b ?ax)
          [?a :a/x ?ax]
          [?b :b/a ?a]]
        )))

(defn register-fixture-ruleset
  [rs]
  (dr/unirule rs "rule 1 doc"
    '[(some.ns/rule1 ?b ?ax)
      [?b :b/a ?a]
      [?a :a/x ?ax]])

  (dr/plurirule rs "rule 2 doc"
    '(some.ns/multi-rule ?x ?y))

  (dr/pluriimpl rs
    :a
    '[(some.ns/multi-rule ?x ?y)
      [?x :a/x ?y]])

  (dr/pluriimpl rs
    :b
    '[(some.ns/multi-rule ?x ?y)
      [?x :b/y ?y]]))

(deftest rules-example
  (let [rs (dr/ruleset {:auto-reverse true})]
    (register-fixture-ruleset rs)

    (is (= (dr/rules rs)
          '[[(some.ns/rule1 ?b ?ax) [?b :b/a ?a] [?a :a/x ?ax]]
            [(some.ns/multi-rule ?x ?y) [?x :a/x ?y]]
            [(some.ns/multi-rule ?x ?y) [?x :b/y ?y]]
            [(some.ns/rule1- ?b ?ax) [?a :a/x ?ax] [?b :b/a ?a]]
            [(some.ns/multi-rule- ?x ?y) [?x :a/x ?y]]
            [(some.ns/multi-rule- ?x ?y) [?x :b/y ?y]]]
          ))
    ))

(deftest doc-example
  (let [rs (dr/ruleset {:auto-reverse true})]
    (register-fixture-ruleset rs)

    (is (= (dr/rule-doc-str rs 'some.ns/rule1)
          "-------------------------\n(some.ns/rule1 ?b ?ax)\n\nrule 1 doc"))
    (is (= (dr/rule-doc-str rs 'some.ns/multi-rule)
          "-------------------------\n(some.ns/multi-rule ?x ?y)\n\nrule 2 doc"))
    ))

(deftest source-example
  (let [rs (dr/ruleset {:auto-reverse true})]
    (register-fixture-ruleset rs)

    (is (= (dr/rule-source* rs 'some.ns/rule1)
          '[[(some.ns/rule1 ?b ?ax)
             [?b :b/a ?a]
             [?a :a/x ?ax]]]))
    (is (= (dr/rule-source* rs 'some.ns/multi-rule)
          '[[(some.ns/multi-rule ?x ?y)
             [?x :a/x ?y]]
            [(some.ns/multi-rule ?x ?y)
             [?x :b/y ?y]]]))
    ))

;; ------------------------------------------------------------------------
;; TODO test with Datomic (Val, 10 Oct 2016)

(comment

  (def conn (let [uri "datomic:mem://datomic-rules-dev"]
              (d/create-database uri)
              (d/connect uri)))

  (def schema
    [(du/field :a/id :string "" {:db/unique :db.unique/identity})
     (du/field :a/x :string "")
     (du/field :b/id :string "" {:db/unique :db.unique/identity})
     (du/to-one :b/a "")
     (du/field :b/y :string "")])

  @(d/transact conn schema)

  @(d/transact conn
     [{:db/id (d/tempid :db.part/user -1)
       :a/id "a0"
       :a/x "x0"}
      {:db/id (d/tempid :db.part/user -2)
       :a/id "a1"
       :a/x "x1"}
      {:db/id (d/tempid :db.part/user -3)
       :b/id "b0"
       :b/y "y0"
       :b/a (d/tempid :db.part/user -1)}
      {:db/id (d/tempid :db.part/user -4)
       :b/id "b1"
       :b/y "y1"
       :b/a (d/tempid :db.part/user -1)}
      {:db/id (d/tempid :db.part/user -5)
       :b/id "b2"
       :b/y "y2"
       :b/a (d/tempid :db.part/user -2)}])


  (def r1 '[[(some.ns/rule1 ?b ?ax)
             [?b :b/a ?a]
             [?a :a/x ?ax]]])

  (def r2 "a big ruleset"
    (into r1 (repeatedly 1000 (fn []
                                [(list (gensym "arule") '?b '?ax)
                                 '[?b :b/a ?a]
                                 '[?a :a/x ?ax]]))))

  (bench/bench
    (d/q '[:find ?ax :in % $ ?b :where
           (some.ns/rule1 ?b ?ax)]
      r1 (d/db conn) [:b/id "b0"]))

  (bench/bench
    (d/q '[:find ?ax :in % $ ?b :where
           (some.ns/rule1 ?b ?ax)]
      r2 (d/db conn) [:b/id "b0"]))

  )
