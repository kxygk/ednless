(add-lib 'clj-curl/clj-curl {:mvn/version "1.1.2"})
(add-lib 'clj-curl/clj-curl {:mvn/version "1.1.2"})
(add-lib 'org.clojure/core.async {:mvn/version "1.8.741"})

(require '[clj-curl.easy :as curl-easy])
(require '[clj-curl.opts :as curl-opts])
(require '[clojure.core.async :as async :refer :all])

;; = `Rain`
;; == `IMERG`
;;  Now we want to read in gridded satellite rain data and overlay it on to our maps. FINAL Rain data can be retrieved from `arthurhou.pps.eosdis.nasa.gov` and requires an account with them
;;
;; The path on the server will look something like this:
;;
;; `ftp://arthurhou.pps.eosdis.nasa.gov/sm/730/gpmdata/2011/08/01/gis/3B-MO-GIS.MS.MRG.3IMERG.20110801-S000000-E235959.08.V06B.tif`
;;
;;
;; EARLY data (not normalized with rain gauges)
;; is available on a separate server:
;; `https://jsimpsonhttps.pps.eosdis.nasa.gov`
;; The registration is the same at:
;; `https://registration.pps.eosdis.nasa.gov/registration`
;; You just need to indicate you want access to *Near-Real Time (NRT)* data
;;
;; An in depth explanation of the data is in a PDF here: https://gpm.nasa.gov/resources/documents/imerg-geotiff-worldfile-documentation
;;
;; While the document focuses on the `GeoTIFF` format, it also provides all the necessary background to understand how everything fits together. It's a very good write up. Here I just provide a very short summary.
;;
;; Looking at the file/path in parts:
;;
;; sm/730/;: unclear
;; gpmdata/:: GPM is the current satellite system that synthesizes different data to give these precipitation maps
;; gis:: means GeoTIFF - b/c the original meteorology format is "HDF5" and it's not GIS-software-friendly
;;
;; Finally these folders are organized by days. Each day's folder will have 48 half-hour snapshots of the precipitation. These come in a format that looks like `3B-HHR-…`
;; Each day also has a *cumulative GeoTiff* will a sum of all the precipitation for that day. This file is named `3B-DAY-…`
;; The monthly precipitation is hidden away in the first day of the month's folder :). Its format will be `3B-MO-…` All the other days of the month won't have this file. Yeah... it took a while to figure that one out.
;;
;; The data can then be downloaded using https://github.com/curl/curl[CURL]. The username and passwords provided by the service is very inconvenient b/c they contain an `@` symbol, so you need to pass them in explicitely using the `--user` flag:
;;
;; [,sh]
;; curl -4 --ftp-ssl --user <<email>>:<<email>> ftp://arthurhouftps.pps.eosdis.nasa.gov/sm/730/gpmdata/2011/08/01/gis/3B-MO-GIS.MS.MRG.3IMERG.20110801-S000000-E235959.08.V06B.tif -o output.tif
;;
;;  We can do the same programmatically in Clojure

;; Based off of libary example:
;; https://github.com/lsevero/clj-curl/blob/master/examples/simple_ftp.clj
(import (clj_curl.Handlers FileHandler))


(defn download-rain
  "Downloads precipitation GeoTIFFs from `arthurhou.pps.eosdis.nasa.gov`"
  [server
   file-path
   file-name
   save-directory]
  (let [file-url  (str server
                            file-path
                            file-name)
             save-file (str
                         save-directory
                         file-name)]
    (if (-> save-file
                 clojure.java.io/file
                 .exists)
      (println (str "Skipping: "
                    file-name
                    " b/c it exists.."))
      (try
        (println (str "Trying to Download: "
                      file-name))
        ;;FileHandler needs to be closed after you finished to use it.
        ;;creating it with 'with-open' does that automatically for you.
        (with-open [filehldr
                    (FileHandler. save-file)]
          (let [curl (curl-easy/init)]
            (do (curl-easy/setopt curl
                                  curl-opts/url
                                  file-url)
                (curl-easy/setopt curl
                                  curl-opts/writefunction
                                  filehldr)
                (curl-easy/setopt curl
                                  curl-opts/password
                                  "kxygk@aliyun.com")
                (curl-easy/setopt curl
                                  curl-opts/username
                                  "kxygk@aliyun.com")
                (curl-easy/setopt curl
                                  curl-opts/use-ssl
                                  1) ;; magic value from curl header
                ;; https://github.com/curl/curl/blob/master/include/curl/curl.h#L875
                (curl-easy/perform curl)
                (println "speed download: " (curl-easy/getinfo-double curl
                                                                      curl-opts/speed-download))
                (curl-easy/cleanup curl))
            (println (str "Finished downloading: "
                          file-name))))
      (catch Exception e (println (str "Can't download: "
                                       file-name)))))))

#_
(download-rain "ftp://arthurhou.pps.eosdis.nasa.gov"
               "/sm/730/gpmdata/2011/08/01/gis/"
               "3B-MO-GIS.MS.MRG.3IMERG.20110801-S000000-E235959.08.V06B.tif"
               "/home/kxygk/Junk/early/")

;; Late File (I think!)
;;
;; Monthly
;;ftp://geokon%2540qq.com@jsimpsonftps.pps.eosdis.nasa.gov/data/imerg/gis/2020/02/3B-MO-L.GIS.IMERG.20200201.V06B.zip
;; Daily
;;ftp://geokon%2540qq.com@jsimpsonftps.pps.eosdis.nasa.gov/data/imerg/gis/2011/01/3B-HHR-L.MS.MRG.3IMERG.20110101-S023000-E025959.0150.V06B.1day.tif

#_
(download-rain "ftp://jsimpsonftps.pps.eosdis.nasa.gov"
               "/data/imerg/gis/2011/01/"
               "3B-HHR-L.MS.MRG.3IMERG.20110101-S023000-E025959.0150.V06B.1day.tif"
               "/home/kxygk/Junk/")

;; Early File (I think!)
;;ftp://geokon%2540qq.com@jsimpsonftps.pps.eosdis.nasa.gov/data/imerg/gis/early/2020/02/3B-HHR-E.MS.MRG.3IMERG.20200201-S023000-E025959.0150.V06B.1day.tif

#_
(download-rain "ftp://jsimpsonftps.pps.eosdis.nasa.gov"
               "/data/imerg/gis/early/2020/02/"
               "3B-HHR-E.MS.MRG.3IMERG.20200201-S023000-E025959.0150.V06B.1day.tif"
               "/home/kxygk/Junk/")

;; Now downloading all the monthly LATE rains will look something like
;; NOTE: 3B-MO-L.GIS.IMERG.20140601.V06B.zip is missing
#_
(let [years               (range
                            2015
                            2021) ;; range is no inclusive of the `end` term
      two-digit-formatter (new
                            java.text.DecimalFormat
                            "00")
      months              (->> (range 1
                                      13)
                               (map #(.format two-digit-formatter
                                              %)))
      ;;
      paths               (flatten (mapv (fn [year]
                                           (mapv (fn [month]
                                                   (str "/data/imerg/gis/"
                                                        year
                                                        "/"
                                                        month
                                                        "/"))
                                                 months))
                                         years))
      files               (flatten
                            (mapv (fn [year]
                                    (mapv (fn
                                            [month]
                                            (str "3B-MO-L.GIS.IMERG."
                                                 year
                                                 month
                                                 "01.V06B.zip"))
                                          months))
                                  years))]
  (dorun (map (fn [path
                   file]
                (download-rain "ftp://jsimpsonftps.pps.eosdis.nasa.gov"
                               path
                               file
                               "/home/kxygk/Junk/latedata/"))
              paths
              files)))



;; Now downloading all the monthly FINAL rains will look something like
#_
(let [years               (range
                            2011
                            2021) ;; range is no inclusive of the `end` term
      two-digit-formatter (new
                            java.text.DecimalFormat
                            "00")
      months              (->> (range 1
                                      13)
                               (map #(.format two-digit-formatter
                                              %)))
      paths               (flatten (mapv (fn
                                           [year]
                                           (mapv (fn
                                                   [month]
                                                   (str "/sm/730/gpmdata/"
                                                        year
                                                        "/"
                                                        month
                                                        "/01/gis/"))
                                                 months))
                                         years))
      files               (flatten (mapv (fn [year]
                                           (mapv (fn
                                                   [month]
                                                   (str "3B-MO-GIS.MS.MRG.3IMERG."
                                                        year
                                                        month
                                                        "01-S000000-E235959."
                                                        month
                                                        ".V06B.tif"))
                                                 months))
                                         years))]
  (dorun (map (fn [path
                   file]
                (download-rain "ftp://arthurhou.pps.eosdis.nasa.gov"
                               path
                               file
                               "/home/geokon/Junk/earlydata/"))
              paths
              files)))

;; Downloading 3 hour chunks of rain

#_

(let [years                (range
                             2011
                             2021) ;; range is no inclusive of the `end` term
      two-digit-formatter  (new java.text.DecimalFormat
                                "00")
      four-digit-formatter (new
                             java.text.DecimalFormat
                             "0000")
      months               (->> (range 9
                                       12)
                                (map #(.format two-digit-formatter
                                               %)))
      days
      (->> (range 1
                  30)
           (map #(.format two-digit-formatter
                          %)))
      hours                (->> (range 2
                                       24
                                       3)
                                (map #(.format two-digit-formatter
                                               %)))
      paths                (flatten (mapv (fn [year]
                                            (mapv (fn [month]
                                                    (mapv (fn [day]
                                                            (mapv (fn [hour]
                                                                    (str "/data/imerg/gis/early/"
                                                                         year
                                                                         "/"
                                                                         month
                                                                         "/"))
                                                                  hours))
                                                          days))
                                                  months))
                                          years))
      files                (flatten (mapv (fn [year]
                                            (mapv (fn [month]
                                                    (mapv (fn [day] (mapv (fn [hour]
                                                                            (str "3B-HHR-E.MS.MRG.3IMERG."
                                                                                 year
                                                                                 month
                                                                                 day
                                                                                 "-S"
                                                                                 hour
                                                                                 "3000-E"
                                                                                 hour
                                                                                 "5959."
                                                                                 (.format four-digit-formatter
                                                                                          (+ 30
                                                                                             (* (Long/parseLong hour)
                                                                                                60)))
                                                                                 ".V06B.3hr.tif"))
                                                                          hours))
                                                          days))
                                                  months))
                                          years))]
  (dorun (map (fn [path
                   file]
                (println "path: "
                         path
                         "\nfile: "
                         file)
                (download-rain "ftp://jsimpsonftps.pps.eosdis.nasa.gov"
                               path
                               file
                               "/home/kxygk/Junk/alldata/"))
              paths
              files)))


;; Download 30min chucks
;; from dirs such as ..
;;jsimpsonftps.pps.eosdis.nasa.gov/data/imerg/gis/2011/04/
;; files of the form ..
;; 3B-HHR-L.MS.MRG.3IMERG.20110401-S210000-E212959.1260.V07B.30min.tif
#_
(for [year  (range
              2011
              2012)
      month (range 2
                   3)
      day   (range 9
                   32)
      hour  (range 0
                   24)]
  ;; For each hour there are 2 30min files
  ;; Min 0-29 and 30-59
  (let [output-dirstr "/home/kxygk/Data/imerg/30min/"
        server "ftp://jsimpsonftps.pps.eosdis.nasa.gov"
        path   (str "/data/imerg/gis/"
                    (format "%04d"
                            year)
                    "/"
                    (format "%02d"
                            month)
                    "/")]
    (download-rain server
                   path
                   (str "3B-HHR-L.MS.MRG.3IMERG."
                        (format "%04d"
                                year)
                        (format "%02d"
                                month)
                        (format "%02d"
                                day)
                        "-S"
                        (format "%02d"
                                hour)
                        "0000"
                        "-E"
                        (format "%02d"
                                hour)
                        "2959."
                        (format "%04d"
                                (* hour
                                   30
                                   2))
                        ".V07B.30min.tif")
                   output-dirstr)
    (download-rain server
                   path
                   (str "3B-HHR-L.MS.MRG.3IMERG."
                        (format "%04d"
                                year)
                        (format "%02d"
                                month)
                        (format "%02d"
                                day)
                        "-S"
                        (format "%02d"
                                hour)
                        "3000"
                        "-E"
                        (format "%02d"
                                hour)
                        "5959."
                        (format "%04d"
                                (+ 30
                                   (* hour
                                      30
                                      2)))
                        ".V07B.30min.tif")
                   output-dirstr)))



;; Download daily maps WIP
;; from dirs such as ..
;; jsimpsonftps.pps.eosdis.nasa.gov/data/imerg/gis/2023/05/
;; files of the form ..
;; 3B-HHR-L.MS.MRG.3IMERG.20230507-S233000-E235959.1410.V07B.1day.tif
;;#_
(->> (let [output-dirstr "/home/kxygk/Data/imerg/daily/V06/late/"
           server        "ftp://jsimpsonftps.pps.eosdis.nasa.gov"]
       (for [year  (range
                     2022
                     2023)
             month (range 1
                          13)
             day   (range 1
                          32)]
         (let [path (str "/data/imerg/gis/V06/"
                         (format "%04d"
                                 year)
                         "/"
                         (format "%02d"
                                 month)
                         "/")]
           (download-rain server
                          path
                          (str "3B-HHR-L.MS.MRG.3IMERG."
                               (format "%04d"
                                       year)
                               (format "%02d"
                                       month)
                               (format "%02d"
                                       day)
                               "-S233000-E235959.1410.V06B.1day.tif")
                          output-dirstr))))
     #_
     (apply pcalls))
;;
;; Test of a file that doesn't exist.
;; Should print an error but not crash (exception is caught)
#_
(download-rain "ftp://jsimpsonftps.pps.eosdis.nasa.gov"
               "/data/imerg/gis/2021/02/" "3B-HHR-L.MS.MRG.3IMERG.20210229-S233000-E235959.1410.V07B.1day.tif"
               "/home/kxygk/Data/imerg/daily/late-more/")
