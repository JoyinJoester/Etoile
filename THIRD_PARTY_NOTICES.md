# Third-Party Notices

## Monica for Android

Etoile's code base, Material 3 design language, navigation shell, and security components derive from
[Monica for Android](https://github.com/Monica-Pass/Monica-for-Android), Copyright (C) Monica contributors,
licensed under the GNU General Public License v3.0 or later. The extraction baseline is recorded in
[`SOURCE.md`](./SOURCE.md). The Steam feature layer, the Monica vault modules, and the MDBX storage engine
have since been removed from this repository.

The complete GPL-3.0 text is in this repository's [`LICENSE`](LICENSE) file.

## Google Sans Flex

`app/src/main/res/font/` ships `GoogleSansFlex` variants (`regular`, `metric`, `display`), used by the
typography in `ui/theme/Type.kt`.

Copyright (C) Google LLC, licensed under the Apache License, Version 2.0.

## AndroidX, Material Components, Kotlin, and OkHttp

Everything else arrives as a published build dependency declared in `gradle/libs.versions.toml` and
`app/build.gradle`: Jetpack AndroidX, Jetpack Compose and Material 3, `com.google.android.material`
color utilities (Apache License 2.0), Kotlin and kotlinx-serialization / kotlinx-coroutines (Apache
License 2.0), OkHttp and MockWebServer (Apache License 2.0), and JUnit (Eclipse Public License 1.0).

Each artifact carries its own license text and copyright notice inside the published package; the
dependency declarations above identify the exact versions used.

## GitHub

GitHub is a trademark of GitHub, Inc. / Microsoft. Etoile is an unofficial third-party client and is not
affiliated with, authorized by, or sponsored by GitHub.
