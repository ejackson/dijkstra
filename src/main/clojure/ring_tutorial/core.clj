(ns ring-tutorial.core
  (:use compojure.core)
  (:use ring.middleware.reload)
  (:use ring.middleware.stacktrace)
  (:use ring.adapter.jetty)
  (:use hiccup.core)
  (:use hiccup.page-helpers)
  (:use net.edmundjackson.dijkstra))

(defn view-layout [& content]
  (html
   (doctype :xhtml-strict)
   (xhtml-tag "en"
	      [:head
	       [:meta {:http-equiv "Content-type"
		       :content "text/html; charset=utf-8"}]
	       [:title "A-Star Demo"]]
	      [:body content])))

(defroutes handler
  (GET "/route" [origin destination]
       (view-layout (find-cycle-route origin destination))))

(def app
     (-> #'handler
	 (wrap-reload '[ring-tutorial.core])
	 (wrap-stacktrace)))

(defn boot [port]
  (run-jetty #'app {:port port}))


