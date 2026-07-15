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

