// What a visitor sees on shundeev.dev, at desktop and phone width (dev-kit#27).
const { test, expect } = require('@playwright/test');

const screens = {
  desktop: { width: 1280, height: 800 },
  phone: { width: 390, height: 844 },
};

for (const [name, viewport] of Object.entries(screens)) {
  test(`the page at ${name} width`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport);
    const response = await page.goto('/');
    expect(response.status()).toBe(200);

    await expect(page).toHaveTitle('Egor Shundeev');
    await expect(page.getByText('Hi, my name is Egor.')).toBeVisible();

    // The stylesheet applied: the text sits in the middle of the screen, down and across.
    // Without it the line would stand at the top, as wide as the page.
    const box = await page.getByText('Hi, my name is Egor.').boundingBox();
    expect(Math.abs(box.y + box.height / 2 - viewport.height / 2)).toBeLessThan(2);
    expect(Math.abs(box.x + box.width / 2 - viewport.width / 2)).toBeLessThan(2);
    expect(box.width).toBeLessThan(viewport.width - 32);

    // Nothing wider than the screen, so no sideways scrolling.
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);

    await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true });
  });
}
