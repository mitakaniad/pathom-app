(ns pathom-app.getting-started
  (:require
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc]
   [com.wsscode.pathom.connect.graphql :as pcg]
   [com.wsscode.pathom.graphql :as pg]
   [com.wsscode.pathom.trace :as pt]
   [clojure.core.async :refer [<!!]]
   ;;[com.wsscode.common.async-clj :refer [let-chan <!p go-catch <? <?maybe]]
   ))

(def user-db (atom {1  {:user/id 1 :user/name "Bert" :user/age 55}
                    2  {:user/id 2 :user/name "Sally" :user/age 22}
                    3  {:user/id 3 :user/name "Allie" :user/age 76}
                    4  {:user/id 4 :user/name "Zoe" :user/age 32}}))

(pc/defmutation user-by-keyword [env param]
  {::pc/sym    'user-by-keyword
   ::pc/params [:keyword-search]
   ::pc/output [{:all-users [:user/id :user/name]}]}
  {:all-users [{:user/id 123
                :user/name "name"}
               {:user/id 345
                :user/name "barname"}]}
  #_[{:user/id 123
      :user/name "name"}
     #_{:user/id 345
        :user/name "barname"}])

;; Without input
(pc/defresolver user-singleton [{::keys [db]} {:user/keys [id] :as user}]
  {::pc/output [{:user-singleton [:user/id :user/name]}]}
  {:user-singleton {:user/id 1 :user/name "foo"}})

;; Single input, result static
(pc/defresolver user-data [{::keys [db]} {:user/keys [id] :as user}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/id :user/name]}
  {:user/id 123
   :user/name "name"})

;; Single input, result static
(pc/defresolver user-data-id [{::keys [db]} {:user/keys [id] :as user}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/id]}
  {:user/id 123
   :user/name "name"})

;; Single input, result static
(pc/defresolver user-data-all [{::keys [db]} {:user/keys [id] :as user}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/id :user/name :user/email :user/created-at]}
  {:user/id 123
   :user/name "name"})

;; Single input, result static
(pc/defresolver user-data-less-result [{::keys [db]} {:user/keys [id] :as user}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/id :user/name :user/email :user/created-at]}
  {:user/id 123})

;; Single input, result from db
(pc/defresolver user-by-id [{::keys [db]} {:user/keys [id] :as user}]
  {::pc/input  #{:user/id}
   ::pc/output [:user/id :user/name :user/email :user/created-at]}
  (println :user-id id)
  (get @user-db id))

; now it's a good practice to create a sequence containing the resolvers
(def app-registry [user-by-keyword user-singleton user-data user-by-id])

;; Create a parser that uses the resolvers:
(def parser
  (p/parallel-parser
   {::p/env     {::p/reader               [p/map-reader
                                           pc/parallel-reader
                                           pc/open-ident-reader
                                           p/env-placeholder-reader]
                 ::p/placeholder-prefixes #{">"}}
    ::p/mutate  pc/mutate-async
    ::p/plugins [(pc/connect-plugin {::pc/register app-registry}) ; setup connect and use our resolvers
                 p/error-handler-plugin
                 p/request-cache-plugin
                 p/trace-plugin]}))

(defn foo []
  (->> ['{(user-by-keyword {:keyword-search ""})
          [:all-users]}]
       (parser {})
       <!!))

(comment

  (foo)

  ;;; Mutation Joins
  ;; => {user-by-keyword {:all-users [#:user{:id 123, :name "name"} #:user{:id 345, :name "barname"}]}}
  (->> ['{(user-by-keyword {:keyword-search ""})
          [:all-users]}]
       (parser {})
       <!!)

  ;;; Query
  ;; => {:user-singleton #:user{:id 1, :name "foo"}}
  (->> [:user-singleton]
       (parser {})
       <!!)

  ;;; Query, with spesific result
  ;; => {:user-singleton #:user{:name "foo"}}
  (->> [{:user-singleton [:user/name]}]
       (parser {})
       <!!)

  ;;; Single Input, fake id
  ;; Case 1: app-registry [user-data user-by-id]
  ;; Case 2: app-registry [user-by-id user-data]
  ;; => {[:user/id 1256] #:user{:id 1256, :name "name"}}
  ;; Case 3: app-registry [user-data]
  ;; => {[:user/id 1256] #:user{:id 1256, :name "name"}}
  ;; Case 4: app-registry [user-by-id]
  ;; => {[:user/id 1256] #:user{:id 1256, :name :com.wsscode.pathom.core/not-found}}
  ;; Case 5: app-registry [user-data-id user-by-id], :user/name not in pc/output
  ;; => {[:user/id 1256] #:user{:id 1256, :name :com.wsscode.pathom.core/not-found}}
  ;; Case 6: app-registry [user-data-all user-by-id], pc/output has more attribute than the result
  ;; => {[:user/id 1256] #:user{:id 1256, :name "name"}}
  ;; Case 7: app-registry [user-data-less-result user-by-id], :user/name not in the result
  ;; => {[:user/id 1256] #:user{:id 1256, :name :com.wsscode.pathom.core/not-found}}
  (->> [{[:user/id 1256] [:user/id :user/name]}]
       (parser {})
       <!!)

  ;;; Single Input, real id
  ;; Case 1: app-registry [user-data user-by-id]
  ;; Case 2: app-registry [user-by-id user-data]
  ;; Case 3: app-registry [user-data]
  ;; Case 4: app-registry [user-by-id]
  ;; Case 5: app-registry [user-data-id user-by-id], :user/name not in pc/output
  ;; Case 6: app-registry [user-data-all user-by-id], pc/output has more attribute than the result
  ;; => {[:user/id 1] #:user{:id 1, :name "Bert"}}

  ;; try all resolver with the same input
  ;; app-registry [user-data user-by-id]
  ;; user-data  -> get static value, with id replaced
  ;;            => {[:user/id 1] #:user{:id 1, :name "name"}}
  ;; user-by-id -> get :user/name 
  ;;            => {[:user/id 1] #:user{:id 1, :name "Bert"}}
  ;; if result has more attribute than output, undefined output will be set to not found
  (->> [{[:user/id 1] [:user/id :user/name]}]
       (parser {})
       <!!)

  ;;; Single Input, not working because it needs query result
  #_(->> [:user/id 1]
         (parser {})
         <!!)

  )