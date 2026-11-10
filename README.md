# shundeev.dev

Personal site of Egor Shundeev. A static one-pager in English, German and Russian,
generated with [Babashka](https://babashka.org) and Hiccup and served by Cloudflare.

## Layout

```
content/site.edn     facts shared by all languages: links, jobs, years, skills
content/{en,de,ru}.edn  translated text, merged with site.edn by :id
src/site/views.clj   Hiccup templates
src/site/build.clj   reads content, checks translations, writes dist/
src/site/dev.clj     local server with rebuild on change
public/              copied to dist/ as is: CSS, fonts, favicon, _headers, robots.txt
wrangler.jsonc       Cloudflare config (static assets, custom domain)
```

English is served at `/`, the others at `/de/` and `/ru/`. The build fails if a
language file is missing a key the templates need.

## Commands

```sh
bb dev          # http://localhost:8080, rebuilds on every change in content/, src/, public/
bb build        # writes dist/
bb deploy       # bb build, then npx wrangler deploy
```

First deploy:

```sh
npm install           # wrangler, pinned in package.json
npx wrangler login    # once, opens the browser
bb deploy
```

`wrangler.jsonc` attaches `shundeev.dev` as a custom domain, so Cloudflare creates
the DNS record and certificate on the first deploy. The zone must be in the same
Cloudflare account and must not already have an A/AAAA/CNAME record for the apex.

## Editing

- Text: edit `content/<lang>.edn`. Keep the same keys in every language.
- New job: add it to `:jobs` in `content/site.edn` with an `:id`, then add the
  same `:id` under `:jobs` in every language file.
- Colors and type: tokens at the top of `public/assets/style.css`.
  The font is Commissioner (SIL OFL), self-hosted in `public/assets/fonts/`.
