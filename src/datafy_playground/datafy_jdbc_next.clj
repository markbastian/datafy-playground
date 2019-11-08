(ns datafy-playground.datafy-jdbc-next
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clojure.datafy :refer :all]))

(def people
  [{:id 1 :name "Richard Parker" :spouseid 2}
   {:id 2 :name "Mary Parker" :spouseid 1}])

(comment
  (with-open [c (jdbc/get-connection {:dbtype "h2" :dbname "mem:test_mem1"})]
    (jdbc/execute! c ["DROP TABLE PERSON IF EXISTS"])
    (jdbc/execute! c ["CREATE TABLE person (id int not null primary key, name varchar(32), spouseid int)"])
    (let [k (keys (first people))]
      (sql/insert-multi! c :person k (map #(map % k) people)))
    (let [[res] (jdbc/execute!
                  c
                  ["SELECT * FROM PERSON WHERE name='Richard Parker'"]
                  {:schema {:person/spouseid :person/id}})]
      (-> res
          datafy
          (nav :person/spouseid 2))))
  )
