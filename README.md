# novelette-text

Novelette-text is a simple ClojureScript library inspired by [CanvasText](http://canvastext.com/) to provide a simple and fast collection of functions to render text on an HTML5 canvas with different styles.

Font rendering on HTML5 canvas can be very tricky, proper font wrapping and different styling can give performance issues especially in 2D videogames.
This library tries to make the developer's life easier by providing simple and efficient functions for such common tasks. 
It is especially aimed for HTML5 game development on canvas.

## Usage

[![Clojars Project](http://clojars.org/novelette-text/latest-version.svg)](http://clojars.org/novelette-text)

Simply add the dependency to your *project.clj* file.

Import the namespace to your project:
```clojure
(ns my-project.core
  (:require [novelette-text.renderer]))
```

Initialize the renderer by providing your canvas surface elementId:
```clojure
(let [renderer (novelette-text.renderer/create-renderer "surface")]
  ; ... Do your rendering here!
  )
```

### Create your own font class:

```clojure
(let [font-class (novelette-text.renderer/create-font-class 
                   {:font-family "Verdana"
                    :font-weight "normal"
                    :font-size "12px"
                    :color "#000"
                    :font-style "normal"
                    :text-align "start"
                    :text-baseline "alphabetic"
                    :line-spacing "5"
                    :text-shadow nil})]
  ; ... 
  )
```

Or alternatively pass an empty map to use the default values:
```clojure
(let [default-font-class (novelette-text.renderer/create-font-class {})]
  ; ....
  )
```

Those two calls are identical as those are the given default font values in the library.

### Render some text:

```clojure
(novelette-text.renderer/draw-text "Hello World!" [100 100] 500 font-class renderer)
```

This will print "Hello World!" starting at coordinates (100, 100) on the canvas and will auto-wrap at 500 width.
If you plan to render the same text very often (like in a game loop), you can provide an ID to cache the render call and make successive calls much faster:
```clojure
(novelette-text.renderer/draw-text "Cached Hello World!" [100 100] 500 font-class :my-cache-id true renderer) 
```

Once you're done you can empty the cache:
```clojure
(novelette-text.renderer/clear-cache! renderer)
```

### Style your text:

Novelette-text also supports inline text styling and newline tags like such:
```clojure
(let [my-text "Hello! <style='font-weight:bold;color:#00FF00'>This text is bold and green</style><br />This is on a newline and <style='font-size:20px;color:#FF000'>this is bigger and red!</style>"]
  (novelette-text.renderer/draw-text my-text [100 100] 500 font-class renderer))
```

It's that easy!

## License

Copyright © 2015 Federico 'Morg' Pareschi

Distributed under the MIT Free Software License.
