// The site as a visitor sees it (dev-kit#27): `npx playwright test`, also run by CI through
// `:user-test` in kit.edn. It drives the Google Chrome installed on the machine (GitHub's
// ubuntu-latest has one), so no browser is downloaded. Screenshots land in test-results/.
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: 'tests',
  outputDir: 'test-results',
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    channel: 'chrome',
    colorScheme: 'light',
    // A failing check stops before its own screenshot; this one shows the page it failed on.
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'python3 -m http.server 4173 --bind 127.0.0.1 --directory public',
    url: 'http://127.0.0.1:4173/',
    reuseExistingServer: !process.env.CI,
  },
});
