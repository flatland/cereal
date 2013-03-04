(ns flatland.cereal.core
  (:require [gloss.core.protocols :refer [Reader Writer]]
            [gloss.core.formats :refer [to-buf-seq]]
            [flatland.useful.fn :refer [fix]]
            [flatland.useful.io :refer [read-seq]]
            [clojure.java.io :refer [input-stream]]
            flatland.io.core
            [gloss.core :as gloss])
  (:import (java.nio ByteBuffer)
           (java.io ObjectInputStream ObjectOutputStream ByteArrayOutputStream)))

(defn java-codec [& {:keys [validator repeated]}]
  (-> (reify
        Reader
        (read-bytes [this buf-seq]
          [true, (.readObject (ObjectInputStream. (input-stream buf-seq))), nil])
        Writer
        (sizeof [this] nil)
        (write-bytes [this _ val]
          (when (and validator (not (validator val)))
            (throw (IllegalStateException. "Invalid value in java-codec")))
          (to-buf-seq
           (let [byte-stream (ByteArrayOutputStream.)]
             (.writeObject (ObjectOutputStream. byte-stream) val)
             (.toByteArray byte-stream)))))
      (fix repeated
           #(gloss/repeated (gloss/finite-frame :int32 %) :prefix :none))))

(defn edn-codec [& {:keys [validator repeated]}]
  (reify
    Reader
    (read-bytes [this buf-seq]
      (let [forms (read-seq buf-seq)]
        (cond repeated [true forms nil]
              (next forms) (throw (Exception. "Bytes left over after decoding frame."))
              :else [true (first forms) nil])))
    Writer
    (sizeof [this] nil)
    (write-bytes [this _ val]
      (when (and validator (not (validator val)))
        (throw (IllegalStateException. "Invalid value in edn-codec")))
      (map #(ByteBuffer/wrap (.getBytes (pr-str %)))
           (fix val (not repeated) list)))))
