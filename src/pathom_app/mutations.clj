(ns pathom-app.mutations
  (:require
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc]
   [com.wsscode.pathom.connect.graphql :as pcg]
   [com.wsscode.pathom.graphql :as pg]
   [com.wsscode.pathom.trace :as pt]
   [clojure.core.async :refer [<!!]]
   ;;[com.wsscode.common.async-clj :refer [let-chan <!p go-catch <? <?maybe]]
   ))

(pc/defmutation send-message [env {:keys [message/text]}]
  {::pc/sym    'send-message
   ::pc/params [:message/text]
   ::pc/output [:message/id :message/text]}
  {:message/id   123
   :message/text text})

(pc/defmutation create-user [{::keys [db]} user]
  {::pc/sym    'user/create
   ::pc/params [:user/name :user/email]
   ::pc/output [:user/id]}
  (let [{:keys [user/id] :as new-user}
        (-> user
            (select-keys [:user/name :user/email])
            (merge {:user/id         1
                    :user/created-at "08/02/2019"}))]
    (swap! db assoc-in [:users id] new-user)
    {:user/id id}))

(pc/defresolver user-data [{::keys [db]} {:keys [user/id]}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/id :user/name :user/email :user/created-at]}
  (get-in @db [:users id]))

(pc/defresolver user-data-less-output [{::keys [db]} {:keys [user/id]}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/id :user/name]}
  (get-in @db [:users id]))

(pc/defresolver user-data-less-result [{::keys [db]} {:keys [user/id]}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/id :user/name :user/email :user/created-at]}
  (let [user (get-in @db [:users id])]
    {:user/id (:user/id user)
     :user/name (:user/name user)}))

(pc/defresolver all-users [{::keys [db]} _]
  {::pc/output [{:user/all [:user/id :user/name :user/email :user/created-at]}]}
  (vals (get db :users)))

(pc/defresolver users [{::keys [db]} _]
  {::pc/output [{:user/all [:user/id :user/name :user/email :user/created-at]}]}
  {:user/all (get db :users)})

(def app-registry [send-message
                   create-user user-data])

(def parser
  (p/parallel-parser
   {::p/env     {::p/reader [p/map-reader
                             pc/parallel-reader
                             pc/open-ident-reader]
                  ::db       (atom {})}
    ::p/mutate  pc/mutate-async
    ::p/plugins [(pc/connect-plugin {::pc/register app-registry})
                 p/error-handler-plugin
                 p/request-cache-plugin
                 p/trace-plugin]}))

(comment
  
  ;; "pc/params is currently a non-op"
  ;; "pc/output is valid and can be used for auto-complete information"
  
  ;;; Mutation with param
  ;; => {send-message #:message{:id 123, :text "hello world"}}
  (->> ['(send-message {:message/text "hello world"})]
       (parser {})
       <!!)
  
  ;;; Mutation join
  
  ;; Case 1: no resolver
  ;; app-registry [create-user]
  ;; => #:user{create #:user{:id 1}}
  (->> ['{(user/create {:user/name "Rick Sanches" :user/email "rick@morty.com"}) 
          [:user/id]}]
       (parser {})
       <!!)
  
  ;; => #:user{create #:user{:id 1, :name :com.wsscode.pathom.core/not-found, :created-at :com.wsscode.pathom.core/not-found}}
  (->> ['{(user/create {:user/name "Rick Sanches" :user/email "rick@morty.com"})
          [:user/id :user/name :user/email]}]
       (parser {})
       <!!)
  
  ;; Case 2: add resolver for name and email
  ;; app-registry [create-user user-data]
  ;; => #:user{create #:user{:id 1, :name "Rick Sanches", :email "rick@morty.com"}}
  
  ;; user/create -> create user
  ;; user-data -> get :user/name :user/email
  (->> ['{(user/create {:user/name "Rick Sanches" :user/email "rick@morty.com"})
          [:user/id :user/name :user/email]}]
       (parser {})
       <!!)

  ;; Case 3: output has less attribute than the result
  ;; app-registry [create-user user-data-less-output]
  ;; => #:user{create #:user{:id 1, :email :com.wsscode.pathom.core/not-found, :email "rick@morty.com"}}
  
  ;; user/create -> create user
  ;; user-data -> get :user/name
  (->> ['{(user/create {:user/name "Rick Sanches" :user/email "rick@morty.com"})
          [:user/id :user/name :user/email]}]
       (parser {})
       <!!)
  
  ;; Case 4: result has less atributte than the output
  ;; app-registry [create-user user-data-less-result]
  ;; => #:user{create #:user{:id 1, :name "Rick Sanches", :email :com.wsscode.pathom.core/not-found}}
  
  ;; user/create -> create user
  ;; user-data -> get :user/name
  (->> ['{(user/create {:user/name "Rick Sanches" :user/email "rick@morty.com"})
          [:user/id :user/name :user/email]}]
       (parser {})
       <!!)

  ;; Case 4: alternative resolver
  ;; app-registry [create-user all-users]
  ;; => #:user{create #:user{:id 1, :name "Rick Sanches", :email "rick@morty.com"}}
  
  ;; user/create -> create user
  ;; all-users -> get :user/name :user/email
  (->> ['{(user/create {:user/name "Rick Sanches" :user/email "rick@morty.com"})
          [:user/id :user/name :user/email]}]
       (parser {})
       <!!)

  ;;; Conclusion
  ;; "user/id from the mutation, the resolvers can walk the graph and 
  ;;  fetch the other requested attributes"
  
  ;; app-registry [users]
  ;; => #:user{:all nil}
  (->> [:user/all]
       (parser {})
       <!!)
  )