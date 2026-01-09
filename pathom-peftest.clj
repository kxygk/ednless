(use 'clojure.math)

(def
  xy-points
  "Just a long list of `[x y]` pairs"
  (repeatedly 1000000
              #(vector (rand)
                       (rand))))
#_
(take 5
      xy-points)
;; => ([0.9595777815722415 0.955966600109044]
;;     [0.4582556879334392 0.4586413232063461]
;;     [0.07833332358513934 0.07130513464892763]
;;     [0.7655731396104173 0.37268965701951307]
;;     [0.40480720580295426 0.3954351287740743])

(defn
  to-polar
  "Convert the `[x y]` pairs to `[radius angle]` pairs"
  [[x
    y]]
  [ (sqrt (+ (pow x   ;;radius
                  2)
             (pow y
                  2)))
   (atan2 y
          x) ])
#_
(->> xy-points
     (mapv to-polar)
     (take 5))
;; => ([1.354496828867144 0.7835129670951717]
;;     [0.6483441515706128 0.7858187507103993]
;;     [0.10592701171653926 0.738464855959417]
;;     [0.8514692082173457 0.4530410919554245]
;;     [0.5658955866045997 0.7736871501622257])

(def xy-points-normalized
  "Convert my `[x y]` pairs to a normalized vector
  Also `[x y]`"
  (let [polar-coords (->> xy-points
                          (mapv to-polar))]
    (mapv (fn [[x
                y]
               [radius
                _]]
            (vector (/ x
                       radius)
                    (/ y
                       radius)))
          xy-points
          polar-coords)))
#_
(take 5
      xy-points-normalized)
;; => ([0.9063464150748226 0.4225354137596247]
;;     [0.9724893343242329 0.23294740742410108]
;;     [0.3622086279586388 0.9320970495781651]
;;     [0.6496713506235731 0.7602151907051993]
;;     [0.39979032478244364 0.916606620208663])



;; Now I want to reimagine this with Pathom

