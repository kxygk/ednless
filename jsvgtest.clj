(add-libs {'com.github.weisj/jsvg {:mvn/version "1.7.0"}})

(import com.github.weisj.jsvg.parser.SVGLoader
        java.net.URI
        java.awt.image.BufferedImage
        java.io.File
        javax.imageio.ImageIO
        com.github.weisj.jsvg.parser.LoaderContext
        com.github.weisj.jsvg.parser.DocumentLimits
        java.awt.Color)

(def jsvg-loader
  (SVGLoader.))

(defn
  render-jpg
  [url-str
   file-str]
  (let [svg-doc     (.load jsvg-loader
                           (->> url-str
                                java.net.URL.)
                           (.documentLimits (LoaderContext/builder) (DocumentLimits. 99 99 99999)))
        doc-size    (-> svg-doc
                        .size)
        width       (-> doc-size
                        .width
                        int)
        height      (-> doc-size
                        .height
                        int)
        imgbuf      (BufferedImage. width
                                    height
                                    java.awt.image.BufferedImage/TYPE_INT_RGB)
        graphics2d  (.createGraphics imgbuf)
        output-file (File. file-str)]
    (.setColor graphics2d
               Color/WHITE)
    (.fillRect graphics2d
               0
               0
               width
               height)
    (.render svg-doc
             nil
             graphics2d)
    (ImageIO/write imgbuf
                   "jpg"
                   output-file)))


(defn
  render-png
  [url-str
   file-str]
  (let [svg-doc     (.load jsvg-loader
                           (->> url-str
                                java.net.URL.)
                           (.documentLimits (LoaderContext/builder) (DocumentLimits. 99 99 99999)))
        doc-size    (-> svg-doc
                        .size)
        imgbuf      (BufferedImage. (-> doc-size
                                        .width
                                        int)
                                    (-> doc-size
                                        .height
                                        int)
                                    java.awt.image.BufferedImage/TYPE_INT_ARGB)
        graphics2d  (.createGraphics imgbuf)
        output-file (File. file-str)]
    (time
    (.render svg-doc
             nil
             graphics2d))
    (ImageIO/write imgbuf
                   "png"
                   output-file)))

(render-jpg "https://raw.githubusercontent.com/wiki/kxygk/imergination/krabi-root-2/year0.svg"
            "year0.jpg")

(render-png "https://raw.githubusercontent.com/wiki/kxygk/imergination/krabi-root-2/year0.svg"
            "year0.png")

(render-jpg "https://raw.githubusercontent.com/wiki/kxygk/imergination/krabi-root-2/indeces.svg"
            "indeces.jpg")

(render-png "https://raw.githubusercontent.com/wiki/kxygk/imergination/krabi-root-2/indeces.svg"
            "indeces.png")
