(ns
    #^{:doc "Direct A-star implementation with supporting data types."}
  net.edmundjackson.dijkstra
  (:use compojure.core)  
  (:use clojure.contrib.json)
  (:import java.lang.Math))

;;-----------------------------------------------------------------------
(defprotocol Metric
  (distance [{x1 :x y1 :y} {x2 :x y2 :y}]))

(defrecord Point [x y]
  Metric
  (distance [{x1 :x y1 :y} {x2 :x y2 :y}]
	    (let [dx (- x2 x1)
		  dy (- y2 y1)]
	      (Math/sqrt (+ (* dx dx) (* dy dy))))))
(defn distance [x y] (.distance x y))

;;-----------------------------------------------------------------------
(def vertex-id (atom 0))
(def edge-id (atom 0))

(defprotocol Joinable
  (join [v1 v2 e])
  (disjoin [v1 v2])
  (edge [v1 v2])
  (neighbours [v1]))

;; Syntax note: adjoining is the set of of vertices that have an edge
;; connecting to this verticex. Neighbours is the set of vertices that
;; have an edge starting on this vertex and ending on it. That is
;; neighbours is a subset of vertices. An adjoining node which only has
;; an edge in the opposite direction has value nil.
(defrecord Vertex [id value adjoining]
  Joinable
  (join [this v e]
	(assoc-in this
		  [:adjoining]
		  (assoc (:adjoining this) (:id v) e)))
  (disjoin [this v]
	   (assoc-in this
		     [:adjoining]
		     (dissoc (:adjoining this) (:id v))))

  ;; Value of an edge between v1 and v2
  (edge [this v2]
	(adjoining (:id v2)))

  ;; See note above
  (neighbours [v1]
	      (into {} (filter val adjoining))))

(defn vertex [value]
  (Vertex. (-> (swap! vertex-id inc) str keyword)  value {}))

(defn join [v1 v2 e]
  (.join v1 v2 e))
(defn disjoin [v1 v2]
  (.disjoin v1 v2))

;;--------------------------------------------------------------------
(defprotocol Graphable
  ;; Connect vertex1 to vertex2 with a director (1 to 2) value of value
  (add-vertex [graph vertex])
  (get-vertex [graph vertex])
  (rem-vertex [graph vertex])
  (connect    [graph v1 v2 val])
  (disconnect [graph v1 v2]))

;; vertices is a map from vertex-id to vertex (for O(1) lookup
(defrecord Graph [stub]
  Graphable
  (add-vertex [this vertex]
	      (assoc this (:id vertex) vertex))
  
  (get-vertex [this v-id]
	      (get this v-id))

  ;; DIRECTED connected of two vertices
  ;; val-r is the value to be inserted for the reverse edge, ie from v2 to v1
  ;; it is either left as it is, or set to nil if none yet exists
  (connect [this v1-id v2-id value]
	   (let [v1    (.get-vertex this v1-id)
		 v2    (.get-vertex this v2-id)
		 val-r (.edge v2 v1)]
	     (-> this
		 (assoc v1-id (join v1 v2 value))
		 (assoc v2-id (join v2 v1 val-r)))))
  
  (disconnect [this v1-id v2-id]
	      (let [v1 (.get-vertex this v1-id)
		    v2 (.get-vertex this v2-id)]
		(-> this
		    (assoc v1-id (disjoin v1 v2))
		    (assoc v2-id (disjoin v2 v1)))))
  
  (rem-vertex [this vertex-id]
	      ;; First remove all the edges, then the vertex itself
	      (let [vertex-to-remove     (vertex-id this)
		    connected-vertex-ids (keys (:adjoining vertex-to-remove))
		    edges-removed        (reduce
					  (fn [g v]
					    (disconnect g v vertex-id))
					  this
					  connected-vertex-ids)]
		(dissoc edges-removed vertex-id))))

;;----------------------------------------------------------------------
;; Algorithm for finding shortest path in graph from v1 to v2 in a graph
(defrecord AStar [vertex f g h parent-astar])

;; Construct vertex into an star-type.
(defn to-astar [heuristic vertex parent-astar]
  (let [g      (if (nil? (:g parent-astar))
		 0
		 (+ (:g parent-astar) (.edge (:vertex parent-astar) vertex)))
	h (heuristic vertex)
	f (+ g h)]
      (AStar. vertex f g h parent-astar)))

;; Reconstruct the path to the origin from an astar node
(defn path-to-origin-iter [astar-node path]
  (let [parent-node (:parent-astar astar-node)]
    (if (nil? astar-node)
      path
      (recur parent-node (conj path (-> astar-node :vertex :id))))))

(defn path-to-origin [astar-node]
  (path-to-origin-iter astar-node '()))

;; Going for scheme-like solution first
(defn a-star-iter [g open closed ast]
  (let [astar-vertex (first open)
	neighbours  (.neighbours (:vertex astar-vertex))
	new-closed  (conj closed astar-vertex)
	admissable? #(not (new-closed %))
	new-open    (reduce
		     (fn [o [v _]]
		       (let [new-astar (ast (v g) astar-vertex)]
			 (if (admissable? new-astar)
			   (conj o new-astar)
			   o)))
		     (disj open astar-vertex)
		     neighbours)]
    (if (= (:h astar-vertex) 0.0)
      astar-vertex
      (if (empty? new-open)
	astar-vertex
	(recur g new-open new-closed ast)))))

;; NB argument heuristic is 2-ary, that we bind in here to 1-ary
(defn a-star [g v-origin-id v-goal-id heuristic]
  (let [v-origin (v-origin-id g)
	v-goal  (v-goal-id g)
	h       (fn [x] (heuristic (:value x) (:value v-goal)))
	ast     (partial to-astar h)]
    (a-star-iter g
		 (sorted-set-by
		  (comparator (fn [x y] (<  (:f x) (:f y))))
		  (ast v-origin nil))
		 (set nil)
		 ast)))

(defn graph []
  (Graph. {}))

(def v1 (vertex (Point. 0 0)))
(def v2 (vertex (Point. 1 0)))
(def v3 (vertex (Point. 0 1)))
(def v4 (vertex (Point. 1 1)))

(def g1
     (-> (graph)
	 (.add-vertex v1)
	 (.add-vertex v2)
	 (.add-vertex v3)
	 (.connect (:id v1) (:id v2) 20)
	 (.connect (:id v2) (:id v3) 10)
	 (.connect (:id v1) (:id v3) 50)))

(defn find-cycle-route [origin destination]
  (json-str (path-to-origin (a-star g1 (keyword origin) (keyword destination) distance))) )
;;--------------------------------------------------------------------------------

