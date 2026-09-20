# Issue template verification

Verified on 2026-09-17. The fixture runs the production parser, ViewModel, and Compose screens with local template and publisher implementations. No real GitHub issues were posted.

## Build and tests

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:compileReleaseKotlin :app:processReleaseMainManifest --console=plain
```

- Build succeeded; 424 unit tests passed, with no failures, errors, or skipped tests.
- Android lint: 0 errors, 104 existing warnings. The build still reports the existing R8 Kotlin metadata and Gradle deprecation warnings.
- Release Kotlin compilation and main manifest processing passed. The compiled Release classes and main manifest exclude the debug fixture package and activity. A full Release APK build was not run for this change.
- Both debug ABI packages contain identical DEX payloads (CRC and length comparison).

## Native checks

17 distinct checks passed on `emulator-5554`:

- Repositories without templates offer Bug report, Feature request, and Question in Chinese and English. Language switching and cancelled template replacement preserve answers.
- Repository YAML controls validate required answers, preview Markdown, and create a local issue. Markdown templates, confirmed replacement, template-load retry, and repository template requirements are exercised.
- Drafts survive window/font changes. Failed publication preserves title and body for a successful retry. The final build clears focus before submission so the keyboard stays closed after an error.
- Nothing, Material 3 Expressive, and Miuix forms work at 320 dp width and 150% text size. Material controls are also checked at 200%, and tablet forms retain their centered 640 dp limit.

The built-in and earlier flow checks were captured on build `07`. Build `08` adds the submission focus fix; its failure/retry flow and the full display matrix were checked again. [native-validation.json](native-validation.json) records the APK for each check and deduplicates repeated chooser checks.

## Artifacts

- `Etoile-Android-armeabi-v7a-0.1.0-26091701-08.APK` (armeabi-v7a)
- `Etoile-Android-arm64-v8a-0.1.0-26091701-08.APK` (arm64-v8a)

The APKs are under `app/build/outputs/apk/debug/` in the project workspace. [Bilingual preview](bilingual-templates.webp) combines actual native captures. The [screens/](screens/) directory contains individual captures, including large-text controls and the final publication retry.
