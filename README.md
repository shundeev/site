# shundeev.dev

Personal site of Egor Shundeev. Right now it is a single static page in `public/`,
served by Cloudflare as static assets — no build step.

The earlier multilingual version (Babashka + Hiccup, en/de/ru) is parked on the
`full-site` branch. Sections come back from there one tracked issue at a time.

## Layout

```
public/          served as is: index.html, style.css, favicon, _headers, robots.txt
wrangler.jsonc   Cloudflare config (static assets from public/, custom domain)
```

## Deploy

```sh
npm install           # wrangler, pinned in package.json
npx wrangler login    # once, opens the browser
npx wrangler deploy
```

`wrangler.jsonc` attaches `shundeev.dev` as a custom domain, so Cloudflare creates
the DNS record and certificate on the first deploy. The zone must be in the same
Cloudflare account and must not already have an A/AAAA/CNAME record for the apex.
