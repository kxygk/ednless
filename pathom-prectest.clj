(add-libs {'com.wsscode/pathom3 {:mvn/version "2025.01.16-alpha"}})

(require
  '[com.wsscode.pathom3.interface.eql :as p.eql]
  '[com.wsscode.pathom3.connect.operation :as pco]
  '[com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
  '[com.wsscode.pathom3.connect.indexes :as pci])

(pco/defresolver get-user
  [{:users/keys [id]}]
  {::pco/output [:users/id
                 :users/email
                 :users/customer_id
                 :users/budget]}
  (println "get-user triggered")
  {:users/id id
   :users/email "99@99.com"
   :users/customer_id 99
   :users/budget 999})

(pco/defresolver get-customer
  [{:customers/keys [id]}]
  {::pco/output [:customers/id
                 :customers/billing_number
                 :customers/phone_number]}
  (println "get-customer triggered")
  {:customers/id id
   :customers/billing_number 69
   :customers/phone_number 6969})

(pco/defresolver get-user-with-customer
  [{:users/keys [id]}]
  {::pco/output [:users/id
                 :users/email
                 :users/customer_id
                 :fat-key
                 :customers/id
                 :customers/billing_number
                 :customers/phone_number]}
  (println "get-user-with-customer triggered")
  {:users/id 66
   :users/email "66@66.com"
   :users/customer_id id
   :fat-key 66
   :customers/id id
   :customers/billing_number 666
   :customers/phone_number 6666})

(def user-customer-bridge
  (pbir/alias-resolver :users/customer_id
                       :customers/id))

(def env
  (pci/register [get-user
                 get-customer
                 get-user-with-customer
                 user-customer-bridge]))

;; ---- test1
(p.eql/process env
               {:users/id 1000}
               [:fat-key
                :customers/phone_number
                :users/email])
;; {:fat-key 66, :customers/phone_number 6666, :users/email "66@66.com"}

;; ---- get-user-with-customer triggered


;; ---- test2
(p.eql/process env
               {:users/id 1000}
               [:customers/phone_number
                :fat-key
                :users/email])
;; {:customers/phone_number 6666, :fat-key 66, :users/email "66@66.com"}


;; !!!!!!!!!!!!!!!!!!!!!!!!
;; MORE TESTS
;; !!!!!!!!!!!!!!!!!!!!!!!!

;; ;;;;;;
;; Fat Pack test
;;
;; This forces a resolver to run

(pco/defresolver get-user-with-customer
  [{:users/keys [id]}]
  {::pco/output [{:fat-pack [:users/id
                             :users/email
                             :users/customer_id
                             :customers/id
                             :customers/billing_number
                             :customers/phone_number]}]}
  (println "get-user-with-customer FAT triggered")
  {:fat-pack {:users/id                 66
              :users/email              "66@66.com"
              :users/customer_id        id
              :customers/id             id
              :customers/billing_number 666
              :customers/phone_number   6666}})

(def env
  (pci/register [get-user
                 get-customer
                 get-user-with-customer
                 user-customer-bridge]))

(p.eql/process env
               {:users/id 1000}
               [{:fat-pack [:customers/phone_number
                            :users/email]}])
;;{:fat-pack {:customers/phone_number 6666, :users/email "66@66.com"}}
;;
;; This works but the result is in an extra layer of wrapping
;; (next part resolves this)
;;

;; ;;;;;;
;; Nested Inputs
;;
;; This allows the `:fat-pack` to be transparently consumed
;;

(pco/defresolver fat-eater
  [{:keys [fat-pack]}]
  {::pco/input[{:fat-pack [:users/id
                           :users/email
                           :users/customer_id
                           :customers/id
                           :customers/billing_number
                           :customers/phone_number]}]
   ::pco/output [:response]}
  (println "fat-eater triggered")
  {:response (str "yum, just ate: "
                  (:users/id fat-pack))})


(def env
  (pci/register [get-user
                 get-customer
                 get-user-with-customer
                 user-customer-bridge
                 fat-eater]))

(p.eql/process env
               {:users/id 1000}
               [:response])
;; {:response "yum, just ate: 66"}


;; BRANCHING PIPELINES
;; How to force a pipeline to take an injexted custom detour


;; ;;;;;;
;; Pipeline Example

(pco/defresolver A
  [{::keys [start]}]
  {::a-2-b (+ start
             1)})

(pco/defresolver B
  [{::keys [a-2-b]}]
  {::b-2-c (+ a-2-b
             998)})

(pco/defresolver C
  [{::keys [b-2-c]}]
  {::result b-2-c})

(def env2
  (pci/register [A
                 B
                 C]))

(p.eql/process env2
               {::start 0.0}
               [::result])
;;#:user{:result 999.0}

;; ;;;;;;
;; Branch selection key on input

(pco/defresolver XXX
  [{::keys [a-2-b
            xxx-key]}]
  {::b-2-c (+ a-2-b
              665)})

(def env2
  (pci/register [A
                 B
                 C
                 XXX]))

(p.eql/process env2
               {::start 0.0}
               [::result])
;;#:user{:result 999.0}

(p.eql/process env2
               {::start 0.0
                ::xxx-key nil}
               [::result])
;; #:user{:result 666.0}

;; ;;;;;;
;; Branch selection key on output - DOESN'T WORK

(pco/defresolver YYY
  [{::keys [a-2-b]}]
  {::b-2-c (+ a-2-b
              968)
   ::yyy-key nil})

(def env3
  (pci/register [A
                 B
                 C
                 XXX
                 YYY]))

(p.eql/process env3
               {::start 0.0}
               [::result])
;;#:user{:result 969.0}

(p.eql/process env3
               {::start 0.0}
               [::result
                ::yyy-key])
;;#:user{:result 969.0, :yyy-key nil}

;; Registering resolvers in a different order doesn't change anything
;; YYYY is always preferred.
;; Maybe because it has the same input requirements but provides two outputs instead of one

(def env4
  (pci/register [YYY
                 A
                 B
                 C
                 XXX]))


(p.eql/process env4
               {::start 0.0}
               [::result])
;;#:user{:result 969.0}

(p.eql/process env4
               {::start 0.0}
               [::result
                ::yyy-key])
