# Device code autofill

In the embedded GitHub login page, tap the displayed device code to fill the page. The separate copy icon still copies the code. Filling does not submit the form: the user chooses **Continue** and completes GitHub authorization.

The implementation fills eight single-character cells without the display hyphen, replacing incorrect characters. Older single-input device forms are also supported. Unrecognized or incomplete forms show a retry message instead of reporting success. The fill is explicitly triggered by a tap, so late-loading pages can be retried.

Both the native wrapper and injected script restrict filling to `https://github.com/login/device` (optional trailing slash/query). The native wrapper also rejects URL credentials and nonstandard ports. Login passwords and two-factor pages are outside this scope. No JavaScript bridge is exposed, and the script does not send requests or submit forms.

## Verification

- `GithubDeviceCodeAutofillTest`: device URL boundaries and strict eight-character code validation.
- `scripts/test-device-code-autofill.cjs`: twelve browser checks using intercepted local HTML, covering split/single inputs, event delivery, incorrect existing values, unsupported controls, wrong origins/paths, and absence of submission.
- Debug-only `device-code-autofill` sample: production Compose code chip and production script inside Android WebView, using synthetic local HTML with network loads blocked.

Run the browser tests with Playwright installed:

```sh
node scripts/test-device-code-autofill.cjs
```

`ETOILE_PLAYWRIGHT_PATH` optionally points to a Playwright module and `ETOILE_CHROME_PATH` to a browser executable. These tests do not authenticate a real GitHub account. Changes to GitHub's future page markup may require updating the field selectors.

## Verified build — 2026-09-17

Debug build **14** passed native checks at normal phone size and 320dp / 150% font size. Tapping the production chip replaced all eight incorrect cells; the separate copy button remained usable, and neither action submitted the local form. The sample's DOM reported all eight resulting values through its title, and screenshots were inspected.

426 unit tests and all 12 browser checks passed. Lint reported 0 errors and 105 warnings (including the old code-label resource now unused after the UI change). Release Kotlin compilation and manifest processing passed; debug samples are excluded from Release classes. A signed Release APK and a real-account GitHub authorization were not tested.

- [arm64 debug APK](../../app/build/outputs/apk/debug/Etoile-Android-arm64-v8a-0.1.0-26091701-14.APK)
- [armv7 debug APK](../../app/build/outputs/apk/debug/Etoile-Android-armeabi-v7a-0.1.0-26091701-14.APK)
