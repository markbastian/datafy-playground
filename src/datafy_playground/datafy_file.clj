(ns datafy-playground.datafy-file
  "A very basic datafy/nav example
  Resources for understanding this:
  * Stuart Halloway's talk https://www.youtube.com/watch?v=c52QhiXsmyI&feature=youtu.be&t=1
  * Sean Corfield's blog post on datafy http://corfield.org/blog/2018/12/03/datafy-nav/
  "
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :refer [datafy nav]]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.pprint :as pp])
  (:import (java.io File)))

(declare file->data)

(defn file-contents->data [input fmt]
  (case fmt
    :raw (slurp input)
    :csv (into [] (csv/read-csv input))))

;Tied to the Question 1 below, is it acceptable to datafy these results?
(defn file-nav [{:keys [parent absolute-path files] :as file-data} k v]
  (case k
    ;Navigate up to a parent
    :parent (datafy (file->data (io/file parent)))          ;Should I datafy on the fly?
    ;Navigate down to children (only works if a directory)
    :files (datafy (file->data (get files v)))              ;Should I datafy on the fly?
    :data (with-open [rdr (io/reader (io/file absolute-path))]
            (file-contents->data rdr v))
    v))

(defn file->data [^File file]
  (with-meta
    (cond->
      {:name   (.getName file)
       :absolute-path (.getAbsolutePath file)
       :parent (.. file getAbsoluteFile getParent)}
      (.isFile file) (assoc :length (.length file))
      (.isDirectory file) (assoc :files (into [] (.listFiles file))))
    {:file  file
     `p/nav file-nav}))

(extend-protocol p/Datafiable
  java.io.File
  (datafy [file]
    (file->data file)))

;Some examples
(comment
  ;Starting with the current directory, navigate up, take the first file in the files
  ; list, then get its name
  ;Question - is it appropriate to auto-datafy in these cases within the nav?
  (-> (io/file ".")
      datafy
      (nav :parent nil)
      ;Question 2: Is this "normal" nav usage or should I do (get-in [:files 0])
      (nav :files 0)
      :name)

  (-> (io/file ".")
      file->data
      (nav :parent nil)
      ;Question 2: Is this "normal" nav usage or should I do (get-in [:files 0])
      (nav :files 1)
      :name)

  (-> (io/file ".")
      file->data
      (nav :parent nil)
      ;Question 2: Is this "normal" nav usage or should I do (get-in [:files 0])
      (nav :files 1)
      (nav :data :raw))

  (-> (io/file "project.clj")
      datafy
      :absolute-path)

  ;Here is a standard way to open a csv file.
  (with-open [r (io/reader (io/file "example.csv"))]
    (pp/pprint (csv/read-csv r)))
  ;Question 3: Is this a "normal" way to navigate to the file contents.
  ; Note that there is no "data" key in the datafied data, but this could be added as a preview.
  (-> (io/file "example.csv")
      datafy
      (nav :data :csv))
  ;Navigating as text vs. csv, for example.
  (-> (io/file "example.csv")
      datafy
      (nav :data :raw))
  )
