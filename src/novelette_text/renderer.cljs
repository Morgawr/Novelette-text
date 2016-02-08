(ns novelette-text.renderer
  (:require-macros [schema.core :as s])
  (:require [clojure.string :as string]
            [schema.core :as s]
            [goog.dom :as dom]))

(def outer-tag-match (js/RegExp. "<\\s*br\\s*\\/>|<\\s*style=[\"|']([^\"|']+)[\"|']\\s*\\>([^>]+)<\\s*\\/style\\s*\\>|[^<]+" "g"))
(def inner-tag-match (js/RegExp. "<\\s*style=[\"|']([^\"|']+)[\"|']\\s*\\>([^>]+)<\\s*\\/style\\s*\\>"))

(s/defrecord Cache
  [canvas :- js/HTMLCanvasElement
   context :- js/CanvasRenderingContext2D])

(s/defrecord Renderer
  [canvas :- js/HTMLCanvasElement
   context :- js/CanvasRenderingContext2D
   buffer-canvas :- js/HTMLCanvasElement
   buffer-context :- js/CanvasRenderingContext2D
   cache-map :- s/Any ;(s/atom {s/Keyword Cache})
   ])

(s/defrecord FontClass
  [font-family :- s/Str
   font-weight :- s/Str
   font-size :- s/Str
   color :- s/Str
   font-style :- s/Str
   text-align :- s/Str
   text-baseline :- s/Str
   line-spacing :- s/Str
   text-shadow :- (s/maybe s/Str)])

(s/defn create-font-class
  "Create a new font class merging the given attributes with the default ones."
  ([] (create-font-class {}))
  ([attributes]
   (merge (FontClass. "Verdana"
                      "normal"
                      "12px"
                      "#000"
                      "normal"
                      "start"
                      "alphabetic"
                      "5"
                      nil) attributes)))

(s/defn create-renderer
  "Create a new text renderer given the target canvas's id in the document."
  [canvas-id :- s/Str]
  (let [document (dom/getDocument)
        canvas (dom/getElement canvas-id)
        ctx (.getContext canvas "2d")
        buffer-canvas (.createElement document "canvas")
        buffer-context (.getContext buffer-canvas "2d")]
    (set! (.-width buffer-canvas) (.-width canvas))
    (set! (.-height buffer-canvas) (.-height canvas))
    (Renderer. canvas ctx buffer-canvas buffer-context (atom {}))))

(s/defn is-cached?
  "Return whether or not the given ID is already cached in the renderer."
  [id :- s/Keyword
   renderer :- Renderer]
  (not (nil? (id @(:cache-map renderer)))))

(s/defn cache!
  "Cache the given ID in the renderer by drawing the buffer into the cache."
  [id :- s/Keyword
   renderer :- Renderer]
  (let [document (dom/getDocument)
        canvas (.createElement document "canvas")
        ctx (.getContext canvas "2d")]
    (set! (.-width canvas) (.-width (:buffer-canvas renderer)))
    (set! (.-height canvas) (.-height (:buffer-canvas renderer)))
    (.drawImage ctx (:buffer-canvas renderer) 0 0)
    (swap! (:cache-map renderer) assoc id (Cache. canvas ctx))))

(s/defn get-cached
  "Return the cached canvas."
  [id :- s/Keyword
   renderer :- Renderer]
  (id @(:cache-map renderer)))

(s/defn clear-cache!
  "Clear all the cache in the renderer."
  [renderer :- Renderer]
  (reset! (:cache-map renderer) {}))

(s/defn need-line-break?
  "Check whether or not the given line of text breaks the width bounds."
  [text :- s/Str
   text-origin-x :- s/Int
   block-origin-x :- s/Int
   width :- s/Int
   context :- js/CanvasRenderingContext2D]
  (let [size1 (+ (.-width (.measureText context text)) text-origin-x)
        size2 (+ width block-origin-x)]
    (> size1 size2)))

(s/defn get-text-height
  "Approximation of text height by measuring the width of the letter M."
  [context :- js/CanvasRenderingContext2D]
  (Math/floor (.-width (.measureText context "M"))))

