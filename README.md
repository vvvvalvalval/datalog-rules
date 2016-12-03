# datalog-rules

> Yeah it does!
> -- Anonymous Datomic user

Utilities for managing [Datalog](http://docs.datomic.com/query.html) rulesets from Clojure.

[![Clojars Project](https://img.shields.io/clojars/v/vvvvalvalval/datalog-rules.svg)](https://clojars.org/vvvvalvalval/datalog-rules)

By Datalog, we mean the rules language exposed by Datomic and Datascript 
 (because of the data-orientation of Datalog, this library doesn't have any code dependency to either of those).

Project status: alpha, subject to breaking changes.

## Rationale

Rules are a powerful abstraction mechanism in the logic language that is Datalog,
 just like functions are a powerful abstraction mechanism in the procedural language that is Clojure.

However, Datalog rules come with limited infrastructure, which leaves much to be desired in a Clojure environment:

* As of Datomic 0.9.5530, Datalog queries accept only one ruleset input,
which pressures you towards centralizing all your rules in one place. This can be bad for code cohesion.
* For 'polymorphic' rules, this is even worse, as there is no way to separate interface declaration from implementation.
* No query-able documentation.
* In order to achieve performance, the same rule sometimes needs to be declared in 2 different orders, which leads to code duplication.

This library provides utilities to address these issues.
It does so by providing global rules registries, called *rulesets*, upon which you can register rules from various places
in your code, in a way that is friendly to interactive development.

## Usage

### TL;DR

Here's a complete usage example; see below for a detailed walkthrough.

```clojure
(require '[datalog-rules.api :as dr])

(def my-ruleset (dr/ruleset {}))

;; registering rules
(dr/unirule my-ruleset
  "Matches iff `?person` lives in `?country`"
  '[(in-country ?person ?country)
    [?person :person/address ?a]
    [?a :address/town ?t]
    [?t :town/country ?country]])
    
(dr/plurirule my-ruleset 
  "Matches iff ?person said 'hello' in her native language"
  '(said-hello ?person))
(dr/pluriimpl my-ruleset :english-hi
  '[(said-hello ?person)
    [?person :speaks :english]
    [?person :said "hi"]])
(dr/pluriimpl my-ruleset :french-hi
  '[(said-hello ?person)
    [?person :speaks :french]
    [?person :said "salut"]])

;; retrieving the rules, for use in query.
(dr/rules my-ruleset)
=> [[(in-country ?person ?country)
     [?person :person/address ?a]
     [?a :address/town ?t]
     [?t :town/country ?country]]
    [(said-hello ?person)
     [?person :speaks :english]
     [?person :said "hi"]]
    [(said-hello ?person)
     [?person :speaks :french]
     [?person :said "salut"]]]
```

### Declaring a ruleset

First, you need to declare a *ruleset*, 
 which is a store to which you will be able to register rules:

```clojure
(require '[datalog-rules.api :as dr])

;; creating a ruleset (with default options)
(def my-ruleset (dr/ruleset {}))
```

### Registering rules

Then you can register Datalog rules to that ruleset.
 `datalog-rules` makes an explicit distinction between 2 sorts of rules:

 * *unirules*, for which only 1 implementation is registered (analoguous to Clojure functions)
 * *plurirules*, for which several named implementations are registered (analogous to Clojure multimethods) 
 
```clojure
;; registering a unirule:
(dr/unirule my-ruleset
  "Matches iff `?person` lives in `?country`"
  '[(in-country ?person ?country)
    [?person :person/address ?a]
    [?a :address/town ?t]
    [?t :town/country ?country]])

;; registering a plurirule
; declaring the interface...
(dr/plurirule my-ruleset 
  "Matches iff ?person said 'hello' in her native language"
  '(said-hello ?person))
; ... then registering implementations:
(dr/pluriimpl my-ruleset :english-hi
  '[(said-hello ?person)
    [?person :speaks :english]
    [?person :said "hi"]])
(dr/pluriimpl my-ruleset :french-hi
  '[(said-hello ?person)
    [?person :speaks :french]
    [?person :said "salut"]])
```

The facts that a unirule has only 1 implementation, and that plurirule implementations are named, 
 allow `unirule`, `plurirule` and `pluriimpl` calls to be idempotent - thus more friendly to interactive development.  

### Using rules in query

After having registered the rules, you can retrieve by calling `dr/rules`: 

```clojure
(dr/rules my-ruleset)
=> [[(in-country ?person ?country)
     [?person :person/address ?a]
     [?a :address/town ?t]
     [?t :town/country ?country]]
    [(said-hello ?person)
     [?person :speaks :english]
     [?person :said "hi"]]
    [(said-hello ?person)
     [?person :speaks :french]
     [?person :said "salut"]]]
```

You can them readily use them in query:
     
```clojure
(require '[datomic.api :as d])

(defn who-said-hello-in-america [db]
  (d/q '[:find [?person ...] :in % $ :where 
         (in-country ?person :usa)
         (said-hello ?person)]
    (dr/rules my-ruleset) db))
```

`dr/rules` caches its result such that subsequent calls on the same ruleset return identical data structures,
 which can be leveraged by the Datalog engine. 

### Docs and source inspection

Similarly to `clojure.repl/doc`, `datalog-rules.api/rule-doc` prints the documentation for a registered rule:

```clojure
(dr/rule-doc my-ruleset in-country)
;-------------------------
;(in-country ?person ?country)
;
;Matches iff `?person` lives in `?country`
=> nil
```

Likewise, `datalog-rules.api/rule-source` returns the source for a registered rule:

```clojure 
(dr/rule-source my-ruleset said-hello)
=>
[[(said-hello ?person) [?person :speaks :english] [?person :said "hi"]]
 [(said-hello ?person) [?person :speaks :french] [?person :said "salut"]]]

```

### Reversed rules generation (experimental)

In Datomic's current implementation of Datalog,
 the order of clauses in a rule [matters for performance](http://docs.datomic.com/query.html#sec-7).

For instance, the `(in-country ?person ?country)` rule we wrote above is fast if `?person` is already bound,
 but slow if `?country` is already bound and `?person` is not.

If that's an issue, one solution is to define 2 rules with different clauses order in their body,
 and choose which one to use depending on the query:

```clojure
(dr/unirule my-ruleset
  "Matches iff `?person` lives in `?country`. Binds ?person first."
  '[(in-country ?person ?country)
    [?person :person/address ?a]
    [?a :address/town ?t]
    [?t :town/country ?country]])
(dr/unirule my-ruleset
  "Matches iff `?person` lives in `?country`. Binds ?country first."
  '[(in-country- ?person ?country)
    [?t :town/country ?country]
    [?a :address/town ?t]
    [?person :person/address ?a]])
```

Of course, the issue is that you're duplicating code when doing this.
 Instead, you can use the `:auto-reverse` option of `(datalog-rules.api/ruleset)`:

```clojure
(def my-ruleset (dr/ruleset {:auto-reverse true}))

(dr/unirule my-ruleset
  "Matches iff `?person` lives in `?country`."
  '[(in-country ?person ?country)
    [?person :person/address ?a]
    [?a :address/town ?t]
    [?t :town/country ?country]])

(dr/rules my-ruleset)
=> [[(in-country ?person ?country)
     [?person :person/address ?a]
     [?a :address/town ?t]
     [?t :town/country ?country]]
    [(in-country- ?person ?country) ;; for the reversed rule, a '-' is appended to the rule name
     [?t :town/country ?country]
     [?a :address/town ?t]
     [?person :person/address ?a]]]

```

### Recommended code organisation

#### Isolate the ruleset in one namespace (and don't reload it)

Similarly to `extend-protocol`, `unirule` / `plurirule` / `pluriimpl` work by performing load-time mutation of the
target ruleset.

Therefore, to avoid having problems during interactive development,
 it is recommended to isolate each ruleset in its own namespace:

```clojure
(ns myapp.ruleset
  (:require [datalog-rules.api :as dr]))

(def my-ruleset (dr/ruleset {})
```

#### Write your own wrappers

Most applications probably need only one ruleset.
 To make things more comfortable, I recommend making your own wrappers the `datalog-rules` API:

```clojure
(ns myapp.rules
  (:require [datalog-rules.api :as dr]
            [myapp.ruleset :refer [my-ruleset]))

(def unirule (partial dr/unirule my-ruleset))
(def plurirule (partial dr/plurirule my-ruleset))
(def pluriimpl (partial dr/pluriimpl my-ruleset))
(defmacro rule-doc [rule-name]
  `(dr/rule-doc my-ruleset ~rule-name))
(defmacro rule-source [rule-name]
  `(dr/rule-source my-ruleset ~rule-name))

(defn rules []
  (dr/rules my-ruleset))
```

## Function - Rule analogy

| Functions | Rules |
|-----------|-------|
|`(defn ...)` | `(unirule my-ruleset ...)`|
|`(defmulti <name> ...)` | `(plurirule my-ruleset ...)`|
|`(defmethod <name> ...)` | `(pluriimpl my-ruleset ...)`|
|`(doc <name>)` | `(rule-doc my-ruleset <name>)`|
|`(source <name>)` | `(rule-source my-ruleset <name>)`|

## Notes

Interestingly, because Datalog is data-oriented, this library has no dependency to a concrete Datalog engine
(such as Datomic Peer Library or DataScript).

## TODO

* tree shaking

## License

Copyright © 2016 Valentin Waeselynck and contributors.

Distributed under the MIT License.
