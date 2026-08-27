package takagi.ru.monica.github.domain

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubDomainArchitectureTest {
    @Test
    fun domainRemainsPureKotlinAndFrameworkIndependent() {
        val workingDirectory = File(".").canonicalFile
        val domainDirectory = listOf(
            File(workingDirectory, "src/main/java/takagi/ru/monica/github/domain"),
            File(workingDirectory, "app/src/main/java/takagi/ru/monica/github/domain")
        ).firstOrNull(File::isDirectory)

        assertNotNull("Unable to locate the GitHub domain source directory", domainDirectory)

        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import java.",
            "import javax."
        )
        val forbiddenAnnotations = setOf("@Composable", "@Immutable", "@Stable")
        val violations = domainDirectory!!
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { source ->
                source.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val statement = line.trim()
                    val isForbiddenImport = forbiddenImports.any(statement::startsWith)
                    val isForbiddenAnnotation = statement.substringBefore('(') in forbiddenAnnotations
                    if (isForbiddenImport || isForbiddenAnnotation) {
                        "${source.name}:${index + 1}: $statement"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            "GitHub domain must stay KMP-compatible and framework-independent:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