(s/defn draw-styled-text
  "Draw the given text in the canvas and render any given style tags."
  [text :- s/Str
   position :- [(s/one s/Int :x) (s/one s/Int :y)]
   max-width :- s/Int
   class :- FontClass
   {:keys [context
           buffer-context
           buffer-canvas] :as renderer}:- Renderer]
  (.clearRect ; We clear the dirty data from previous drawings
    buffer-context 0 0 (.-width buffer-canvas) (.-height buffer-canvas))
  (let [matches (.match text outer-tag-match)
        font-class (atom class)  ; These are stateful definitions because it
        curr-pos (atom position) ; looks better if we add a bit of contained
        to-print (atom "")       ; state in this case ;_; sorry
        groups (atom [])
        token-counter (atom 0)]
    (doseq [m matches]
      (reset! font-class class)
      (reset! to-print "")
      (swap! token-counter inc)
      (.save buffer-context)
      (cond
        (.test (js/RegExp. "<\\s*style=" "i") m)
        (let [[_ p mm] (.match m inner-tag-match)
              prop-list (map #(string/split % #":") (string/split p #";"))]
          (doseq [[k v] prop-list]
            (swap! font-class assoc (keyword k) v))
          (reset! to-print mm))
        (.test (js/RegExp. "<\\s*br\\s*" "i") m)
        (swap! groups conj {:new-line true})
        :else
        (reset! to-print m))
      ; We need to intantiate the context so we can properly measure strings
      (set! (.-textBaseline buffer-context) (:text-baseline @font-class))
      (set! (.-textAlign buffer-context) (:text-align @font-class))
      (set! (.-font buffer-context) (str (:font-style @font-class) " "
                                         (:font-weight @font-class) " "
                                         (:font-size @font-class) " "
                                         (:font-family @font-class) " "))
      (set! (.-fillStyle buffer-context) (:color @font-class))
      (when (:text-shadow @font-class)
        (let [shadow (map #(string/replace % #"px" "")
                          (string/split (:text-shadow @font-class) #" "))]
          (set! (.-shadowOffsetX buffer-context) (shadow 0))
          (set! (.-shadowOffsetY buffer-context) (shadow 1))
          (set! (.-shadowBlur buffer-context) (shadow 2))
          (set! (.-shadowColor buffer-context) (shadow 3))))
      (when-not (empty? @to-print)
        (let [split-words (string/split (string/trim @to-print) #" ")]
          (doseq [word split-words]
            (let [latest-group (last @groups)
                  line-height (+ (get-text-height buffer-context)
                                 (js/parseInt (:line-spacing class) 10))]
              (cond
                (nil? latest-group)
                (swap! groups conj
                       {:text word
                        :class @font-class
                        :position @curr-pos
                        :counter @token-counter
                        :length (.-width (.measureText buffer-context word))})
                (:new-line latest-group)
                (do
                  (reset! curr-pos [(first position)
                                    (+ (second @curr-pos) line-height)])
                  (swap! groups conj
                         {:text word
                          :class @font-class
                          :position @curr-pos
                          :counter @token-counter
                          :length (.-width
                                    (.measureText buffer-context word))}))
                (not (= (:counter latest-group) @token-counter))
                (if (need-line-break?
                      (str " " word)
                      (+ (:length latest-group)
                         (first (:position latest-group)))
                      (first position) max-width buffer-context)
                  (do
                    (reset! curr-pos [(first position)
                                      (+ (second @curr-pos) line-height)])
                    (swap! groups
                           conj {:text word
                                 :class @font-class
                                 :position @curr-pos
                                 :counter @token-counter
                                 :length (.-width
                                           (.measureText
                                             buffer-context word))}))
                  (do
                    (reset! curr-pos [(+ (first (:position latest-group))
                                         (:length latest-group))
                                      (second @curr-pos)])
                    (swap! groups conj {:text (str " " word)
                                        :class @font-class
                                        :position @curr-pos
                                        :counter @token-counter
                                        :length (.-width (.measureText
                                                           buffer-context
                                                           (str " " word)))})))
                (need-line-break?
                  (str " " word)
                  (+ (:length latest-group) (first (:position latest-group)))
                  (first position) max-width buffer-context)
                (do
                  (reset! curr-pos [(first position)
                                    (+ (second @curr-pos) line-height)])
                  (swap! groups
                         conj {:text word
                               :class @font-class
                               :position @curr-pos
                               :counter @token-counter
                               :length (.-width
                                         (.measureText buffer-context word))}))
                :else
                (swap! groups assoc (dec (count @groups))
                       (merge latest-group
                              {:text (str  (:text latest-group) " " word)
                               :length (+ (:length latest-group)
                                          (.-width
                                            (.measureText
                                              buffer-context
                                              (str " "  word))))})))))))
      (.restore buffer-context))
    (doseq [g @groups]
      (let [new-line (:new-line g)
            font-class (:class g)
            text (:text g)
            position (:position g)]
        (.save buffer-context)
        (when-not new-line
          (set! (.-textBaseline buffer-context) (:text-baseline font-class))
          (set! (.-textAlign buffer-context) (:text-align font-class))
          (set! (.-font buffer-context) (str (:font-style font-class) " "
                                             (:font-weight font-class) " "
                                             (:font-size font-class) " "
                                             (:font-family font-class) " "))
          (set! (.-fillStyle buffer-context) (:color font-class))
          (when (:text-shadow font-class)
            (let [shadow (map #(string/replace % #"px" "")
                              (string/split (:text-shadow font-class) #" "))]
              (set! (.-shadowOffsetX buffer-context) (shadow 0))
              (set! (.-shadowOffsetY buffer-context) (shadow 1))
              (set! (.-shadowBlur buffer-context) (shadow 2))
              (set! (.-shadowColor buffer-context) (shadow 3))))
          (.fillText buffer-context text (first position) (second position)))
        (.restore buffer-context)))
    (.drawImage context buffer-canvas 0 0)))

(s/defn draw-text
  "Draw the given text in the canvas and possibly cache it."
  ([text :- s/Str
    position :- [(s/one s/Int :x) (s/one s/Int :y)]
    max-width :- s/Int
    class :- FontClass
    renderer :- Renderer]
   (draw-text text position max-width class :no-id false renderer))
  ([text :- s/Str
    position :- [(s/one s/Int :x) (s/one s/Int :y)]
    max-width :- s/Int
    class :- FontClass
    id :- s/Keyword
    cached? :- s/Bool
    {:keys [context] :as renderer}:- Renderer]
   (if (and cached? (is-cached? id renderer))
     (.drawImage context (:canvas (get-cached id renderer)) 0 0)
     (draw-styled-text text position max-width class renderer))
   (when (and cached? (not (is-cached? id renderer)))
     (cache! id renderer))))
