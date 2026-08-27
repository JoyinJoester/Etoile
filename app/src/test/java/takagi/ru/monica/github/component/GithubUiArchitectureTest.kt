package takagi.ru.monica.github.component

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubUiArchitectureTest {
    @Test
    fun userFacingTextLiteralsStayInResources() {
        val allowedLiterals = setOf("Etoile")
        val rawText = Regex("""\bText\s*\(\s*(?:text\s*=\s*)?"([^"$]*)"""")
        val violations = uiSources().flatMap { source ->
            rawText.findAll(source.readText()).mapNotNull { match ->
                val literal = match.groupValues[1]
                if (literal.isBlank() || literal in allowedLiterals) {
                    null
                } else {
                    "${source.name}: raw Text literal '$literal'"
                }
            }
        }.toList()

        assertTrue(
            "GitHub UI text must use localized resources:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun externalIconButtonsUseTheSharedAccessibleComponent() {
        val violations = uiSources()
            .filterNot { it.name == "GithubComponents.kt" }
            .filter { source ->
                val text = source.readText()
                "IconButton(" in text && "OpenInNew" in text
            }
            .map { "${it.name}: use GithubOpenOnGithubButton" }
            .toList()

        assertTrue(
            "External actions must share one icon, label, and touch target:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun iconButtonsDoNotForceTargetsBelowMaterialMinimum() {
        val violations = uiSources().flatMap { source ->
            iconButtonArguments(source.readText()).mapNotNull { arguments ->
                val size = Regex("""\.size\(\s*(\d+)\.dp\s*\)""")
                    .find(arguments)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                if (size != null && size < MINIMUM_TOUCH_TARGET_DP) {
                    "${source.name}: IconButton forced to ${size}dp"
                } else {
                    null
                }
            }
        }.toList()

        assertTrue(
            "IconButton targets must be at least ${MINIMUM_TOUCH_TARGET_DP}dp:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun modalBottomSheetsUseTheSharedGithubShell() {
        val violations = uiSources()
            .filterNot { it.name == "GithubComponents.kt" }
            .filter { source -> Regex("""\bModalBottomSheet\s*\(""").containsMatchIn(source.readText()) }
            .map { "${it.name}: use GithubModalBottomSheet" }
            .toList()

        assertTrue(
            "GitHub sheets must share container styling and future inset behavior:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun featureAndSharedUiDoNotOwnNavigationControllers() {
        val violations = uiSources()
            .filterNot { it.name == "EtoileGithubApp.kt" }
            .filter { source ->
                val text = source.readText()
                "NavController" in text || "NavHostController" in text
            }
            .map { "${it.name}: pass a focused navigation callback or use a shared navigator" }
            .toList()

        assertTrue(
            "Feature and shared UI must not depend on navigation controllers:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun pullToRefreshUsesTheSharedGithubShell() {
        val violations = uiSources()
            .filterNot { it.name == "GithubRefreshComponents.kt" }
            .filter { source -> Regex("""\bPullToRefreshBox\s*\(""").containsMatchIn(source.readText()) }
            .map { "${it.name}: use GithubPullToRefreshBox" }
            .toList()

        assertTrue(
            "GitHub refresh gestures must share indicator styling and behavior:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun userSurfacesUseTheSharedAvatarComponent() {
        val requiredUsages = mapOf(
            "GithubUserComponents.kt" to 2,
            "ProfileScreen.kt" to 1,
            "PublicUserProfileScreen.kt" to 1
        )
        val violations = requiredUsages.mapNotNull { (fileName, minimumUsages) ->
            val source = uiSources().firstOrNull { it.name == fileName }
                ?: return@mapNotNull "$fileName: source not found"
            val usages = Regex("""\bGithubAvatar\s*\(""").findAll(source.readText()).count()
            if (usages < minimumUsages) {
                "$fileName: expected at least $minimumUsages GithubAvatar usages, found $usages"
            } else {
                null
            }
        }

        assertTrue(
            "GitHub user surfaces must share avatar loading and fallback behavior:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private fun uiSources(): Sequence<File> {
        val workingDirectory = File(".").canonicalFile
        val githubDirectory = listOf(
            File(workingDirectory, "src/main/java/takagi/ru/monica/github"),
            File(workingDirectory, "app/src/main/java/takagi/ru/monica/github")
        ).firstOrNull(File::isDirectory)

        assertNotNull("Unable to locate the GitHub source directory", githubDirectory)

        return githubDirectory!!
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { source ->
                val relative = source.relativeTo(githubDirectory).invariantSeparatorsPath
                relative == "EtoileGithubApp.kt" ||
                    relative.startsWith("component/") ||
                    relative.startsWith("feature/") ||
                    relative.startsWith("settings/")
            }
    }

    private fun iconButtonArguments(source: String): Sequence<String> = sequence {
        var searchFrom = 0
        while (true) {
            val marker = source.indexOf("IconButton(", startIndex = searchFrom)
            if (marker < 0) break
            val argumentStart = marker + "IconButton(".length
            var depth = 1
            var index = argumentStart
            var quote: Char? = null
            var escaped = false
            while (index < source.length && depth > 0) {
                val char = source[index]
                if (quote != null) {
                    if (escaped) {
                        escaped = false
                    } else if (char == '\\') {
                        escaped = true
                    } else if (char == quote) {
                        quote = null
                    }
                } else {
                    when (char) {
                        '"', '\'' -> quote = char
                        '(' -> depth++
                        ')' -> depth--
                    }
                }
                index++
            }
            if (depth == 0) yield(source.substring(argumentStart, index - 1))
            searchFrom = index.coerceAtLeast(marker + 1)
        }
    }

    private companion object {
        const val MINIMUM_TOUCH_TARGET_DP = 48
    }
}
