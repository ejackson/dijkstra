(ns
    #^{:doc "Basic Routing Example"}
  net.edmundjackson.dijkstra
  (:import java.lang.Math)
  (:use [clojure.set :as set])
  (:use [clojure.contrib.seq-utils :only (find-first)]))

;;-----------------------------------------------------------------------
(defprotocol Metric
  (distance [{x1 :x y1 :y} {x2 :x y2 :y}]))

(defrecord Point [x y]
  Metric
  (distance [{x1 :x y1 :y} {x2 :x y2 :y}]
	    (let [dx (- x2 x1)
		  dy (- y2 y1)]
	      (Math/sqrt (+ (* dx dx) (* dy dy))))))

;;-----------------------------------------------------------------------
(def vertex-id (atom 0))
(def edge-id (atom 0))

(defprotocol Joinable
  (join [v1 v2 e])
  (disjoin [v1 v2]))

(defrecord Vertex [id value neighbours]
  Joinable
  (join [this v e]
	(assoc-in this
		  [:neighbours]
		  (assoc (:neighbours this) (:id v) e)))
  (disjoin [this v]
	   (assoc-in this
		     [:neighbours]
		     (dissoc (:neighbours this) (:id v)))))
(defn vertex [value]
  (Vertex. (swap! vertex-id inc) value {}))

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
  (connect [graph v1 v2 val])
  (disconnect [graph v1 v2]))

;; vertices is a map from vertex-id to vertex (for O(1) lookup
(defrecord Graph [vertices]
  Graphable

  (add-vertex [this vertex]
	      (Graph. (assoc vertices (:id vertex) vertex)))

  (get-vertex [this v-id]
	      (-> v-id vertices val))

  (connect [this v1-id v2-id value]
	   (let [v1 (.get-vertex this v1-id)
		 v2 (.get-vertex this v2-id)]
	   (Graph.
	    (-> vertices
		(assoc v1-id (join v1 v2 value))
		(assoc v2-id (join v2 v1 value))))))

  (disconnect [this v1-id v2-id]
	   (let [v1 (.get-vertex this v1-id)
		 v2 (.get-vertex this v2-id)]
	     (Graph.
	      (-> vertices
		  (assoc v1-id (disjoin v1 v2))
		  (assoc v2-id (disjoin v2 v1))))))

  (rem-vertex [this vertex-id]
	      ;; First remove all the edges
	      (let [vertex-to-remove     (vertices vertex-id)
		    connected-vertex-ids (keys (:neighbours vertex-to-remove))
		    edges-removed        (reduce (fn [g v]
						   (disconnect g (vertices v) vertex-to-remove))
						 this
						 connected-vertex-ids)]
		(Graph. (-> edges-removed :vertices (dissoc vertex-id))))))

(defn graph []
  (Graph. {}))

(def v1 (vertex (Point. 0 0)))
(def v2 (vertex (Point. 1 0)))
(def v3 (vertex (Point. 0 1)))
(def v4 (vertex (Point. 1 1)))

(-> (graph)
    (.add-vertex v1)
    (.add-vertex v2)
    (.add-vertex v3)
    (.connect (:id v1) (:id v2) 20)
    (.connect (:id v2) (:id v3) 10)
    (.connect (:id v1) (:id v3) 5))

