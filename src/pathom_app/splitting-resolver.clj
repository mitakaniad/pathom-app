(ns pathom-app.splitting-resolver
  (:require
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc]
   [com.wsscode.pathom.connect.graphql :as pcg]
   [com.wsscode.pathom.graphql :as pg]
   [com.wsscode.pathom.trace :as pt]
   [clojure.core.async :refer [<!!]]
   ;;[com.wsscode.common.async-clj :refer [let-chan <!p go-catch <? <?maybe]]
   ))

;;; Splitting resolver

(def product->brand
  {1 "Taylor"})

(pc/defresolver latest-product [_ _]
  {::pc/output [{::latest-product [:product/id :product/title :product/price]}]}
  {::latest-product {:product/id    1
                     :product/title "Acoustic Guitar"
                     :product/price 199.99M}})

(pc/defresolver product-brand [_ {:keys [product/id]}]
  {::pc/input  #{:product/id}
   ::pc/output [:product/brand]}
  {:product/brand (get product->brand id)})

;; a silly pretend lookup
(def brand->id {"Taylor" 44151})

(pc/defresolver brand-id-from-name [_ {:keys [product/brand]}]
  {::pc/input #{:product/brand}
   ::pc/output [:product/brand-id]}
  {:product/brand-id (get brand->id brand)})

; now it's a good practice to create a sequence containing the resolvers
(def app-registry [latest-product product-brand brand-id-from-name])

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

  ;;; Splitting Resolver
  ;; "it will try to figure it out how to satisfy that request based on 
  ;;  the attributes that the current contextual entity already has."

  ;; => #:pathom-app.core{:latest-product #:product{:title "Acoustic Guitar", :brand "Taylor"}}
  ;; latest-product -> get :product/title and get explicit :product/id
  ;; product-brand -> get :product/brand based on :product/id from latest-product resolver
  (->> [{::latest-product [:product/title :product/brand]}]
       (parser {})
       <!!)

  ;; "When a required attribute is not present in the current entity, 
  ;;  Connect will look for resolvers that can fetch it, 
  ;;  analyze their inputs, and recursively walk backwards towards the "known data" in the context."

  ;; => #:pathom-app.core{:latest-product #:product{:title "Acoustic Guitar", :brand-id 44151}}
  ;; latest-product -> get :product/title and get explicit :product/id
  ;; product-brand -> get explicit :product/brand based on :product/id from latest-product resolver
  ;; brand-id-from-name -> get :product/brand-id based on :product/brand from product-brand resolver
  (->> [{::latest-product [:product/title :product/brand-id]}]
       (parser {})
       <!!)

  ;;; Ident-join Queries
  ;; => {[:product/id 1] #:product{:brand "Taylor"}}  
  (->> [{[:product/id 1] [:product/brand]}]
       (parser {})
       <!!)

  ;; => {[:product/brand "Taylor"] #:product{:brand-id 44151}}
  (->> [{[:product/brand "Taylor"] [:product/brand-id]}]
       (parser {})
       <!!)

  )
