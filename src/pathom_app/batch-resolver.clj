(ns pathom-app.batch-resolver
  (:require [com.wsscode.pathom.core :as p]
            [com.wsscode.pathom.connect :as pc]
            [clojure.core.async :as async :refer [<!! go]]
            [com.wsscode.pathom.profile :as pp]))

(pc/defresolver list-things [_ _]
  {::pc/output [{:items [:number]}]}
  {:items [{:number 3}
           {:number 10}
           {:number 18}]})

(pc/defresolver slow-resolver [_ {:keys [number]}]
  {::pc/input  #{:number}
   ::pc/output [:number-added]}
  (go
    (async/<! (async/timeout 1000))
    {:number-added (inc number)}))

(def app-registry [list-things slow-resolver])

(def parser
  (p/async-parser
   {::p/env     {::p/reader [p/map-reader
                             pc/async-reader2
                             pc/open-ident-reader]}
    ::p/mutate  pc/mutate-async
    ::p/plugins [(pc/connect-plugin {::pc/register app-registry})
                 p/error-handler-plugin
                 p/request-cache-plugin
                 p/trace-plugin]}))

(comment
  
  ;; => {:items [{:number-added 4} {:number-added 11} {:number-added 19}]}
  ;; list-things -> slow-resolver
  (->> [{:items [:number-added]}]
       (parser {})
       <!!)
  
  )