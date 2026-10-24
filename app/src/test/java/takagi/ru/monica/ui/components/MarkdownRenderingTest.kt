package takagi.ru.monica.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MarkdownRenderingTest {
    @Test fun backgroundPreparationKeepsTheWholeDocumentConsistent() = runTest {
        val document = prepareMarkdownDocument(
            "<p><a href=\"docs\"><img src=\"logo.svg\" width=\"112\" alt=\"Logo\"></a></p>\n\n[Guide][guide]\n\n[guide]: https://example.com/guide",
            darkTheme = true,
        )
        assertTrue(document.content.contains("](<docs>)"))
        assertEquals("logo.svg", document.images.single().destination)
        assertEquals(112, document.images.single().width)
        assertEquals("https://example.com/guide", document.references["guide"])
    }

    private fun render(markdown: String, renderImages: Boolean = true): AnnotatedString {
        val content = prepareMarkdownContent(markdown)
        return content.buildMarkdownAnnotatedString(
            style = TextStyle.Default,
            annotatorSettings = DefaultAnnotatorSettings(
                linkTextSpanStyle = TextLinkStyles(), codeSpanStyle = SpanStyle(),
                annotator = markdownReadingAnnotator(markdownInlineImages(content).associateBy { it.offset }, renderImages, SpanStyle(), references = markdownReferenceLinks(content)),
            ),
        )
    }

    @Test fun namedAndNumericEntitiesRenderAsTextIncludingNonBmpCharacters() {
        assertEquals("Download \u00a0·\u00a0 Docs \u2003 Sponsors © 😀", render("Download &nbsp;·&nbsp; Docs &emsp; Sponsors &copy; &#x1F600;").text)
        assertEquals("&unknown; &amp; <img>", render("&unknown; &amp;amp; &lt;img&gt;").text)
    }

    @Test fun entitiesCannotInjectMarkdownOrHtmlAndEscapedEntitiesStayLiteral() {
        val result = render("&#42;&#42;plain&#42;&#42; &lt;img src=&#34;x&#34;&gt; \\&copy;")
        assertEquals("**plain** <img src=\"x\"> &copy;", result.text)
        assertTrue(result.getStringAnnotations(0, result.length).isEmpty())
    }

    @Test fun codeSpansKeepEntitiesBackslashesAndHtmlLiteral() {
        assertEquals("&nbsp; \\* <img src=\"x\">", render("`&nbsp; \\* <img src=\"x\">`").text)
        assertEquals("a ` b", render("`` a ` b ``").text)
    }

    @Test fun htmlAnchorsKeepTargetsAndImageLinks() {
        val content = prepareMarkdownContent("<p><a href=\"https://example.com?a=1&amp;b=2\"><img src=\"logo.svg\" width=\"112\" alt=\"Logo\"></a></p>")
        assertTrue(content, content.contains("](<https://example.com?a=1&b=2>)"))
        val result = render(content)
        assertEquals("https://example.com?a=1&b=2", (result.getLinkAnnotations(0, result.length).single().item as LinkAnnotation.Url).url)
        assertEquals(112, markdownInlineImages(content).single().width)
    }

    @Test fun inlineHtmlLinksAndFormattingWorkWithoutAnyImages() {
        val result = render("Read <a href=\"docs/start.md\">the <strong>guide</strong></a><br>Next")
        assertEquals("Read the guide\nNext", result.text)
        assertEquals("docs/start.md", (result.getLinkAnnotations(0, result.length).single().item as LinkAnnotation.Url).url)
    }

    @Test fun htmlFormattingAndEntitiesSurviveBlockConversion() {
        val content = prepareMarkdownContent("<div><h3>Title</h3><p><strong>Bold</strong> &lt;img&gt; &amp;amp;</p><script>evil()</script><style>p{}</style></div>")
        assertTrue(content, content.contains("### Title"))
        assertTrue(content, content.contains("**Bold**"))
        assertFalse(content, content.contains("evil()"))
        assertEquals("Bold <img> &amp;", render(content.substringAfter("### Title").trim()).text)
    }

    @Test fun codeFencesIndentedCodeAndInlineExamplesAreNotRewritten() {
        val code = "```html\n<img width=\"112\" src=\"a.svg\"> &nbsp;\n```\n\n    <p>&emsp;</p>\n\n`<a href=\"x\">&nbsp;</a>`"
        assertEquals(code, prepareMarkdownContent(code))
        assertTrue(markdownInlineImages(code).isEmpty())
    }

    @Test fun htmlCodeIsConvertedToLiteralCodeWithoutLosingEntities() {
        val content = prepareMarkdownContent("<pre><code>&lt;img&gt; &amp;nbsp;\n```</code></pre>")
        assertTrue(content, content.contains("````\n<img> &nbsp;\n```\n````"))
        assertEquals("Run **literal** <img> &nbsp; now", render("Run <code>**literal** &lt;img&gt; &amp;nbsp;</code> now").text)
    }

    @Test fun pictureSelectsMatchingThemeAndKeepsFallbackDimensionsAndLink() {
        val html = "<p><a href=\"sponsors\"><picture><source media=\"(prefers-color-scheme: dark)\" srcset=\"dark.svg\"><img src=\"light.svg\" height=\"36\" alt=\"Sponsor\"></picture></a></p>"
        val light = markdownInlineImages(prepareMarkdownContent(html)).single()
        val darkContent = prepareMarkdownContent(html, darkTheme = true)
        val dark = markdownInlineImages(darkContent).single()
        assertEquals("light.svg", light.destination)
        assertEquals("dark.svg", dark.destination)
        assertEquals(36, dark.height)
        assertTrue(darkContent, darkContent.contains("](<sponsors>)"))
    }

    @Test fun htmlTableKeepsColumnsAndLinks() {
        val content = prepareMarkdownContent("<table><tr><th>Name</th><th>Link</th></tr><tr><td>Client</td><td><a href=\"docs\">Docs</a></td></tr></table>")
        assertTrue(content, content.contains("| Name | Link |\n| --- | --- |\n| Client | [Docs](<docs>) |"))
    }

    @Test fun multipleInlineImagesGetDistinctKeysSizesAndOriginalLinkTargets() {
        val markdown = "[![Build](https://img.shields.io/build.svg)](https://example.com/build) ![Logo](logo.png)"
        val images = markdownInlineImages(markdown)
        assertEquals(2, images.size)
        assertNotEquals(images[0].key, images[1].key)
        assertEquals(Size(100f, 20f), markdownImageSize(images[0], Size(100f, 20f), 360f))
        assertEquals(Size(360f, 180f), markdownImageSize(images[1], Size(1200f, 600f), 360f))
        val result = render(markdown)
        val imageTags = result.getStringAnnotations(0, result.length).map { it.item }.filter { it.startsWith("etoile-image-") }
        assertEquals(images.map { it.key }, imageTags)
        assertEquals("https://example.com/build", (result.getLinkAnnotations(0, result.length).single().item as LinkAnnotation.Url).url)
    }

    @Test fun repeatedImageUrlsCanHaveDifferentExplicitDimensions() {
        val images = markdownInlineImages(prepareMarkdownContent("<p><img src=\"a.svg\" width=\"112\"><img src=\"a.svg\" height=\"36\"></p>"))
        assertEquals(2, images.size)
        assertEquals(Size(112f, 56f), markdownImageSize(images[0], Size(400f, 200f), 360f))
        assertEquals(Size(72f, 36f), markdownImageSize(images[1], Size(400f, 200f), 360f))
    }

    @Test fun explicitDimensionsClampToScreenWidthWithTheSameAspectRatio() {
        val image = MarkdownInlineImage(0, "a.png", "Screenshot", width = 900, height = 600)
        assertEquals(Size(360f, 240f), markdownImageSize(image, Size(1800f, 1200f), 360f))
        val pending = markdownImageSize(image, Size.Unspecified, 360f)
        assertTrue(pending.width.isFinite() && pending.height.isFinite())
    }

    @Test fun imageReferenceDefinitionsResolveWithoutTreatingCodeAsImages() {
        val markdown = "![Logo][logo]\n\n[logo]: ./logo.svg \"Title\"\n\n`![No](private.png)`"
        val image = markdownInlineImages(markdown).single()
        assertEquals("./logo.svg", image.destination)
        assertEquals("Logo", image.description)
        assertTrue(render(markdown).getStringAnnotations(0, render(markdown).length).any { it.item == image.key })
    }

    @Test fun absoluteHtmlImagesAndReferenceImagesKeepTheirUrls() {
        val html = "<p><img src=\"https://example.com/logo.svg?a=1&amp;b=2\" width=\"112\" alt=\"Logo\"></p>"
        val content = prepareMarkdownContent(html)
        assertEquals("https://example.com/logo.svg?a=1&b=2", markdownInlineImages(content).single().destination)
        assertTrue(render(content).getStringAnnotations(0, render(content).length).any { it.item.startsWith("etoile-image-") })
        val reference = "![Badge][badge]\n\n[badge]: <https://img.shields.io/badge/pass-green>"
        assertEquals("https://img.shields.io/badge/pass-green", markdownInlineImages(reference).single().destination)
    }

    @Test fun referenceLinksAndBracketedDestinationsDecodeOnceAndPreserveFormatting() {
        val result = render("[**Docs**][docs]\n\n[docs]: <https://example.com?a=1&amp;b=2>")
        assertEquals("Docs", result.text)
        assertEquals("https://example.com?a=1&b=2", (result.getLinkAnnotations(0, result.length).single().item as LinkAnnotation.Url).url)
        val inline = render("[Docs](<https://example.com?q=&amp;amp;>)")
        assertEquals("https://example.com?q=&amp;", (inline.getLinkAnnotations(0, inline.length).single().item as LinkAnnotation.Url).url)
    }

    @Test fun imageOptOutAlsoHidesLinkedAndHtmlImages() {
        assertEquals("Before  after", render("Before [![Logo](a.svg)](docs) after", renderImages = false).text)
        assertFalse(render("<p>Before <img src=\"a.svg\" alt=\"Logo\"> after</p>", renderImages = false).text.contains("Logo"))
    }

    @Test fun documentTypographyIsIndependentOfDisplayThemeAndLinksInheritTextSize() {
        val typography = markdownReadingTypography()
        assertEquals(28.sp, typography.h1.fontSize)
        assertEquals(20.sp, typography.h3.fontSize)
        assertEquals(16.sp, typography.paragraph.fontSize)
        assertEquals(FontFamily.Default, typography.paragraph.fontFamily)
        assertEquals(typography.text, typography.list)
        assertFalse(typography.textLink.style!!.fontSize.isSp)
    }
}
