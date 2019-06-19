(ns pathom-app.parameters
  (:require
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc]
   [com.wsscode.pathom.connect.graphql :as pcg]
   [com.wsscode.pathom.graphql :as pg]
   [com.wsscode.pathom.trace :as pt]
   [clojure.core.async :refer [<!!]]
   ;;[com.wsscode.common.async-clj :refer [let-chan <!p go-catch <? <?maybe]]
   ))

;;; Parameters
(def instruments
  [{:instrument/id 1
    :instrument/brand "Fender"
    :instrument/type :instrument.type/guitar
    :instrument/price 300}
   {:instrument/id 2
    :instrument/brand "Tajima"
    :instrument/type :instrument.type/ukulele
    :instrument/price 50}
   {:instrument/id 3
    :instrument/brand "Ibanez"
    :instrument/type :instrument.type/bass
    :instrument/price 270}
   {:instrument/id 4
    :instrument/brand "Cassio"
    :instrument/type :instrument.type/piano
    :instrument/price 160}])

(pc/defresolver instruments-list [env _]
  {::pc/output [{::instruments [:instrument/id :instrument/brand
                                :instrument/type :instrument/price]}]}
  (let [{:keys [sort]} (-> env :ast :params)]
    {::instruments (cond->> instruments
                     (keyword? sort) (sort-by sort))}))

; now it's a good practice to create a sequence containing the resolvers
(def app-registry [instruments-list])

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

(comment

  ;;; Parameters
  ;; "inputs are more a dependency thing, while params are more like options"
  
  ;; invalid expression
  (->> [(::instruments {:sort :instrument/brand})]
       (parser {})
       <!!)

  ;; valid expression
  (->> ['(::instruments {:sort :instrument/brand})]
       (parser {})
       <!!)

  (->> ['(::instruments {:sort :instrument/price})]
       (parser {})
       <!!)

  (->> ['(::instruments {:sort :instrument/type})]
       (parser {})
       <!!)

  ;; params with join
  (->> ['{(::instruments {:sort :instrument/price})
          [:instrument/id :instrument/brand]}]
       (parser {})
       <!!)
  
  )
