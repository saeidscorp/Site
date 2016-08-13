(ns site.routes.blog
  (:require [compojure.core :refer (defroutes GET PUT ANY)]
            [site.utils :refer [handler ->>>]]
            [site.db.entities :as e]
            [site.service.media :as media]
            [site.layout :as layout]
            [ring.util.response :refer [redirect]]
            [bidi.ring :as bdr]
            [bidi.bidi :as bd]
            [site.utils.markdown]
            [me.raynes.fs :as fs])
  (:use delimc.core
        hara.event)
  (:import (java.io IOException)))

(def single-context-map {:breadcrumb-path [{:href "/" :name "Home"} {:href "/blog" :name "blog"}]
                         :popular-tags    [{:href "/tag/clojure" :name "Clojure"} {:href "/tag/java" :name "Java"}]
                         :categories      [{:href "/tag/clojure" :name "Clojure"} {:href "/tag/java" :name "Java"}]
                         :comments        true})

(def multi-context-map {:breadcrumb-path [{:href "/" :name "Home"} {:href "/blog" :name "blog"}]
                        :popular-tags    [{:href "/tag/clojure" :name "Clojure"} {:href "/tag/java" :name "Java"}]
                        :categories      [{:href "/tag/clojure" :name "Clojure"} {:href "/tag/java" :name "Java"}]})

(def multi-card-context-map {:breadcrumb-path [{:href "/" :name "Home"} {:href "/blog" :name "blog"}]
                             :popular-tags    [{:href "/tag/clojure" :name "Clojure"} {:href "/tag/java" :name "Java"}]
                             :categories      [{:href "/tag/clojure" :name "Clojure"} {:href "/tag/java" :name "Java"}]
                             :blog-box-rows   [[{:title       "Welcome to this blog!"
                                                 :description "Lorem ispum dolor sit amet."
                                                 :author      {:name      "Saeid"
                                                               :image-src "/assets/images/blog/author/author_1.jpg"}
                                                 :tags        [{:name "Welcome" :url "#"}]
                                                 :categories  [{:title "Welcome"}]
                                                 :has-image   true
                                                 :image-src   "/assets/images/blog/card/blog_1.jpg"
                                                 :date        "02 Sep"}
                                                {:title       "Clojure for Summer!"
                                                 :description "A short outline of plans to do in summer."
                                                 :author      {:name "Saeid" :image-src ""}
                                                 :tag         [[:name "Plans" :url "#"] [:name "Clojure" :url "#"]]}]
                                               [{:title       "Mac OS X Yosemite"
                                                 :description "This is the first time I tried Mac OS X."
                                                 :author      {:name      "Saeid"
                                                               :image-src "/assets/images/blog/author/author_3.jpg"}
                                                 :tags        [{:name "OS" :url "#"}]
                                                 :image-src   "/media/uploads/screenshot.png"}
                                                {:title       "OpenShift Hosting"
                                                 :description "Lorem ipsum dolor sit amet."
                                                 :author      {:name      "Saeid"
                                                               :image-src "/assets/images/blog/author/author_2.jpg"}
                                                 :tags        [{:name "Clojure" :url "#"}
                                                               {:name "Cloud" :url "#"}]}]]})

;(defroutes blog-routes
;           (GET "/blog/multi-card-boxed" [] (layout/render "blog/multi-card-boxed.html" multi-card-context-map)) ;; check
;           (GET "/blog/multi-card-side" [] (layout/render "blog/multi-card-side.html" multi-context-map)) ;; check
;           (GET "/blog/multi-full" [] (layout/render "blog/multi-full.html" multi-context-map))
;           (GET "/blog/multi-side" [] (layout/render "blog/multi-side.html" multi-context-map))
;           (GET "/blog/single-full" [] (layout/render "blog/single-full.html" single-context-map)) ;; check
;           (GET "/blog/single-side/:id" [id] (layout/render "blog/single-side.html"
;                                                            (assoc single-context-map :post (e/post-to-map (e/get-post-by-id id))))))

;; TODO: use site.service.user/get-logged-in-user
(defn handle-new-post [{{title         "title", short-title "short_title",
                         short-content "short_content", post-content "post_content"}
                        :params
                        :as reqmap}]
  ;(prone.debug/debug)
  (reset (shift k (e/create-post title short-title short-content post-content) (k :ok))
         (layout/render "blog/redirect-after.html" {:status          :success
                                                    :message         "Post created successfully."
                                                    :detail-message  "Redirecting to post..."
                                                    :redirect-target (bd/path-for site.layout/routes :post :url-title short-title)})))

(defn handle-image-upload [{{{:keys [filename tempfile content-type] :as file-props} "editormd-image-file"} :params :as reqmap}]
  (cheshire.core/encode
    (reset (let [path (str (media/media-path reqmap) filename)]
             (shift k (try (fs/copy+ tempfile path)
                           (k :ok)
                           (catch IOException e
                             (signal [:upload-image-error {:exception  e
                                                           :file-props file-props}])
                             {:success 0})))
             ;; FIXME: relying on content-type sent from client is dangerous.
             (shift k (e/create-media path content-type)
                    (k {:success 1
                        :message "successfully uploaded."
                        :url     (str "/media/uploads/" filename)}))))))

(def blog-routes
  ["/" [["blog/" [[:get [["multi-card-boxed" (handler [] (layout/render "blog/multi-card-boxed.html" multi-card-context-map))]
                         ["multi-card-side" (handler [] (layout/render "blog/multi-card-side.html" multi-card-context-map))]
                         ["multi-full" (handler [] (layout/render "blog/multi-full.html" multi-context-map))]
                         ["multi-side" (handler [] (layout/render "blog/multi-side.html" (assoc multi-context-map
                                                                                           :posts (e/get-latest-posts))))]
                         [["single-full/" :id] (handler [id] (layout/render "blog/single-full.html" (assoc single-context-map
                                                                                                      :post (e/get-post-by-id id))))]
                         [["single-side/" :id] (handler :post-id [id] (layout/render "blog/single-side.html"
                                                                                     (assoc single-context-map :post (->>> (e/get-post-by-id id)
                                                                                                                           (assoc _
                                                                                                                             :content (site.utils.markdown/markdown-to-html (:content _)))))))]
                         [[:url-title] (handler :post [url-title]
                                         (layout/render "blog/single-side.html"
                                                        (assoc single-context-map :post (e/get-post-by-title url-title))))]]]
                  ["admin/" [["post" [[:get (handler :post-page []
                                              (layout/render "blog/write-post.html" {:image-upload-url (bd/path-for site.layout/routes :upload-image)}))]
                                      [:post (handler :post-do [:as reqmap]
                                               (handle-new-post reqmap))]]]
                             ["upload-image" [[:post (handler :upload-image [:as reqmap]
                                                       (handle-image-upload reqmap))]]]]]]]

        ["author/" [[:get [[[[#"\d+" :id]] (handler :author-id [id]
                                             {:body (str "author: " id)})]
                           [[:name] (handler :author [name]
                                      {:content-type "text/html"
                                       :body         (str "author name: " name)})]]]]]]])