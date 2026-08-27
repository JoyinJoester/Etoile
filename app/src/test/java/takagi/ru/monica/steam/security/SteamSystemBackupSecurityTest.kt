package takagi.ru.monica.steam.security

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class SteamSystemBackupSecurityTest {
    @Test
    fun manifestExplicitlyDisablesAndroidSystemBackup() {
        val document = parseXml(projectFile("app/src/main/AndroidManifest.xml"))
        val application = document.getElementsByTagName("application").item(0) as Element

        assertEquals("false", application.androidAttribute("allowBackup"))
        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.androidAttribute("dataExtractionRules"))
    }

    @Test
    fun legacyBackupRulesExcludeEveryStorageDomain() {
        val document = parseXml(projectFile("app/src/main/res/xml/backup_rules.xml"))

        assertEquals(REQUIRED_STORAGE_DOMAINS, excludedDomains(document.documentElement))
    }

    @Test
    fun android12RulesExcludeEveryCloudAndDeviceTransferDomain() {
        val document = parseXml(projectFile("app/src/main/res/xml/data_extraction_rules.xml"))

        listOf("cloud-backup", "device-transfer").forEach { tagName ->
            val section = document.getElementsByTagName(tagName).item(0) as Element
            assertEquals(REQUIRED_STORAGE_DOMAINS, excludedDomains(section))
        }
    }

    private fun excludedDomains(parent: Element): Set<String> {
        val excludes = parent.getElementsByTagName("exclude")
        return buildSet {
            repeat(excludes.length) { index ->
                val element = excludes.item(index) as Element
                assertEquals(".", element.getAttribute("path"))
                add(element.getAttribute("domain"))
            }
        }
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        val REQUIRED_STORAGE_DOMAINS = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref"
        )
    }
}
