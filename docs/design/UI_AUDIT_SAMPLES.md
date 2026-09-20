# UI audit samples

`DesignAuditActivity` lives only in `app/src/debug`. It renders deterministic
fixtures through the production screen composables without creating a session
or invoking repositories. Actions show a persistent result at the bottom of the
screen and a toast rather than mutating data. Do not rely on Logcat: this project's
R8 rules remove Android log calls.
These fixtures validate layout and navigation within the screen, not backend behavior.

Build with `:app:assembleDebug`, install the matching ABI APK, then launch:

```powershell
adb shell am force-stop takagi.joyin.github.etoile
adb shell am start -n takagi.joyin.github.etoile/takagi.ru.monica.debug.DesignAuditActivity --es sample release --ez dark true
```

Use `--es sample labels` for 30 long collection names, or `--es sample inbox`
for long notification titles. Use `--ez dark false` for light mode. Stop the
activity before changing fixtures so the new intent is applied on creation.
Use `--es sample links` to compare native Compose, absolute Markdown, and relative
Markdown links. Each click updates the persistent result, which is also visible
in the UIAutomator tree.
The inbox fixture omits the normal app navigation chrome; it is for row review.

Use `--es sample accounts` for four long account identities and removal confirmation.
Use `--es sample review` for a 13-line wide diff and a review form in its failure
state. The review fixture uses the production comment card and composer; submit
buttons report APPROVE, REQUEST_CHANGES, or COMMENT without posting a review.
The bottom feedback overlay can cover the final action at large font sizes;
restart the fixture between action checks when needed.

The Release fixture contains 25 sections and three architecture-specific files.
Check the Assets shortcut, full filenames, and download target toast. The label
fixture should scroll to the creation field and preserve typed text on rotation.
Label save/delete callbacks only show a toast; validation and persistence require
the ViewModel tests or the real application.

Verified on API 35: Release asset shortcut and full filenames; label creation
field reachable after 30 rows. Screenshots are in `.codex-tmp/audit-assets.png`
and `.codex-tmp/audit-labels-bottom.png`. Those images precede the later Markdown
heading typography fix. Keyboard, rotation, and light mode still need review.

Subsequent API 35 checks confirmed reading-size Markdown headings and retained
label input after landscape/portrait rotation. Link callbacks were confirmed by
`link-native-result.xml`, `link-absolute-result.xml`, and `link-relative-result.xml`.
`link-release-result.xml` proves the Release callback invokes the repository
Markdown resolver; it does not prove every GitHub relative URL convention is handled.

Account checks at 1.5 font scale confirmed readable names and preserved removal
target across rotation (`accounts-large.png`, `accounts-confirm-rotation.xml`).
Review checks confirmed expanded/horizontally scrolled code (`review-expanded.png`),
readable form and actions at 1.5 font scale (`review-large.png`), all three callback
results (`review-approve-result.xml`, `review-changes-result.xml`,
`review-comment-result.xml`), and expansion retained on rotation
(`review-rotation.xml`). `review-spacing.png` confirms separated author/time text.
These results do not validate remote review submission or account removal.

Use `--es sample actions` for a long workflow title/path, a dispatch error,
and a 12-line wide log with truncation notice. API 35 at 1.5 font scale:
`actions-large.png` shows separate metadata and controls; `dispatch-restored.xml`
contains `target=arm64` after cancelling, reopening, and rotating the dialog;
`log-wrap.png` and `log-wrap.xml` show the log wrapping and the action changing to
Scroll horizontally. Dispatch callbacks only report values; no workflow is run.

Use `--es sample actions-detail` for a failed run, long branch/actor/runner names,
and successful, timed-out and skipped steps. API 35 dark at 1.5 font scale:
`actions-summary.png` and `actions-steps.png` confirm complete metadata and step
names. `actions-light.png` covers the light summary at normal font scale;
`actions-steps-light.png` includes a feedback overlay over the final status.
`actions-rerun.xml` records the local RERUN callback, not a remote operation.

Release exclusion verified after `assembleRelease` succeeded: the arm64 release
APK manifest and defined DEX packages, plus the release R8 mapping, contain no
DesignAuditActivity or takagi.ru.monica.debug entry. Evidence:
`.codex-tmp/release-manifest.xml`, `.codex-tmp/release-dex-packages.txt`, and
`.codex-tmp/iteration-release-exclusion.log`. The release APK was not installed.

Use `--es sample create-issue` to test the actual CreateIssueViewModel Factory
and screen under Activity saved-state ownership. Repository calls are guarded
and Submit is intercepted. Type title/body, press Home, run `adb shell am kill
takagi.joyin.github.etoile`, confirm the PID disappears, then bring the existing
task back. API 35: new PID and both fields restored in create-restored.xml.
create-keyboard-fixed.png validates the corrected IME inset layout. This does
not establish signed-in navigation-stack restoration or remote creation.

Use `--es sample webhooks` for two same-name hooks with distinct IDs, many events,
and a long 503 response. webhooks-large.png validates dark 1.5-font readability;
hook-108-target.xml confirms the first management URL callback. This fixture
cannot verify actual GitHub permissions, deliveries or editing.
