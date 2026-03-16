(add-libs {'cljfx/cljfx         {:mvn/version "1.10.6"}
           'com.wsscode/pathom3 {:mvn/version "2025.01.16-alpha"}
           'funcool/promesa     {:mvn/version "12.0.0-RC2"}})

(require ;;'[com.wsscode.pathom3.interface.smart-map :as psm]
  '[clojure.core.cache.wrapped :as cc]
  '[com.wsscode.pathom3.cache :as p.cache]
  '[com.wsscode.pathom3.connect.indexes :as pci]
  '[com.wsscode.pathom3.connect.operation :as pco]
  '[com.wsscode.pathom3.interface.async.eql :as p.a.eql]
  '[com.wsscode.pathom3.connect.planner :as pcp]
  '[promesa.core :as p])

;; Modified example from Pathom Docs
;; https://pathom3.wsscode.com/docs/resolvers

;; # Core State
;; We start off with some "core" state map
(def *state
  (atom {::id 1}))

;; This is assumed static
(def user-db
  {1 {::name     "Alice"
      ::email    "alice@example.com"
      ::birthday "1989-10-25"}
   2 {::name     "Bob"
      ::email    "bob@example.com"
      ::birthday "1975-09-11"}
   3 {::name     "Bill"
      ::email    "bill@example.com"
      ::birthday "1969-07-12"}})

;; # Derived States
;; Then "derived states" are implemented using Pathom Resolvers

;; Resolvers are similar to functions,
;; but have more structured input/outputs.
;; They take in a map and return a map.
;; The I/O keys are declared at the top.
;; The keys are global to an environment
;; (so need to be namespaced and unique)
(pco/defresolver user-by-id
  [{::keys [id]}]        ; INPUTS
  {::pco/input  [::id]
   ::pco/output [::name  ; OUTPUTS
                 ::email
                 ::birthday]}
  ;; we'll run this on a thread in the background
  (p/vthread (do (println (str "Going in to the DB and getting user: "
                               id))
                 (Thread/sleep 2345)
                 (get user-db
                      id))))

;; Resolvers can be executed like a function.
;; However,
;; this is effectively only useful during testing
;; (normal, non-threaded resolvers can be chained in pipelines as well)
#_@(user-by-id {::id 1})
;; {:name "Alice", :email "alice@example.com", :birthday "1989-10-25"}
                                        ;
;; Here is another Resolver (I need to think of a more exciting example)

(pco/defresolver birth-year
  [{::keys [birthday]}]
  {::pco/input  [::birthday]
   ;; ::pco/cache-store ::my-cache   ; you can also designate a cache (memoization)
   ::pco/output [::birth-year]}
  (p/vthread (do (println (str "Extracting a Birth Year from the BDay: "
                               birthday))
                 (Thread/sleep 3141)
                 {::birth-year (-> birthday
                                   (clojure.string/split #"-")
                                   first)})))
#_
@(birth-year {::birthday "2012-12-12"})
;;#:kxygk.pathomfx.state{:birth-year "2012"}

;; # Pathom Engine

;; However,
;; the real power of Pathom is in using its engine.
;; You designate input and request output
;; (using EQL queries).
;; The engine then finds the sequence of resolvers that will get you the output,
;; runs them,
;; and returns the result map.

;; First the resolvers are registered to an environment `env`
;; The `env` is the Soup of Resolvers/Keys where the Pathom engine looks for a solution
;; (we also specify we want parallel execution of independent resolvers)
#_
(let [env (pci/register {::p.a.eql/parallel? true}
                        [user-by-id
                         birth-year])]
  ;; The Engine request I'm using here is specifically async.
  ;; So it can handle that the resolvers are returning Promesa `vthread`s
  @(p.a.eql/process env                ; the `process` call takes an
                    {::id 1}           ; input map
                    [::birth-year]))   ; and returns a promise of a map of keys
;;#:kxygk.pathomfx.state{:birth-year "1989"}

;; Note how such an "API" is fundamentally extremely flexible:
;; You can get intermediary forms
#_
(let [env (pci/register {::p.a.eql/parallel? true}
                        [user-by-id
                         birth-year])]
  @(p.a.eql/process env
                    {::id 1}
                    [::birthday]))   ; get the earlier `::birthday` output
;;#:kxygk.pathomfx.state{:birthday "1989-10-25"}

;; You can also inject data at different parts of the pipeline
#_
(let [env (pci/register {::p.a.eql/parallel? true}
                        [user-by-id
                         birth-year])]
  @(p.a.eql/process env
                    {::name     "Baby"   ;; inject your own user not in the db
                     ::birthday "2025-11-11"}
                    [::birth-year]))

;; The Pathom engine is clever enough to cache resolver outputs during each request,
;; so that different pieces can be safely reused during different computations

;; However,
;; as we will see,
;; architecturally the UI will make many separate requests.
;; So a manual cache should be added to each resolver.
;; We make a small fn that injects an size-1 cache into each resolver.
;; (ie. it remembers the last "run")
(defrecord CoreCacheStore
    ;;taken from https://pathom3.wsscode.com/docs/cache
    [cache-atom]
  p.cache/CacheStore
  (-cache-lookup-or-miss [_
                          cache-key
                          f]
    (cc/lookup-or-miss cache-atom
                       cache-key
                       (fn [_] (f))))
  (-cache-find [_
                cache-key]
    (find @cache-atom
          cache-key)))

(defn lru-cache
  "Makes a basic empty cache with a given threshold"
  [threshold]
  (-> (cc/lru-cache-factory {}
                            :threshold
                            threshold)
      (->CoreCacheStore)))

;; TODO: "LRU1" is sorta meaningless..
;; There is probably a less dumb way to make a single entry cache
(def cache-strategies
  {:lru1  (fn [] (lru-cache 1))
   :lru50 (fn [] (lru-cache 50))
   :none  (fn [] nil)})

(def default-resolver-cache
  :lru1)

(defn add-caches
  "This drills in to the `env` to inject caches"
  [env]
  (let [resolvers (get env
                       ::pci/index-resolvers)]
    (reduce-kv
      (fn [new-env
           resolver-key
           resolver]
        (let [cache-type (get-in resolver
                                 [:config
                                  ::inject-cache]
                                 default-resolver-cache)]
          (let [cache-constructor (get cache-strategies
                                       cache-type)]
            (if-let [new-cache-atom (cache-constructor)]
              (let [cache-unique-key (keyword (str (name resolver-key)
                                                   "CACHE"))]
                (-> new-env
                    (assoc cache-unique-key ;; add cache's key to `env`
                           new-cache-atom)
                    (assoc-in [::pci/index-resolvers ;; point resolver to cache
                               resolver-key
                               :config
                               ::pco/cache-store]
                              cache-unique-key)))
              (do (println "Unknown caching strategy")
                  new-env)))))
      env
      resolvers)))


;; We can now an environment with all the resolvers,
;; and inject small single entry caches for each one
;; We also add a plan-cache
(defonce plan-cache*
  (atom {}))

(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    [user-by-id
                     birth-year])
      (pcp/with-plan-cache plan-cache*)
      add-caches))

;; The Pathom engine then takes a given series of input keys,
;; finds a way to hook up the resolvers to get the requested output
#_
(p.a.eql/process env
                 {::id 2} ;; input map
                 [::birth-year])
#_
(p.a.eql/process env
                 {::id 1} ;; input map
                 [::birthday])
#_
@(p.a.eql/process env
                  {::name     "Baby"
                   ::birthday "2025-11-11"}
                  [::birth-year])


;; The outstanding issue is that while resolvers and plans are cached,
;; the `p.a.eql/process`'s final returned Promises is regenerated on each request.
;; (you can try running the tests above and they'll return new Promises each time)
;; This is the case even is all the intermediary Resolvers are returning caches Promises!

;; We will create a wrapper UI element based on `fx/ext-state`.
;; It will act as a cache and hold on to a Promise.
;; It will be keyed on
;; - the Pathom query
;; - the parts of the core-state that the query needs
;; If either of these things change then the UI element is destroyed.
;; It is then recreated (with the new key) and it will get a new updated Promise.

(require '[edn-query-language.core :as eql]
         '[com.wsscode.pathom3.format.shape-descriptor :as pfsd])

(defn get-actual-dependencies
  "This finds the input keys required by a query.
  NOTE: Probably doesn't work right for nested queries"
  [env
   entity
   tx]
  (let [ast   (eql/query->ast tx)
        plan  (pcp/compute-run-graph (assoc env
                                            :edn-query-language.ast/node
                                            ast
                                            ::pcp/available-data
                                            (pfsd/data->shape-descriptor entity)))
        nodes (::pcp/nodes plan)]
    (->> nodes
         vals
         (mapcat ::pcp/input)
         keys
         set)))

;; Now the GUI
(require '[cljfx.api :as fx])

(defn promise-gate-desc
  "The `fx/ext-state` injects props/keys in to the UI tree in `:desc`.
  Particularly:
  - `:state` for its current state.
  - `:swap-state` for a function to update the internal state"
  [{:keys [state
           swap-state
           env
           entity
           tx
           loading-ui
           realized-ui-fn]}]
  (cond
    ;; promise realized -> make UI element
    (map? state)
    (realized-ui-fn state)
    ;; on first run we're in the `:init` phase
    ;; - make the EQL request and get a promise back
    ;; - attach a callback to the promise that will update the state of this element
    ;; - (that later state update will trigger a redraw)
    ;; - return a `loading-ui`
    ;; - change the state to `:waiting`
    ;; This only gets run once!
    (= state
       :init)
    (let [pathom-promise (p.a.eql/process env
                                          entity
                                          tx)]
      (swap-state (constantly :waiting))
      (p/then pathom-promise
              #(fx/on-fx-thread (swap-state (constantly %))))
      loading-ui)
    ;; This condition is hit in two cases:
    ;; - there is a first pass where the `promise-gate` is being built,
    ;; and for some reason the state is `nil`
    ;; - it's `:waiting` for the promise
    ;; (something triggers a redraw of the loading-ui - ex: app resize)
    :else
    loading-ui))

(defn promise-gate
  "An extra wrapped of `fx/ext-recreate-on-key-changed` is needed for clarity.
  Unfortunately `:key` in `fx/ext-state` plays two roles.
  As in other UI elements,
  it is the UI's identity - that if-changed triggers a regeneration of the element.
  However,
  if you provide a `:key`,
  then `fx/state` will pass its state to the `:desc` using this as the key.
  (instead of the default `:state`)"
  [{:keys [env
           entity
           tx]
    :as props}]
  (let [needed-keys    (get-actual-dependencies env
                                                entity
                                                tx)
        relevant-state (select-keys entity
                                    needed-keys)]
    {:fx/type fx/ext-recreate-on-key-changed
     :key     [tx
               relevant-state]
     :desc    {:fx/type       fx/ext-state
               :initial-state :init
               :desc          (assoc props
                                     :fx/type promise-gate-desc)}}))

(defn ui-root
  [{:keys [value]}]
  {:fx/type :stage
   :showing true
   :scene   {:fx/type :scene
             :root    {:fx/type  :v-box
                       :children [{:fx/type        promise-gate
                                   :env            env
                                   :entity         value
                                   :tx             [::birth-year]
                                   :loading-ui     {:fx/type :label
                                                    :text    "Loading..."}
                                   :realized-ui-fn (fn [pathom-map]
                                                     {:fx/type :label
                                                      :text    (str "Born: "
                                                                    (-> pathom-map
                                                                        ::birth-year))})}
                                  {:fx/type   :button
                                   :text      "Next User"
                                   :on-action (fn [_]
                                                (swap! *state
                                                       update
                                                       ::id
                                                       #(if (= % 3)
                                                          1
                                                          (inc %))))}]}}})

(defn root-state-watcher
  "This `fx/ext-watcher` is an element that just watches an IRef.
  In this case it's watching our core state.
  This setup obviates the need for a renderer.
  You could have parts of the UI tree have their own states/`fx/ext-watcher`"
  [{:keys [state]}]
  {:fx/type fx/ext-watcher
   :ref     state
   :desc    {:fx/type ui-root}})

(def app
  (-> {:fx/type root-state-watcher
       :state   *state}
      fx/create-component
      fx/on-fx-thread))
