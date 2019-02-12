(ns datafy-playground.datafy-sqldb
  (:require [clojure.java.jdbc :as j]
            [clojure.core.protocols :as p]
            [clojure.datafy :refer [datafy nav]]))

(declare person)

(def person-def
  (j/create-table-ddl
    :person
    [[:id :int "not null"]
     [:name "varchar(32)"]
     [:age :int "not null"]]))

(def children-def
  (j/create-table-ddl
    :children
    [[:parent_id :int]
     [:child_id :int]]))

(defn enhance [db m]
  (letfn [(parent-child-nav [k v]
            (prn [k v])
            (case k
              :parent_id (person db v)
              :child_id (person db v)
              v))]
    (with-meta m {`p/nav (fn [_ k v] (parent-child-nav k v))})))

(defn children-of [db id]
  (->> (j/query db [(format "SELECT * FROM children WHERE parent_id = %s" id)])
       (map (partial enhance db))))

(defn parents-of [db id]
  (->> (j/query db [(format "SELECT * FROM children WHERE child_id = %s" id)])
       (map (partial enhance db))))

(defn person [db id]
  (let [[m] (j/query db [(format "SELECT * FROM person WHERE id = %s" id)])]
    (with-meta
      m
      {`p/datafy (fn [m]
                   (with-meta
                     (into m {:children :... :parents :...})
                     {`p/nav (fn [{:keys [id]} k v]
                               (case k
                                 :parents (parents-of db id)
                                 :children (children-of db id)
                                 v))}))})))

(with-open [c (j/get-connection {:connection-uri "jdbc:h2:mem:test_mem"})]
  (let [db {:connection c}]
    (j/db-do-commands db person-def)
    (j/db-do-commands db children-def)
    (j/insert-multi! db :person [{:id 1 :name "Richard Parker" :age 62}
                                 {:id 2 :name "Mary Parker" :age 63}
                                 {:id 3 :name "Ben Parker" :age 65}
                                 {:id 4 :name "May Parker" :age 64}
                                 {:id 5 :name "Peter Parker" :age 26}
                                 {:id 6 :name "Mary Jane Watson" :age 26}])
    (j/insert-multi! db :children [{:parent_id 1 :child_id 5}
                                   {:parent_id 2 :child_id 5}])
    (-> (person db 5)
         datafy
         (nav :parents :...)
         second
         (nav :parent_id 2))))
