(ns site.dev
  "Local preview: serves dist/ and rebuilds when content/, src/ or public/ change."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [org.httpkit.server :as http]
            [site.build :as build]))

(def ^:private mime-types
  {"html" "text/html; charset=utf-8",
   "css" "text/css; charset=utf-8",
   "js" "text/javascript; charset=utf-8",
   "svg" "image/svg+xml",
   "woff2" "font/woff2",
   "xml" "application/xml; charset=utf-8",
   "txt" "text/plain; charset=utf-8",
   "png" "image/png"})

(defn- file-response
  [status path]
  {:status status,
   :headers {"Content-Type"
               (get mime-types (fs/extension path) "application/octet-stream"),
             "Cache-Control" "no-store"},
   :body (fs/file path)})

(defn- not-found
  [uri]
  ;; Same lookup as Cloudflare's "404-page" mode: nearest 404.html up the
  ;; tree.
  (let [lang-404 (fs/path build/out-dir
                          (or (second (str/split uri #"/")) "")
                          "404.html")
        page (if (fs/regular-file? lang-404)
               lang-404
               (fs/path build/out-dir "404.html"))]
    (file-response 404 page)))

(defn- handler
  [{:keys [uri]}]
  (let [root (fs/canonicalize build/out-dir)
        path (fs/normalize (fs/path root (str/replace-first uri #"^/+" "")))]
    (cond (not (str/starts-with? (str path) (str root))) {:status 400,
                                                          :body "Bad path"}
          (fs/regular-file? path) (file-response 200 path)
          ;; /de -> /de/, like Cloudflare's auto-trailing-slash.
          (and (fs/directory? path) (not (str/ends-with? uri "/")))
            {:status 301, :headers {"Location" (str uri "/")}}
          (fs/regular-file? (fs/path path "index.html"))
            (file-response 200 (fs/path path "index.html"))
          :else (not-found uri))))

(defn- snapshot
  []
  (into {}
        (for [dir [build/content-dir "src" build/public-dir]
              file (fs/glob dir "**")
              :when (fs/regular-file? file)]
          [(str file) (fs/last-modified-time file)])))

(defn- rebuild!
  []
  (try (require 'site.views 'site.build :reload)
       (build/build!)
       (catch Exception e
         (binding [*out* *err*] (println "Build failed:" (ex-message e))))))

(defn -main
  [& [port]]
  (let [port (or (some-> port
                         parse-long)
                 8080)]
    (rebuild!)
    (http/run-server #'handler {:port port})
    (println (str "Serving http://localhost:"
                  port
                  "/ — watching for changes, Ctrl+C to stop"))
    (loop [seen (snapshot)]
      (Thread/sleep 300)
      (let [now (snapshot)]
        (when (not= seen now) (rebuild!))
        (recur now)))))
