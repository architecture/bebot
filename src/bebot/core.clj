(ns bebot.core
  (:use bebot.simulate
	  bebot.utils
	  bebot.moving)
  (:import [java.awt Robot Rectangle]
	   [java.awt.image BufferedImage RenderedImage]
	   [java.io File]
	   [javax.imageio ImageIO]
	   [java.awt.event InputEvent])
	(:gen-class))

(def r (new Robot))
(def cali (ref {:x 192 :y 236}))

(def gameboard (make-array java.lang.Object 8 8))

(defn calibrate []
  "Calibrate the window"
  (let [w (new Rectangle 0 0 950 950)
	img (. r createScreenCapture w)
	found (atom false)]
    (doseq [y (range 0 600) x (range 0 600) :while (false? @found)]
      ; Find all the 4 corners
      (if (every?
	   #(contains? colors %)
	   [(. img getRGB x y)
	    (. img getRGB (+ x (* space 7)) y)
	    (. img getRGB x (+ y (* space 7)))
	    (. img getRGB (+ x (* space 7)) (+ y (* space 7)))])
	(do
	  (reset! found true)
	  (dosync (ref-set cali {:x x :y y})))))
    (if found
      (println (str "Calibrated successfully.\n" @cali))
      (println "Couldn't calibrate. Perhaps you need to change the color values in the code."))))

(defn scan [board]
  "Scans the board and puts in the colors on the board 
  or color name if it couldn't recognize it."
  (let [w (new Rectangle 0 0 950 950)
	img (. r createScreenCapture w)]
    (doseq [y (range 0 8) x (range 0 8)]
      (let [color (. img getRGB (+ (:x @cali) (* space x))
		     (+ (:y @cali) (* space y)))]
	(aset board y x (if (nil? (colors color))
			    color
			  (colors color)))))))

(defn game-tree [board]
  "Generate a game tree one level deep."
  (let [tree (ref (list))]
    (doseq [move (all-moves board)]
      (dosync (alter tree conj (list move (all-moves (simulate-board-move board move))))))
    @tree))

(defn best-move [tree]
  "Pick out the best move out of a game tree one level deep."
  (loop [ntree tree moves (list)]
    (if (empty? ntree)
      (last (sort-by #(:score %) moves))
      (let [second-score (:score (last (sort-by #(:score %) (first (rest (first ntree))))))]
      (recur (rest ntree)
	     (conj moves
	      (assoc (ffirst ntree)
		:score (+ (:score (ffirst ntree))
			  (if (nil? second-score) 0 second-score)))))))))

(defn make-move [move]
  "Makes a move given a 'move map'."
  (if (not (nil? move))
    (do
      (. r mouseMove (+ (:x @cali) (* (-> move :from :x) space))
	             (+ (:y @cali) (* (-> move :from :y) space)))
      (. r mousePress InputEvent/BUTTON1_MASK)
      (. r mouseRelease InputEvent/BUTTON1_MASK)
      (. r mouseMove (+ (:x @cali) (* (-> move :to :x) space) 3)
                     (+ (:y @cali) (* (-> move :to :y) space) 3))
      (. r mousePress InputEvent/BUTTON1_MASK)
      (. r mouseRelease InputEvent/BUTTON1_MASK))))

(defn run [sleep stupid]
  "Run the bot, parameters are sleep and stupid. 
  Sleep makes it count down from 5 before starting.
  Stupid makes it not look ahead a move."
  (if sleep
    (dotimes [n 5]
      (println (str (- 5 n) "..")) ;The countdown doesn't work with lein repl but works fine with swank
      (Thread/sleep 1000)))
  (let [start (System/currentTimeMillis)]
    (loop []
      (if (> (- (System/currentTimeMillis) start) 68000)
	"Done"
	(do
	  (scan gameboard)
	  (if stupid
	    (do
	      (make-move (last (sort-by #(:score %) (all-moves gameboard))))
	      (Thread/sleep 100))
	     (make-move (best-move (game-tree gameboard))))
	  (recur))))))

(defn -main [& args]
  "Does a calibration and then runs, for those who
   just wants to try out real simple via Java."
  (calibrate)
  (run false true))