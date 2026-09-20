package takagi.ru.monica.github.domain

data class FdroidSource(val url: String, val name: String, val fingerprint: String)

/** Public repository addresses and signing fingerprints, cross-checked against Droid-ify presets. */
object FdroidSources {
    val builtIn = listOf(
        FdroidSource("https://f-droid.org/repo", "F-Droid", "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"),
        FdroidSource("https://guardianproject.info/fdroid/repo", "Guardian Project official releases", "B7C2EEFD8DAC7806AF67DFCD92EB18126BC08312A7F2D6F3862E46013C7A6135"),
        FdroidSource("https://apt.izzysoft.de/fdroid/repo", "IzzyOnDroid F-Droid repository", "3BF0D6ABFEAE2F401707B6D966BE743BF0EEE49C2561B9BA39073711F628937A"),
        FdroidSource("https://microg.org/fdroid/repo", "microG Project", "9BD06727E62796C0130EB6DAB39B73157451582CBD138E86C468ACC395D14165"),
        FdroidSource("https://molly.im/fdroid/foss/fdroid/repo", "Molly", "5198DAEF37FC23C14D5EE32305B2AF45787BD7DF2034DE33AD302BDB3446DF74"),
        FdroidSource("https://archive.newpipe.net/fdroid/repo", "NewPipe", "E2402C78F9B97C6C89E97DB914A2751FDA1D02FE2039CC0897A462BDB57E7501"),
        FdroidSource("https://www.collaboraoffice.com/downloads/fdroid/repo", "Collabora Office", "573258C84E149B5F4D9299E7434B2B69A8410372921D4AE586BA91EC767892CC"),
        FdroidSource("https://briarproject.org/fdroid/repo", "Briar", "1FB874BEE7276D28ECB2C9B06E8A122EC4BCB4008161436CE474C257CBF49BD6"),
        FdroidSource("https://releases.threema.ch/fdroid/repo", "Threema Libre", "5734E753899B25775D90FE85362A49866E05AC4F83C05BEF5A92880D2910639E"),
        FdroidSource("https://www.cromite.org/fdroid/repo", "Cromite", "49F37E74DEE483DCA2B991334FB5A0200787430D0B5F9A783DD5F13695E9517B"),
        FdroidSource("https://static.cryptomator.org/android/fdroid/repo", "Cryptomator", "f7c3ec3b0d588d3cb52983e9eb1a7421c93d4339a286398e71d7b651e8d8ecdd"),
        FdroidSource("https://cdn.kde.org/android/stable-releases/fdroid/repo", "KDE Stables", "13784ba6c80ff4e2181e55c56f961eed5844cea16870d3b38d58780b85e1158f"),
        FdroidSource("https://app.simplex.chat/fdroid/repo", "SimpleX Chat", "9F358FF284D1F71656A2BFAF0E005DEAE6AA14143720E089F11FF2DDCFEB01BA"),
        FdroidSource("https://fdroid.mm20.de/repo", "MM20 Apps", "156FBAB952F6996415F198F3F29628D24B30E725B0F07A2B49C3A9B5161EEE1A"),
        FdroidSource("https://breezy-weather.github.io/fdroid-repo/fdroid/repo", "Breezy Weather", "3480A7BB2A296D8F98CB90D2309199B5B9519C1B31978DBCD877ADB102AF35EE"),
        FdroidSource("https://gh.artemchep.com/keyguard-repo-fdroid/repo", "Keyguard Project", "03941CE79B081666609C8A48AB6E46774263F6FC0BBF1FA046CCFFC60EA643BC"),
        FdroidSource("https://f5a.torus.icu/fdroid/repo", "Fcitx5 for Android F-Droid Repo", "5D87CE1FAD3772425C2A7ED987A57595A20B07543B9595A7FD2CED25DFF3CF12"),
        FdroidSource("https://fdroid.ironfoxoss.org/fdroid/repo", "IronFox", "C5E291B5A571F9C8CD9A9799C2C94E02EC9703948893F2CA756D67B94204F904"),
    )
    val defaults = setOf(builtIn[0].url, builtIn.first { it.name.startsWith("Izzy") }.url)
}
