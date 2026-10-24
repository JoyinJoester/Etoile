package takagi.ru.monica.ui.components

import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.model.DefaultMarkdownTypography

/** Document text must not inherit a theme's decorative page-title font or display scale. */
internal fun markdownReadingTypography(): DefaultMarkdownTypography {
    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
    fun heading(size: Int, height: Int) = body.copy(
        fontSize = size.sp, lineHeight = height.sp, fontWeight = FontWeight.SemiBold
    )
    return DefaultMarkdownTypography(
        h1 = heading(28, 36), h2 = heading(24, 32), h3 = heading(20, 28),
        h4 = heading(18, 26), h5 = heading(16, 24), h6 = heading(16, 24),
        text = body, paragraph = body, ordered = body, bullet = body, list = body, table = body,
        quote = body.copy(fontStyle = FontStyle.Italic),
        code = body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp),
        inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        textLink = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
    )
}
