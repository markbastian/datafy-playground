(ns datafy-playground.datafy-sqldb
  (:require [clojure.java.jdbc :as j]
            [clojure.core.protocols :as p]
            [clojure.datafy :refer [datafy nav]]
            [clojure.pprint :as pp]))

(declare person)

(def person-def
  (j/create-table-ddl
    :person
    [[:id :int "not null"]
     [:name "varchar(32)"]
     [:age :int "not null"]
     [:spouse_id :int]]))

(def children-def
  (j/create-table-ddl
    :children
    [[:parent_id :int]
     [:child_id :int]]))

(defn enhance [db m]
  (with-meta
    m
    {`p/datafy (fn [m]
                 (with-meta
                   m
                   {`p/nav (fn [_ k v]
                             (case k
                               :parent_id (person db v)
                               :child_id (person db v)
                               v))}))}))

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
                                 :spouse_id (person db v)
                                 v))}))})))

(defn with-sample-db [dbfn]
  ;See http://www.h2database.com/html/features.html#database_url for connection string info
  (with-open [c (j/get-connection {:connection-uri "jdbc:h2:mem:test_mem"})]
    (let [db {:connection c}]
      (j/db-do-commands db person-def)
      (j/db-do-commands db children-def)
      (j/insert-multi! db :person [{:id 1 :name "Richard Parker" :age 62 :spouse_id 2}
                                   {:id 2 :name "Mary Parker" :age 63 :spouse_id 1}
                                   {:id 3 :name "Ben Parker" :age 65 :spouse_id 4}
                                   {:id 4 :name "May Parker" :age 64 :spouse_id 3}
                                   {:id 5 :name "Peter Parker" :age 26}
                                   {:id 6 :name "Mary Jane Watson" :age 26}])
      (j/insert-multi! db :children [{:parent_id 1 :child_id 5}
                                     {:parent_id 2 :child_id 5}])
      (do (dbfn db)))))

(comment
  ;Example 1a: Note that prior to datafication nav just returns the value 2
  (with-sample-db
    (fn [db]
      (-> (person db 1)
          (nav :spouse_id 2))))

  ;Example 1b: datafication works
  (with-sample-db
    (fn [db]
      (-> (person db 1)
          ;Datafication now converts from "Thing" to navigable thing. In this case our thing is just
          ; a map so it doesn't look any different. Does it make sense to require explicit datafication here?
          datafy
          ;And now we can nav to the spouse of person 1
          (nav :spouse_id 2))))

  ;Example 2: Getting the children. Is this a valid case of datafy/nav or is this an abuse of the API?
  (with-sample-db
    (fn [db]
      (-> (person db 1)
          ;Note that datafication adds the children and parents fields, but the values are ..., implying
          ;that we haven't evaluated this yet. Is there a better value to use? nil would lead the user to
          ;believe that we know the children/parents and they are nil.
          datafy)))

  (with-sample-db
    (fn [db]
      (-> (person db 1)
          datafy
          ;I can now nav to my children
          (nav :children :...)
          ;And browse them as regular data
          first
          ;But I can't keep navigating because I haven't datafied
          (nav :child_id 5))))

  (with-sample-db
    (fn [db]
      (->
        ;Get a thing
        (person db 1)
        ;Datafy it
        datafy
        ;nav (is this legit?)
        (nav :children :...)
        ;browse as data
        first
        ;datafy
        datafy
        ;nav
        (nav :child_id 5)
        ;And we get our answer (Peter Parker)
        pp/pprint)))
  )