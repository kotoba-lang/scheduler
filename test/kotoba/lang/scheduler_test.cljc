(ns kotoba.lang.scheduler-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.scheduler :as sch]
            [kotoba.lang.time :as t]))

(defn- clock-fn [state-atom] (fn [] @state-atom))

(deftest scheduler-shape
  (let [s (sch/scheduler (clock-fn (atom 0)))]
    (is (map? (:jobs s)))
    (is (empty? (:jobs s)))))

(deftest at-schedules-a-one-shot
  (let [clk (atom 0) s (sch/scheduler (clock-fn clk))]
    (let [s' (sch/at s :a (t/instant 1000) (fn [now] [:a (:time/instant now)]))]
      (is (contains? (:jobs s') :a))
      (is (false? (sch/due? s' 999)))
      (is (true?  (sch/due? s' 1000))))))

(deftest tick-fires-due-and-removes-one-shot
  (let [clk (atom 0) s (sch/scheduler (clock-fn clk))
        fired-atom (atom [])]
    (let [s (sch/at s :a (t/instant 1000) (fn [now] (swap! fired-atom conj (:time/instant now))))]
      (let [[s' fired] (sch/tick s 1000)]
        (is (= [1000] @fired-atom))
        (is (= 1 (count fired)))
        (is (not (contains? (:jobs s') :a)))))))

(deftest tick-does-nothing-before-due
  (let [s (sch/scheduler (clock-fn (atom 0)))
        s (sch/at s :a (t/instant 1000) (fn [now] :x))]
    (let [[s' fired] (sch/tick s 500)]
      (is (empty? fired))
      (is (contains? (:jobs s') :a)))))

(deftest every-re-enqueues-at-at-plus-interval
  (let [s (sch/scheduler (clock-fn (atom 500)))
        s (sch/every s :b (t/seconds 1) (fn [now] :tick) :start (t/instant 1000))]
    (let [[s' fired] (sch/tick s 1000)]
      (is (= [:tick] fired))
      (is (= 2000 (:time/instant (:at (get (:jobs s') :b)))))
      (is (false? (sch/due? s' 1999)))
      (is (true?  (sch/due? s' 2000))))))

(deftest cancel-removes-job
  (let [s (sch/scheduler (clock-fn (atom 0)))
        s2 (-> s (sch/at :a (t/instant 1000) (fn [_] :x)) (sch/cancel :a))]
    (is (not (contains? (:jobs s2) :a)))))

(deftest multiple-jobs-fire-in-order
  (let [s (sch/scheduler (clock-fn (atom 0)))
        s (-> s
              (sch/at :late  (t/instant 2000) (fn [now] [:late  (:time/instant now)]))
              (sch/at :early (t/instant 500)  (fn [now] [:early (:time/instant now)])))]
    (let [[_s' fired] (sch/tick s 2000)]
      (is (= [[:early 2000] [:late 2000]] fired)))))
