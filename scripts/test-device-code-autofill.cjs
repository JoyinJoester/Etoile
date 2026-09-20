// Requires Playwright. Set ETOILE_PLAYWRIGHT_PATH / ETOILE_CHROME_PATH for a local runtime.
const { chromium } = require(process.env.ETOILE_PLAYWRIGHT_PATH || 'playwright');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const assert = require('node:assert/strict');
const script = readFileSync(join(__dirname, '../app/src/main/assets/github-device-code.js'), 'utf8')
  .replace('__ETOILE_DEVICE_CODE__', JSON.stringify('AB12CD34'));

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.ETOILE_CHROME_PATH || undefined });
  try {
    const page = await browser.newPage();
    await page.route('**/*', route => route.fulfill({ contentType: 'text/html', body: '<html><body></body></html>' }));
    const cases = [
      { name: 'eight cells, wrong existing values and a hidden aggregate', html: '<input type="hidden" name="user_code">' + '<input maxlength="1" value="X">'.repeat(8), values: [...'AB12CD34'] },
      { name: 'single legacy field', html: '<input name="user_code">', values: ['AB12-CD34'] },
      { name: 'eight-character legacy field', html: '<input name="otp" maxlength="8">', values: ['AB12CD34'] },
      { name: 'one-time-code legacy field', html: '<input autocomplete="one-time-code">', values: ['AB12-CD34'] },
      { name: 'incomplete grid is untouched', html: '<input maxlength="1" value="X">'.repeat(6), values: Array(6).fill('X'), ok: false },
      { name: 'password is untouched', html: '<input name="otp" type="password" value="secret">', values: ['secret'], ok: false },
      { name: 'readonly field is untouched', html: '<input name="user_code" readonly value="old">', values: ['old'], ok: false },
      { name: 'login page is untouched', url: 'https://github.com/login', html: '<input name="otp">', values: [''], ok: false },
      { name: 'two-factor page is untouched', url: 'https://github.com/sessions/two-factor', html: '<input name="otp">', values: [''], ok: false },
      { name: 'lookalike origin is untouched', url: 'https://github.com.evil.test/login/device', html: '<input name="otp">', values: [''], ok: false },
      { name: 'insecure origin is untouched', url: 'http://github.com/login/device', html: '<input name="otp">', values: [''], ok: false },
      { name: 'unexpected port is untouched', url: 'https://github.com:444/login/device', html: '<input name="otp">', values: [''], ok: false },
    ];
    for (const test of cases) {
      await page.goto(test.url || 'https://github.com/login/device');
      await page.setContent('<form>' + test.html + '<button type="submit">Continue</button></form>');
      await page.evaluate(() => {
        window.inputEvents = 0;
        window.submissions = 0;
        document.addEventListener('input', () => window.inputEvents++);
        document.addEventListener('submit', e => { e.preventDefault(); window.submissions++; });
        HTMLFormElement.prototype.submit = () => window.submissions++;
      });
      const ok = await page.evaluate(script);
      assert.equal(ok, test.ok !== false, test.name);
      assert.deepEqual(await page.locator('input:not([type="hidden"])').evaluateAll(xs => xs.map(x => x.value)), test.values, test.name);
      assert.equal(await page.evaluate(() => window.submissions), 0, 'Autofill must not submit');
      assert.equal(await page.evaluate(() => window.inputEvents), ok ? test.values.length : 0, test.name);
      console.log('PASS ' + test.name);
    }
    console.log(`${cases.length} device-code browser checks passed; no live GitHub requests.`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