(add-libs {'com.wsscode/pathom3 {:mvn/version "2025.01.16-alpha"}})

(require
  '[com.wsscode.pathom3.connect.operation :as pco]
  '[com.wsscode.pathom3.connect.planner :as pcp]
  '[com.wsscode.pathom3.interface.eql :as pie]
  '[com.wsscode.pathom3.connect.indexes :as pci]
  '[com.wsscode.pathom3.interface.smart-map :as psm])

(pco/defresolver $radius
  "Take `::x` and `::y` values and use pythagoras to return a `::radius`"
  [{::keys [x
            y]}]
  {::radius (sqrt (+ (pow x   ;;radius
                          2)
                     (pow y
                          2)))})
#_
($radius {::x 3.0
          ::y 5.0})
;; => #:user{:radius 5.830951894845301}

(pco/defresolver $angle
  "Take `::x` and `::y` values and return an `::angle`"
  [{::keys [x
            y]}]
  {::angle (atan2 y
                  x)})
#_
($angle {::x 3.0
         ::y 5.0})
;; => #:user{:angle 1.0303768265243125}

(pco/defresolver $normalized-point
  "L2 norm.. divide x y points by their length"
  [{::keys [x
            y
            radius]}]
  {::x-norm (/ x
               radius)
   ::y-norm (/ y
               radius)})

#_
($normalized-point {::x      3.0
                    ::y      5.0
                    ::radius 2.0})
;; => #:user{:x-norm 1.5, :y-norm 2.5}
;; here the radius can only be infered with the full `register`
;; so for testing you need to supply the values
#_
(::x-norm  (psm/smart-map (pci/register [$radius
                                         $angle
                                         $normalized-point
                                         $polar-data
                                         $normalized-data])
                          {::x 3.0
                           ::y 4.0}))
;; => 0.6  ;; this is 3.0/5.0 (think 3,4,5 triangle)



(def xy-point-maps
  "Now I remake the x-y data as labeled maps
  So the x and y are explicitely labeled"
  (repeatedly 10000000
              #(hash-map ::x
                         (rand)
                         ::y
                         (rand))))
#_
(take 5
      xy-point-maps)
;; => (#:user{:y 0.36939725673492985, :x 0.9602380622865435}
;;     #:user{:y 0.16960025245899446, :x 0.8414923599000971}
;;     #:user{:y 0.23689244515168162, :x 0.3515261363669846}
;;     #:user{:y 0.41155488019829933, :x 0.27518489053192907}
;;     #:user{:y 0.04144667976964722, :x 0.7061907847196561})

(pco/defresolver $polar-data
  "Converts a lists of xy point maps to
   a list of polar coordinates"
  [{::keys [cartesian-data]}]
  {::pco/input [{::cartesian-data [::radius
                                   ::angle ]}]}
  {::polar-data (mapv (fn [{::keys [radius
                                    angle]}]
                        {::radius radius
                         ::angle  angle}) ;; maybe superfluous
                      cartesian-data)})
#_
($polar-data {::cartesian-data [{::radius 3.0
                                 ::angle  4.0}]})
;; => #:user{:polar-data [{:radius 3.0, :angle 5.0}]}
#_
($polar-data {::cartesian-data [{::x 3.0
                                 ::y 4.0}]})
;; => #:user{:polar-data [{:radius nil, :angle nil}]}
;; Doesn't work b/c it needs the register!

(pco/defresolver $normalized-data
  [{::keys [cartesian-data]}]
  {::pco/input [{::cartesian-data [::x-norm
                                   ::y-norm]}]}
  {::normalized-data (mapv (fn [{::keys [x-norm
                                         y-norm]}]
                             {::x-norm x-norm
                              ::y-norm y-norm}) ;; maybe superfluous
                           cartesian-data)})

;;     {:y 0.9528055634606857, :x 0.9674033979599416})

(pco/defresolver $angular-average
  "Converts a lists of xy point maps to
   a list of polar coordinates"
  [{::keys [cartesian-data]}]
  {::pco/input [{::cartesian-data [::angle]}]}
  {::angular-average (/ (reduce (fn [sum
                                     next-point]
                                  (+ sum
                                     (::angle next-point)))
                                0.0
                                cartesian-data)
                        (count cartesian-data))})


(def env (pci/register [$radius
                        $angle
                        $normalized-point
                        $polar-data
                        $normalized-data
                        $angular-average]))

(def smap (psm/smart-map env
                         {::cartesian-data xy-point-maps}))

#_
(take 5
      (::normalized-data smap))
;; => (#:user{:x-norm 0.08712330380715341, :y-norm 0.9961975355991032}
;;     #:user{:x-norm 0.47009242740808693, :y-norm 0.8826171931780916}
;;     #:user{:x-norm 0.7068921332233743, :y-norm 0.7073213640113715}
;;     #:user{:x-norm 0.8241905340531331, :y-norm 0.5663126023471587}
;;     #:user{:x-norm 0.9010577036764886, :y-norm 0.4336992214026367})

#_
(take 5
      (::polar-data smap))
;; => ({:radius 0.2674784113124991, :angle 1.4835624270007897}
;;     {:radius 0.9248188165128844, :angle 1.0814008320003392}
;;     {:radius 0.7074320549968458, :angle 0.7857016754029951}
;;     {:radius 0.9759277453181741, :angle 0.602024979576675}
;;     {:radius 0.28699758977324435, :angle 0.4485941613968548})


(def env2 (pci/register [$radius
                        $angle
                        $normalized-point
                        $polar-data
                        $normalized-data
                        $angular-average]))


(defonce plan-cache* (atom {}))

(def env3 (-> (pci/register [$radius
                        $angle
                        $normalized-point
                        $polar-data
                        $normalized-data
                             $angular-average])
              (pcp/with-plan-cache plan-cache*)))


(println "`process` EQL query direct `angular average` calculation")

(time 
(/ (reduce (fn [sum
                next-point]
             (+ sum
                (::angle next-point)))
           0.0
           (-> env2
               (pie/process {::cartesian-data xy-point-maps}
                            [{::cartesian-data [::angle]}])
               ::cartesian-data))
   (count xy-point-maps))
)



(println "Normal smart map `angular average` calculation")
(time
(::angular-average smap)
)


(println "`process` EQL query direct `angular average` calculation")

(time 
(/ (reduce (fn [sum
                next-point]
             (+ sum
                (::angle next-point)))
           0.0
           (-> env2
               (pie/process {::cartesian-data xy-point-maps}
                            [{::cartesian-data [::angle]}])
               ::cartesian-data))
   (count xy-point-maps))
)



(println "`process` EQL query direct `angular average` calculation - with CACHE")

(time 
(/ (reduce (fn [sum
                next-point]
             (+ sum
                (::angle next-point)))
           0.0
           (-> env3
               (pie/process {::cartesian-data xy-point-maps}
                            [{::cartesian-data [::angle]}])
               ::cartesian-data))
   (count xy-point-maps))
)



(println "Normal EQL `angular average` fetch with CACHE")
(time
  (-> env3
      (pie/process  {::cartesian-data xy-point-maps}
                    [::angular-average]))
)




(println "Normal EQL `angular average` fetch with NO CACHE")
(time
  (-> env2
      (pie/process  {::cartesian-data xy-point-maps}
                    [::angular-average]))
)

;; (pco/defresolver $some-fancy-plotting
;;   [{::keys [cartesian-data
;;             angular-average]}]
;;   ::pco/input [{::cartesian-data [::x-norm
;;                                   ::y-norm
;;                                   :radius
;;                                   :angle]}]
;;   {::svg-hiccup (my-fancy-plotting-function cartesian-data
;;                                             angular-average)})
