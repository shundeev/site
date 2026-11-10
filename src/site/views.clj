(ns site.views
  "Hiccup templates. Every fn takes a ctx map:
   {:site site.edn, :ts {lang translations}, :lang current lang, :css href, :year int}"
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [hiccup2.core :as h]))

;; ---------------------------------------------------------------------------
;; URLs

(defn- default-lang [site] (first (:langs site)))

(defn lang-path
  "Site-relative path of a language's home page: / for the default, /de/ etc."
  [site lang]
  (if (= lang (default-lang site)) "/" (str "/" (name lang) "/")))

(defn- abs-url [site lang] (str (:url site) (lang-path site lang)))

(defn- mailto [site] (str "mailto:" (:email site)))

(def ^:private fonts
  {:latin "/assets/fonts/commissioner-latin.woff2",
   :cyrillic "/assets/fonts/commissioner-cyrillic.woff2"})

(defn- preload-fonts
  "Fonts the first screen needs. The name and the domain are always Latin
   on Latin-script pages; the Russian page also needs Cyrillic."
  [lang]
  (if (= lang :ru) [(:cyrillic fonts) (:latin fonts)] [(:latin fonts)]))

;; ---------------------------------------------------------------------------
;; Head

(defn- json-ld
  [{:keys [site ts lang]}]
  (let [t (ts lang)]
    (-> {"@context" "https://schema.org",
         "@type" "Person",
         "name" (str/join " " (:name t)),
         "jobTitle" (:job-title t),
         "url" (abs-url site lang),
         "email" (mailto site),
         "sameAs" (mapv :href (:links site)),
         "address" {"@type" "PostalAddress",
                    "addressLocality" "Berlin",
                    "addressCountry" "DE"},
         "knowsLanguage" ["ru" "en" "de"]}
        json/generate-string
        ;; Never let data close the <script> element.
        (str/replace "</" "<\\/"))))

(defn- head
  [{:keys [site ts lang css], :as ctx} {:keys [title description index?]}]
  (let [t (ts lang)]
    [:head [:meta {:charset "utf-8"}]
     [:meta {:name "viewport", :content "width=device-width, initial-scale=1"}]
     [:title title] [:meta {:name "description", :content description}]
     (when-not index? [:meta {:name "robots", :content "noindex"}])
     [:meta {:name "color-scheme", :content "light dark"}]
     [:meta
      {:name "theme-color",
       :content "#eef1ec",
       :media "(prefers-color-scheme: light)"}]
     [:meta
      {:name "theme-color",
       :content "#0d1a15",
       :media "(prefers-color-scheme: dark)"}]
     (for [href (preload-fonts lang)]
       [:link
        {:rel "preload",
         :href href,
         :as "font",
         :type "font/woff2",
         :crossorigin "anonymous"}]) [:link {:rel "stylesheet", :href css}]
     [:link {:rel "icon", :href "/favicon.svg", :type "image/svg+xml"}]
     (when index?
       (list
         [:link {:rel "canonical", :href (abs-url site lang)}]
         (for [l (:langs site)]
           [:link
            {:rel "alternate", :hreflang (name l), :href (abs-url site l)}])
         [:link
          {:rel "alternate",
           :hreflang "x-default",
           :href (abs-url site (default-lang site))}]
         [:meta {:property "og:type", :content "profile"}]
         [:meta {:property "og:title", :content title}]
         [:meta {:property "og:description", :content description}]
         [:meta {:property "og:url", :content (abs-url site lang)}]
         [:meta {:property "og:locale", :content (:locale t)}]
         (for [l (:langs site)
               :when (not= l lang)]
           [:meta {:property "og:locale:alternate", :content (:locale (ts l))}])
         [:meta {:property "profile:first_name", :content (first (:name t))}]
         [:meta {:property "profile:last_name", :content (last (:name t))}]
         [:meta {:name "twitter:card", :content "summary"}]
         [:script {:type "application/ld+json"} (h/raw (json-ld ctx))]))]))

;; ---------------------------------------------------------------------------
;; Page chrome

(defn- top-bar
  [{:keys [site ts lang]}]
  (let [t (ts lang)]
    [:header.top [:a.domain {:href (lang-path site lang)} (:domain site)]
     [:nav.langs {:aria-label (get-in t [:ui :languages])}
      [:ul
       (for [l (:langs site)]
         [:li
          [:a
           {:href (lang-path site l),
            :hreflang (name l),
            :lang (name l),
            :aria-label (:lang-name (ts l)),
            :aria-current (when (= l lang) "page")}
           (str/upper-case (name l))]])]]]))

(defn- footer
  [{:keys [ts lang year]}]
  (let [t (ts lang)]
    [:footer.foot [:p (:footer t)]
     [:p "© " year " " (str/join " " (:name t))]]))

