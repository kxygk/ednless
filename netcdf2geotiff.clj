;; Assumes GDAL is installed
;; `gdal_translate` and `gdalinfo` are available


(def
  era5-precip
  "The original values seem to be floating point values
   You can inspect them with `gdalinfo -stats /home/kxygk/Data/era5/precipitation-monthly.nc > netcdf.info`
   I got values for one band of example
   STATISTICS_MAXIMUM=0.055014610290527
   STATISTICS_MEAN=0.0023013708624162
   STATISTICS_MINIMUM=0
   In another line it specifies the values are in meters
   GRIB_units=m
   But the GeoTIFF will use UnsignedInt values
   So on the 0-65535 range:
   https://en.wikipedia.org/wiki/C_data_types
   So we need GDAL to rescale everything
   I'm having it scale to 0 to 0.65535 meters..
   to 0-65535
   So that the values are directly interpretable as in `nanometers`
   This should be more than enough precision.."
  {:netcdf-filestr "/home/kxygk/Data/sst/monthly/sst.mon.mean.nc"
   :output-dirstr  "/home/kxygk/Data/sst/monthly/"
   :input-min 0
   :input-max 0.065535 ;; more than the actual max
   :output-min 0       ;; but remaps cleanly to UInt16 vals
   :output-max 65535
   :netcdf-var "tp"})

(def
  sst
  " SST files from
    `https://psl.noaa.gov/data/gridded/data.noaa.oisst.v2.highres.html`
  Just following the era5 conversion from before
  `gdalinfo` indicated the min max is -3 to 45 (freezing point of liquid sea water?)"
  {:netcdf-filestr "/home/kxygk/Data/sst/monthly/sst.mon.mean.nc"
   :output-dirstr  "/home/kxygk/Data/sst/monthly/"
   :input-min -3 ;; from `ncview`
   :input-max 45 ;; indicated the min/max `valid-range`
   :output-min 0
   :output-max 65535
   :netcdf-var "sst"})

(let [{:keys [netcdf-filestr
              output-dirstr
              rescaling-vals
              input-min
              input-max
              output-min
              output-max
              netcdf-var]} sst
      ]
  (let [info    (->> netcdf-filestr
                     (clojure.java.shell/sh "gdalinfo")
                     second
                     second)
        by-band (clojure.string/split info
                                      #"\nBand")

        last-block-number (-> by-band
                              last
                              (clojure.string/split #" ")
                              second
                              read-string)
        blocks            (range 1
                                 (inc last-block-number))]
    (println (str "Total number of Blocks: "
                  last-block-number))
    (let [to-tiff (fn [block-num]
                    (str "gdal_translate "
                         "-ot UInt16 " ;; output unsigned 16 bit
                         (str "-scale "
                              input-min
                              " "
                              input-max
                              " "
                              output-min
                              " "
                              output-max) ;; rescale from internal max to 0-65535
                         " -b "
                         block-num
                         (str " NETCDF:"
                              netcdf-filestr
                              ":"
                              netcdf-var) ;; indicates temperature
                         (str " "
                              output-dirstr
                              "geotiff/block-"
                              (format "%04d"
                                      block-num)
                              ".tiff")))
          rotate (fn [block-num]
                   (str "gdalwarp"
                        " -s_srs "
                        "\"+proj=longlat +ellps=WGS84\""
                        " -t_srs WGS84 "
                        output-dirstr
                        "geotiff/block-"
                        (format "%04d"
                                block-num)
                        ".tiff"
                        " "
                        output-dirstr
                        "geotiff-rot/"
                        "block-"
                        (format "%04d"
                                block-num)
                        "-rot"
                        ".tiff"
                        "  -wo SOURCE_EXTRA=1000"
                        " --config CENTER_LONG 0"))]
      (println (str "Example to-geotiff converter:\n"
                    (to-tiff 1)))
      (println (str "Example rotation converter:\n"
                    (rotate 1)))
      (->> blocks
           (run! (fn [block-number]
                   (clojure.java.shell/sh "/bin/bash"
                                          "-c"
                                          (to-tiff block-number)))))
      (->> blocks
           (run! (fn [block-number]
                   (clojure.java.shell/sh "/bin/bash"
                                          "-c"
                                          (rotate block-number))))))))
;; => "gdalwarp -s_srs \"+proj=longlat +ellps=WGS84\" -t_srs WGS84 /home/kxygk/Data/era5/monthly/era5-geotiff-block-0001 /home/kxygk/Data/era5/monthly//rot/era5-geotiff-block-0001  -wo SOURCE_EXTRA=1000 --config CENTER_LONG 0"
