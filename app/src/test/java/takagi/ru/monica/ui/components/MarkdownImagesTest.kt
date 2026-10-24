package takagi.ru.monica.ui.components

import org.junit.Assert.*
import org.junit.Test

class MarkdownImagesTest {
    @Test fun resolvesGitLabDocumentImagesWithoutUsingGithubPaths() {
        val base = "https://gitlab.example.com/team/gitlab/group/repo/-/raw/abc123/docs/README.md"
        assertEquals("https://gitlab.example.com/team/gitlab/group/repo/-/raw/abc123/images/a%20b.png",
            resolveMarkdownImage("../images/a%20b.png", baseUrl = base))
        assertEquals("https://gitlab.example.com/uploads/preview.png",
            resolveMarkdownImage("/uploads/preview.png", baseUrl = base))
        assertNull(resolveMarkdownImage("file:///data/private.txt", baseUrl = base))
        assertNull(resolveMarkdownImage("https://user:pass@example.com/image", baseUrl = base))
    }

    @Test fun resolvesRelativeDocumentLinksQueriesAndFragmentsAndRejectsExecutableSchemes() {
        val base = "https://gitlab.example.com/group/repo/-/blob/abc123/docs/README.md"
        assertEquals("https://gitlab.example.com/group/repo/-/blob/abc123/CONTRIBUTING.md#setup",
            resolveMarkdownDocumentLink("../CONTRIBUTING.md#setup", base))
        assertEquals("$base#notes", resolveMarkdownDocumentLink("#notes", base))
        assertEquals("$base?plain=1", resolveMarkdownDocumentLink("?plain=1", base))
        assertEquals("mailto:maintainer@example.com", resolveMarkdownDocumentLink("mailto:maintainer@example.com", base))
        listOf("javascript:alert(1)", "data:text/html,test", "file:///etc/passwd", "https://user:pass@example.com/").forEach {
            assertNull(it, resolveMarkdownDocumentLink(it, base))
        }
    }

    @Test fun resolvesRelativeImagesAgainstDocumentDirectoryAndRef() {
        assertEquals("https://raw.githubusercontent.com/owner/repo/feature%2Fdocs/images/a%20b.png?raw=1",
            resolveMarkdownImage("../images/a%20b.png?raw=1#dark", MarkdownImageContext("owner/repo", "feature/docs", "docs/README.md")))
    }

    @Test fun rootRelativeImagesAndParentPathsStayInsideRepository() {
        val context = MarkdownImageContext("owner/repo", "main", "docs/guide.md")
        assertEquals("https://raw.githubusercontent.com/owner/repo/main/logo.svg", resolveMarkdownImage("/logo.svg", context))
        assertEquals("https://raw.githubusercontent.com/owner/repo/main/logo.svg", resolveMarkdownImage("../../../logo.svg", context))
    }

    @Test fun convertsGithubBlobPageToImageBytes() {
        assertEquals("https://raw.githubusercontent.com/o/r/main/logo.png?raw=true",
            resolveMarkdownImage("https://github.com/o/r/blob/main/logo.png?raw=true"))
        assertEquals("https://raw.githubusercontent.com/o/r/main/logo.png", resolveMarkdownImage("https://github.com/o/r/raw/main/logo.png"))
    }

    @Test fun preservesExternalImageEndpointsAndRejectsLocalResources() {
        assertEquals("https://img.shields.io/badge/build-pass-green", resolveMarkdownImage("//img.shields.io/badge/build-pass-green"))
        assertEquals("https://user-images.githubusercontent.com/123/a.png", resolveMarkdownImage("https://user-images.githubusercontent.com/123/a.png"))
        listOf("file:///data/private.txt", "content://provider/private", "javascript:alert(1)", "relative.png", "https://user:pass@example.com/image").forEach {
            assertNull(it, resolveMarkdownImage(it))
        }
    }

    @Test fun convertsHtmlImagesAndDecodesAttributes() {
        val output = prepareMarkdownImages("<p align=\"center\"><img src=\"./a.png?x=1&amp;y=2\" alt=\"A [logo]\"></p>")
        assertTrue(output, output.contains("![A \\[logo\\]](<./a.png?x=1&y=2>)"))
        assertFalse(output, output.contains("<p"))
    }

    @Test fun doesNotRewriteFencedOrInlineCode() {
        val markdown = "```html\n<img src=\"example.png\">\n```\n\n`<img src=\"inline.png\">`"
        assertEquals(markdown, prepareMarkdownImages(markdown))
    }

    @Test fun keepsStandardAndLinkedMarkdownImagesUnchanged() {
        val markdown = "![logo](./logo.png)\n\n[![build](https://img.shields.io/badge/build-pass-green)](https://example.com)"
        assertEquals(markdown, prepareMarkdownImages(markdown))
    }
}
