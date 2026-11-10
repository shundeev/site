(ns site.build
  "Reads content/*.edn, renders HTML with site.views and writes the static site to dist/."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [site.views :as views])
  (:import [java.security MessageDigest]
           [java.time Year]))

(def out-dir "dist")
(def public-dir "public")
(def content-dir "content")

(defn- read-edn
  [path]
  (try (edn/read-string (slurp (str path)))
       (catch Exception e
         (throw (ex-info (str "Cannot read " path ": " (ex-message e))
                         {:path (str path)}
                         e)))))

(defn- short-hash
  [path]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (fs/read-all-bytes path))]
    (subs (apply str (map #(format "%02x" (bit-and % 0xff)) digest)) 0 10)))

;; ---------------------------------------------------------------------------
;; Content

(def ^:private required-keys
  #{:lang-name :locale :title :description :name :job-title :lede :intro :ui
    :sections :jobs :skills :education :spoken :contact :footer :not-found})

(defn- check-translation!
  "Fails the build when a language file misses something the templates need,
   so a typo in content/de.edn doesn't ship as an empty heading."
  [site lang t]
  (let [missing (concat (set/difference required-keys (set (keys t)))
                        (for [{:keys [id]} (:jobs site)
                              :when (nil? (get-in t [:jobs id]))]
                          [:jobs id])
                        (for [{:keys [id]} (:skills site)
                              :when (nil? (get-in t [:skills id]))]
                          [:skills id]))]
    (when (seq missing)
      (throw (ex-info (str "content/" (name lang)
                           ".edn is missing: " (pr-str (vec missing)))
                      {:lang lang, :missing missing})))))

(defn load-content
  []
  (let [site (read-edn (fs/path content-dir "site.edn"))
        ts (into {}
                 (for [lang (:langs site)]
                   [lang
                    (read-edn (fs/path content-dir
                                       (str (name lang) ".edn")))]))]
    (doseq [[lang t] ts] (check-translation! site lang t))
    {:site site, :ts ts}))

;; ---------------------------------------------------------------------------
;; Build

(defn- write! [path s] (fs/create-dirs (fs/parent path)) (spit (str path) s))

(defn- lang-dir
  [site lang]
  (if (= lang (first (:langs site)))
    (fs/path out-dir)
    (fs/path out-dir (name lang))))

(defn build!
  []
  (let [{:keys [site], :as content} (load-content)
        ctx (assoc content
              :css (str "/assets/style.css?v="
                        (short-hash (fs/path public-dir "assets" "style.css")))
              :year (.getValue (Year/now)))]
    (fs/delete-tree out-dir)
    (fs/copy-tree public-dir out-dir)
    (doseq [lang (:langs site)
            :let [ctx (assoc ctx :lang lang)
                  dir (lang-dir site lang)]]
      (write! (fs/path dir "index.html") (views/home-page ctx))
      (write! (fs/path dir "404.html") (views/not-found-page ctx)))
    (write! (fs/path out-dir "sitemap.xml") (views/sitemap ctx))
    (println "Built" (str out-dir "/") "for" (pr-str (:langs site)))))

(defn -main [& _args] (build!))
