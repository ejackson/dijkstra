(ns net.edmundjackson.astar
  (:use clojure.contrib.json)
  (:use compojure.core
	compojure.route
	ring.adapter.jetty
	ring.middleware.reload
	ring.middleware.stacktrace
	        [clojure.contrib.http.agent :exclude (bytes)])
  (:import java.lang.Math))

;;-----------------------------------------------------------------------
(defn distance [{x1 :x y1 :y} {x2 :x y2 :y}]
  (let [dx (- x2 x1)
	dy (- y2 y1)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

;; Need a map with keys: id, value, adjoining
(defn join [this {id :id} e]
  (assoc-in this [:adjoining id] e))

;; Value of an edge between v1 and v2
(defn edge [{adjoining :adjoining} {id :id}]
  (adjoining id))

;;------------------------------------------------------------------------
;; Return the graph with v1 and v2 connected by an edge
;; of value edge
(defn connect [graph v1-id v2-id e]
  (let [v1    (graph v1-id)
	v2    (graph v2-id)]
    (merge graph
	   {v1-id (join v1 v2 e)}
	   {v2-id (join v2 v1 (edge v2 v1))})))

;; =======================================================================
;; The algorithm bit
;; Construct an astar-node
;; New superfluous comment
(defn astar-node [heuristic vertex parent-node]
  (let [g (if (nil? (:g parent-node)) 0
	      (+ (:g parent-node) (edge (:vertex parent-node) vertex)))
	h (heuristic vertex)]
    {:f (+ g h),:g g, :h h, :vertex vertex, :parent-node parent-node}))

(defn path-to-origin
  ([astar-node]
     (path-to-origin astar-node '()))
  ([astar-node path]
     (if (nil? astar-node) path
	 (recur (:parent-node astar-node) (conj path (-> astar-node :vertex :id))))))

(defn a-star-iter [g open closed node]
  (let [current      (first open)
	neighbours   (into {} (filter val (-> current :vertex :adjoining)))
	new-closed   (conj closed current)
	admissable?  #(not (new-closed %))
	new-open     (reduce
		      (fn [o [v _]]
			(let [new-astar (node (v g) current)]
			  (if (admissable? new-astar)
			    (conj o new-astar)
			    o)))
		      (disj open current)
		     neighbours)]
    (if (= (:h current) 0.0)
      (path-to-origin current)
      (if (empty? new-open)
	(path-to-origin current)
	(recur g new-open new-closed node)))))

;; NB argument heuristic is 2-ary, that we bind in here to 1-ary
(defn a-star [g v-origin-id v-goal-id]
  (let [v-goal   (v-goal-id g)
	h        (fn [x] (distance x v-goal))
	node     (partial astar-node h)]
    (a-star-iter g
		 (sorted-set-by
		  (comparator (fn [x y] (<  (:f x) (:f y))))
		  (node (v-origin-id g) nil))
		 (set nil)
		 node)))

;;-----------------------------------------------------------------------
(def g
     (->
      {:1 {:id :1 :adjoining {} :x 0 :y 0}
       :2 {:id :2 :adjoining {} :x 1 :y 0}
       :3 {:id :3 :adjoining {} :x 0 :y 1}}
     (connect :1 :2 12)
     (connect :2 :3 23)
     (connect :1 :3 2)))

;;-------------------------------------------------------------------------
(defn kw-keys
  "Convert a map with string keywords to keywords"
  [in-map]
  (reduce
   (fn [m [k v]] (assoc m (keyword k) v))
   {}
   in-map))
  
;; -----------------=  REST Routing =-------------------
(defroutes main-routes
  (GET "/route" {query-params :query-params}
       (let [qp (kw-keys query-params)]
	 (str (a-star g (keyword (qp :origin)) (keyword (qp :destination)))))))

;; ------------------------------------------------------
;; Server endxo
(def app
     (-> #'main-routes
	 (wrap-reload '(net.edmundjackson.astar))
	 (wrap-stacktrace)))

(defn boot []
  (run-jetty #'app {:port 8083}))


#_ (string (http-agent "http://localhost:8083/journeys/?origin=here&destination=there"
		       :method "POST"))


#_ (string (http-agent "http://localhost:8083/route?origin=1&destination=2"))
