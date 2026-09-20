# Nothing page audit

Status is evidence-based. A passing JVM test is not visual approval. Screenshots
from earlier APKs establish only the behavior they actually show. Dark mode is
the primary review mode; light mode, large text, and narrow/landscape layouts
remain required. The full project goal is not complete.

Sources: current `github/feature`, navigation graphs, screenshots in `.codex-tmp`,
and the chronological record in `NOTHING_ITERATION.md`.

| Page / flow | Evidence currently available | Remaining verification |
| --- | --- | --- |
| Home | Dark phone screenshot; shared navigation large-text screenshot | Signed-in data, light mode, wide layout |
| Explore / search scopes | Real public results, scope picker, dark/light screenshots | Long result rows, pagination failure, wide layout |
| Explore flip mode | Source implementation | Device navigation and empty/error states |
| Inbox | ViewModel tests for pagination and stale read callbacks; long-title fixture | Device row layout, triage menu, empty filters, account transitions |
| Profile | Source layout and adaptive achievement columns | Signed-in dark/light screenshots, large text and long bio |
| Public user profile | Source and repository tests | Full profile, follow state, long data, tabs |
| Followers / following | Source implementation | Pagination and empty/error views on device |
| My repositories | Search scope UI and retry regression tests | Search, no-match pagination, clear query, large text on device |
| My Issues / PRs | Open/closed/all filters and ViewModel tests | Full device flow, long titles and status changes |
| Star collections | Filtering and batch ViewModel tests | Main selection flow, empty/error views, dark/light screenshots |
| Label manager | 30-row fixture reaches create field; input retained after rotation | Real create/rename/delete feedback; keyboard layout; light mode |
| Label assignment / batch | Scroll and quantity layout implemented | Multi-selection, mixed assignments, long labels on device |
| Repository detail | Source improvements and permission tests | Full device review of all tabs and long names |
| Repository files | Binary/large-file fallback; source paging tests | Large file, Markdown links, code scroll and branch switching |
| Branches / tags | Ref selector paging status repaired | Multi-page filters, tag-only data, errors and retry on device |
| Collaborators | Source and ViewModel tests | Role controls, denied permission, scrolling sheets |
| Webhooks (read-only) | Source and ViewModel tests; per-hook GitHub management link added | Long response/ID/link fixture check; denied permission and error recovery. Editing and delivery management use GitHub web, not native forms |
| Organizations | Source and ViewModel tests | Long names, membership state, pagination on device |
| Issue list / detail | Source, metadata and ViewModel tests | Comments, management sheets, errors and refresh on device |
| Create Issue | Keyboard inset fix verified; production Factory restored both fields after Android process death in fixture | Real signed-in navigation-stack recovery, validation and submit response |
| PR list / detail / review | Source, review forms and ViewModel tests | Tabs, long diffs, review submission UI, permissions |
| Commits / diff | Explicit long-patch expansion and source tests | Large patch expand/collapse, binary files and text selection |
| Release list | Source and pagination tests | Empty/error, draft/prerelease and large text on device |
| Release detail | Long notes asset jump, full filenames, reading headings; normal/1.5x metadata screenshots | Light mode, link conventions, real download handoff |
| Actions workflows / runs / jobs | Source, paging tests, dispatch input parser tests | Failed/cancelled states, log wrapping, dispatch form rotation |
| Store directory / sources | Real public results; grid 2-to-1 column text scaling screenshots | F-Droid errors, many sources, all filters and keyboard |
| Store app / downloads | Source download and parser behavior; detail retry | Download progress, install handoff, cancellation, unsupported device |
| Sign-in / embedded browser | Large-text sign-in screenshots and policy tests | Full OAuth/device/token flows with available credentials |
| Account manager | Long-name fixture at 1.5 font; removal confirmation target retained on rotation | Real multiple-account operations, busy/error state, light mode |
| Settings | Dark/light and named palettes on device; radio semantics | Other languages, all alternate themes, wide layout |

Shared checks still outstanding:

- Triage remaining Lint warnings; latest lintDebug has 0 errors and 106 warnings.
- Review custom value slots in metadata rows beyond Release.
- Complete light-mode and landscape checks for every routed screen.
- Validate functional controls with production state; fixture toast callbacks do
  not validate persistence, API calls, downloads, or authentication.
- Preserve debug-source isolation; arm64 Release APK exclusion checked before later fixture additions.
- Keep build/toolchain warnings and actual test totals current.

`CopilotPlaceholderScreen` has no navigation call site in the inspected source.
It is an unused placeholder, not evidence of Copilot support. Home Discussions
and Projects currently lead to GitHub's web pages and should be described as such.

Next priority: complete account and creation forms, then review Actions and PR
flows with deterministic state fixtures; address any runtime layout findings
before collecting further completion evidence.