(defn- layout
  [{:keys [lang ts], :as ctx} head-opts & body]
  (str "<!doctype html>\n"
       (h/html {:mode :html}
               [:html {:lang (name lang)} (head ctx head-opts)
                [:body [:a.skip {:href "#main"} (get-in (ts lang) [:ui :skip])]
                 [:div.page (top-bar ctx) (into [:main#main] body)
                  (footer ctx)]]])))

;; ---------------------------------------------------------------------------
;; Home page

(defn- section
  [id title & body]
  [:section.section.grid {:id id, :aria-labelledby (str id "-title")}
   [:h2.section-title {:id (str id "-title")} title]
   (into [:div.section-body] body)])

(defn- pair [k v] [:div [:dt k] [:dd v]])

(defn- hero
  [{:keys [site ts lang]}]
  (let [t (ts lang)
        [first-name last-name] (:name t)]
    [:section.hero.grid
     ;; The space keeps the accessible name "Egor Shundeev" when spans are
     ;; blocks.
     [:h1.name [:span first-name] " " [:span last-name]]
     [:div.hero-text [:p.lede (:lede t)] [:p.intro (:intro t)]
      [:ul.links [:li [:a {:href (mailto site)} (get-in t [:ui :email])]]
       (for [{:keys [href label]} (:links site)]
         [:li [:a {:href href, :rel "me"} label]])]]]))

(defn- period
  [{:keys [from to]} present]
  (if to [:span [:time from] "–" [:time to]] [:span [:time from] "–" present]))

(defn- work
  [{:keys [site ts lang]}]
  (let [t (ts lang)]
    (section "work"
             (get-in t [:sections :work])
             [:ol.jobs
              (for [job (:jobs site)
                    :let [{:keys [org role place points]}
                            (merge job (get-in t [:jobs (:id job)]))]]
                [:li.job [:p.job-years (period job (get-in t [:ui :present]))]
                 [:div
                  [:h3.job-org
                   (if-let [href (:href job)]
                     [:a {:href href} org]
                     org)] [:p.job-role role ", " place]
                  [:ul.job-points (for [p points] [:li p])]]])])))

(defn- skills
  [{:keys [site ts lang]}]
  (let [t (ts lang)]
    (section "skills"
             (get-in t [:sections :skills])
             [:dl.pairs
              (for [{:keys [id items]} (:skills site)]
                (pair (get-in t [:skills id]) (str/join ", " items)))])))

(defn- education
  [{:keys [site ts lang]}]
  (let [{:keys [degree school]} (:education (ts lang))]
    (section "education"
             (get-in (ts lang) [:sections :education])
             [:dl.pairs
              (pair [:time (get-in site [:education :year])]
                    (list degree [:br] [:span.muted school]))])))

(defn- spoken
  [{:keys [ts lang]}]
  (let [t (ts lang)]
    (section "languages"
             (get-in t [:sections :spoken])
             [:dl.pairs
              (for [[language level] (:spoken t)] (pair language level))])))

(defn- contact
  [{:keys [site ts lang]}]
  (let [t (ts lang)]
    (section "contact"
             (get-in t [:sections :contact])
             [:p.contact-text (get-in t [:contact :text])]
             [:p.contact-email [:a {:href (mailto site)} (:email site)]]
             [:ul.links
              (for [{:keys [href label]} (:links site)]
                [:li [:a {:href href, :rel "me"} label]])]
             [:p.muted (get-in t [:contact :place])])))

(defn home-page
  [{:keys [ts lang], :as ctx}]
  (let [t (ts lang)]
    (layout ctx
            {:title (:title t), :description (:description t), :index? true}
            (hero ctx)
            (work ctx)
            (skills ctx)
            (education ctx)
            (spoken ctx)
            (contact ctx))))

;; ---------------------------------------------------------------------------
;; 404

(defn not-found-page
  [{:keys [site ts lang], :as ctx}]
  (let [{:keys [title text home]} (:not-found (ts lang))]
    (layout ctx
            {:title title, :description text, :index? false}
            [:section.hero.grid [:h1.name.name-small [:span "404"]]
             [:div.hero-text [:p.lede title] [:p.intro text]
              [:p [:a {:href (lang-path site lang)} home]]]])))

;; ---------------------------------------------------------------------------
;; Sitemap

(defn sitemap
  [{:keys [site]}]
  (let [alternates
          (for [l (:langs site)]
            [:xhtml:link
             {:rel "alternate", :hreflang (name l), :href (abs-url site l)}])]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         (h/html {:mode :xml}
                 [:urlset
                  {:xmlns "http://www.sitemaps.org/schemas/sitemap/0.9",
                   :xmlns:xhtml "http://www.w3.org/1999/xhtml"}
                  (for [l (:langs site)]
                    [:url [:loc (abs-url site l)] alternates])]))))
