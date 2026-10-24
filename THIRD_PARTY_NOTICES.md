# Third-Party Notices

## Monica for Android

Etoile's code base, Material 3 design language, navigation shell, and security components derive from
[Monica for Android](https://github.com/Monica-Pass/Monica-for-Android), Copyright (C) Monica contributors,
licensed under the GNU General Public License v3.0 or later. The extraction baseline is recorded in
[`SOURCE.md`](./SOURCE.md). The Steam feature layer, the Monica vault modules, and the MDBX storage engine
have since been removed from this repository.

The complete GPL-3.0 text is in this repository's [`COPYING`](COPYING) file; the project notice is in [`LICENSE`](LICENSE).

## Bundled fonts

`app/src/main/res/font/` ships Google Sans Flex variants (`regular`, `metric`, `display`),
Space Grotesk, Space Mono and Doto. All four families are distributed under the SIL Open Font License, Version 1.1.

Copyright notices and complete upstream font licenses are bundled in [`docs/legal/fonts/`](docs/legal/fonts/):

- Google Sans Flex: Copyright 2015 The Google Sans Flex Authors ([license](docs/legal/fonts/googlesansflex-OFL.txt)).
- Space Grotesk: Copyright 2020 The Space Grotesk Project Authors ([license](docs/legal/fonts/spacegrotesk-OFL.txt)).
- Space Mono: Copyright 2016 The Space Mono Project Authors ([license](docs/legal/fonts/spacemono-OFL.txt)).
- Doto: Copyright 2024 The Doto Project Authors ([license](docs/legal/fonts/doto-OFL.txt)).

The application includes these full font license texts in its offline third-party notices.

## AndroidX, Material Components, Kotlin, and OkHttp

Everything else arrives as a published build dependency declared in `gradle/libs.versions.toml` and
`app/build.gradle`: Jetpack AndroidX, Jetpack Compose and Material 3, `com.google.android.material`
color utilities (Apache License 2.0), Kotlin and kotlinx-serialization / kotlinx-coroutines (Apache
License 2.0), OkHttp and MockWebServer (Apache License 2.0), and JUnit (Eclipse Public License 1.0).

The resolved release graph, including transitive modules and platform metadata, is recorded in
[`docs/legal/dependencies.json`](docs/legal/dependencies.json) and the [readable inventory](docs/legal/dependencies.md).
Published archive license/notice texts are preserved where supplied, together with the Apache 2.0
license and jsoup's MIT license. Cargo.lock registry packages, including build/target-specific
dependencies that may not ship in the Android library, are listed as a conservative superset;
their supplied license/copyright notices are preserved too. The JNI macro license omitted from
its crate archive is included from its recorded upstream commit.

The application combines this inventory and the complete bundled texts in its offline third-party
notices. `verifyThirdPartyNotices` checks the current release graph, Cargo.lock, and referenced texts
so dependency changes require regeneration and review. See [regeneration instructions](docs/acceptance/RELEASE_PROCESS.md).

## Markdown images

Markdown uses [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
and [Coil](https://github.com/coil-kt/coil), including its OkHttp network and SVG modules, under Apache License 2.0.
[jsoup](https://jsoup.org/) 1.23.2, Copyright Jonathan Hedley, is used to parse HTML image tags under the MIT License.
The parser preserves code examples and does not execute HTML scripts.

## GitHub

GitHub is a trademark of GitHub, Inc. / Microsoft. Etoile is an unofficial third-party client and is not
affiliated with, authorized by, or sponsored by GitHub.

## GitLab

GitLab is a trademark of GitLab B.V. Etoile is an independent client, not affiliated with or endorsed by GitLab.