Latest evidence (2026-09-13): account long-name/rotation confirmation and PR
diff expansion/horizontal reading/rotation/large-font form have device fixture
coverage. All three review action callbacks were observed, without remote writes.
Shared author/time spacing was fixed and visually checked. Full unit regression:
329 tests, 0 failures; debug APK assembled and installed. Creation process recovery,
Actions runtime checks, full light/landscape coverage, release fixture exclusion,
and existing Lint errors remain open.

Actions follow-up: workflow rows now separate identity from controls and preserve
dispatch input across dialog close/reopen and rotation. API 35 at 1.5 font scale
verified long title/path, error visibility, restored parameter input and wrapped
logs through debug fixtures. Run summary/steps, light mode and real dispatch
results still need coverage. Debug assembly passed after a transient dex lock retry.

Actions detail follow-up: step status now sits below the full-width step name;
long actor identities can wrap. Dark 1.5-font run summary and all three step
statuses visually verified; normal-font light summary checked. Local RERUN
callback observed. Fixture overlay partially covers the last light-mode step
status, so that screenshot is not complete light-step evidence.

Release exclusion gate: assembleRelease succeeded; arm64 APK manifest/DEX package
listing and release R8 mapping contain no debug DesignAuditActivity or debug
package. This closes fixture exclusion for the inspected release artifact;
no release installation or end-to-end release smoke test was performed.

Static-check follow-up: custom color generation now uses public MaterialKolor
color utilities instead of Material Android restricted APIs. The manifest's
receiver removal marker has a local MissingClass explanation/suppression.
Latest full regression is 330 tests, 0 failures; lintDebug passes with 0 errors,
106 warnings (iteration-color-lint.log). The prior 90-error gate is closed;
remaining warnings still need triage. assembleDebug passed with the new dependency.
Custom-color device appearance and remaining page verification are still open.

Create Issue follow-up: corrected duplicate IME inset application by consuming
Scaffold padding before imePadding. API 35 screenshot create-keyboard-fixed.png
shows the form and submit control above the keyboard. The create-issue fixture
uses the production screen/Factory with Android CreationExtras; after background
am kill, PID changed 21292 -> 21423 and both fields restored (create-restored.xml).
This closes the Activity/Factory process-recovery check, while real signed-in
navigation-stack restoration remains unverified. Debug assembly passed.

Webhook follow-up: same-name hooks now show IDs; events and selectable responses
are complete, with HTTP error emphasis and individual GitHub settings links.
Dark 1.5-font fixture verified long content and /settings/hooks/108 callback.
Native editing/deliveries are not implemented; management uses GitHub web.

Repository file review: current source already has explicit binary/too-large
fallbacks, selectable monospace text with vertical and horizontal scrolling,
Markdown relative-link resolution, and independent branch/tag loading states.
No code change was needed in this pass; long-file and real ref-switch device
coverage remain open.

Explore flip mode review: the repository-only toolbar toggle is wired from
ExploreScreen, with saveable mode state, loading skeleton, empty/error/retry
states, near-end pagination and repository navigation. It is not an orphaned
placeholder. Device long-result and landscape checks remain open.

Label assignment review: the editor sheet uses a bounded LazyColumn with
pagination, disabled rows while saving, and horizontally scrollable Nothing
label capsules so long names remain intact; descriptions are limited to two
lines. Multi-select and mixed-state device interaction remain open.

Environment consistency check: emulator is restored to font scale 1.0 with
rotation enabled and user rotation 0. The latest arm64 debug APK is present;
working-tree changes remain intentionally uncommitted for continued iteration.

Inbox runtime follow-up: fixed confirmation ownership using a saveable ID
resolved against current items and cleared if absent/auth required. Confirmation
is disabled during triage. API 35 large-font fixture/menu and rotated confirmation
verified (inbox-large.png, inbox-menu.xml, inbox-confirm-rotation.xml). No remote
unsubscribe performed. Repository-name typography dominates the large-font row;
visual hierarchy needs follow-up. Fixture now respects system bars.

Inbox hierarchy follow-up: repository labels now use bodySmall/onSurfaceVariant
with extra spacing, keeping notification titles primary while retaining long
repository identifiers. API 35 1.5-font fixture rechecked this change in
inbox-hierarchy.png; build succeeded and emulator settings were restored.

Latest full regression after webhook retry changes: 334 test cases, 0 failures
(iteration-full-regression.log). No new device fixture run in this pass; emulator
settings remain restored.

Quality regression after recent Profile, Inbox, Webhook, repository-file/ref,
and Actions changes: testDebugUnitTest and lintDebug both succeeded. 334 tests,
0 failures, 0 lint errors; existing warnings remain tracked.
