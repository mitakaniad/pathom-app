(ns pathom-app.core
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

; now it's a good practice to create a sequence containing the resolvers
(def app-registry [user-by-keyword user-singleton user-data])

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

  )
