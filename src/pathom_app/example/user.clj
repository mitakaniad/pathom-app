(ns pathom-app.example.user
  (:require
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc]
   [com.wsscode.pathom.connect.graphql :as pcg]
   [com.wsscode.pathom.graphql :as pg]
   [com.wsscode.pathom.trace :as pt]
   [clojure.core.async :refer [<!!]]
   [clojure.string :as str]
   ;;[com.wsscode.common.async-clj :refer [let-chan <!p go-catch <? <?maybe]]
   ))

(def user-db (atom {1  {:user/id 1 :user/name "Bert" :user/age 55}
                    2  {:user/id 2 :user/name "Sally" :user/age 22}
                    3  {:user/id 3 :user/name "Allie" :user/age 76}
                    4  {:user/id 4 :user/name "Zoe" :user/age 32}}))

(pc/defresolver search-by-keyword [env input]
  {::pc/output [{:all-user [:user/id :user/name :user/age]}]}
  (let [{:keys [keyword]} (-> env :ast :params)
        users (->> @user-db
                   vals
                   (filter #(.contains (str/lower-case (:user/name %)) (str keyword)))
                   vec)]
    {:all-user users}))

(def app-registry [search-by-keyword])

(def parser
  (p/parallel-parser
   {::p/env     {::p/reader               [p/map-reader
                                           pc/parallel-reader
                                           pc/open-ident-reader
                                           p/env-placeholder-reader]
                 ::p/placeholder-prefixes #{">"}}
    ::p/mutate  pc/mutate-async
    ::p/plugins [(pc/connect-plugin {::pc/register app-registry})
                 p/error-handler-plugin
                 p/request-cache-plugin
                 p/trace-plugin]}))

(comment 
  
  (->> ['(:all-user {:keyword "all"})]
       (parser {})
       <!!)
  )