# Android store and reader detail

The store opens the F-Droid catalog by default. It includes 18 selectable repositories, with F-Droid and IzzyOnDroid enabled by default. Enabled repositories persist locally. Catalogs merge by package name and choose the highest compatible version code. Search, installed-app and update filters operate on the complete synchronized catalog, rather than a fixed 60-item prefix. Each source failure is reported independently.

Index-v1 JARs are verified against pinned SHA-256 signing-certificate fingerprints. APK candidates require SHA-256 hashes, a compatible SDK range and a supported native ABI (or no native ABI restriction). Index metadata order does not matter. APK downloads are hashed before parsing or exposing installation. Android retains control of installation confirmation. Package update comparisons use version codes. Manual refresh bypasses the six-hour index cache.

GitHub recommendations and custom repository sources are verified against published release assets before being displayed. The resolver follows release pagination until it finds APKs, ignores drafts and known incompatible ABIs, and excludes desktop archives. Network errors remain errors, not proof that an app has no APK. Unknown APK filename architectures can only be determined by the actual package/system installer.

Application details are a separate navigation destination with one toolbar and no tab bar. Installation precedes descriptions and release notes. Long text is expandable. Reaching the bottom and pulling upward at least 96 dp, then releasing, moves to the next application in the active catalog. Normal scrolling does not trigger it. There is no wraparound after the last app. A horizontal right drag or explicit button opens the project; GitHub URLs use the native project route and other hosts use the browser. System back restores the catalog.

## Reference study

- Capy Reader, commit `6aa1ddfd154189c3deb107210e35952dceddf2a3`: `ArticleView.kt` (ArticlePullRefresh), `ArticleTransition.kt`, `ArticleScrollState.kt`, and horizontal gesture direction handling. The Etoile gesture implementation was written for its existing Compose lists; no reader module was imported.
- Droid-ify, commit `ff1453ed957f3b2382abce9a7eb9a9db8def3a3a`: repository preset public URLs/fingerprints and application-detail organization. Presets include F-Droid, IzzyOnDroid, Guardian Project, microG, Molly, NewPipe, Collabora Office, Briar, Threema Libre, Cromite, Cryptomator, KDE Stables, SimpleX Chat, MM20, Breezy Weather, Keyguard, Fcitx5 and IronFox.

## Verification

- Release selection regression tests cover desktop-only latest releases, pagination fallback, draft exclusion, architecture compatibility, universal APKs, no-APK catalogs and network failures.
- Native reader checks: independent toolbar, download visibility, expandable text, ordinary-scroll protection, bottom pull, end of list, right swipe, back to catalog and source management.
- Live F-Droid and IzzyOnDroid synchronization passed signing verification on an emulator. A real 2.7 MB APK download passed its index SHA-256 checksum and PackageManager parsing, and exposed the install button. Installation was not executed.
- The emulator had broken DNS. Live verification used a temporary loopback HTTPS CONNECT proxy without TLS interception; proxy settings are restored after verification.
- 17 of 18 preset index addresses responded during the availability check; Cromite timed out. Availability does not guarantee that every source remains reachable on every network.

Final validation: 440 JVM tests passed; lint reported zero errors. The default sources produced 5,268 compatible apps on the test emulator. Search found Capy Reader, and its GitHub project opened through the native repository route; system back returned to the detail and then the filtered catalog. A transient emulator native JIT/accessibility crash occurred during one replay; after Android package AOT compilation, the same gesture checks passed. No product code workaround was introduced for that emulator event.
