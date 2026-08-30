package takagi.ru.monica.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/**
 * 完整的 Markdown 渲染（GFM）：标题、列表、表格、任务列表、引用、
 * 围栏代码块、图片、删除线等全部支持，配色自动跟随 Material 3 全局主题
 * （Nothing / Miuix 设计下同样跟随映射后的色板）。
 *
 * 内部基于 multiplatform-markdown-renderer，图片经 Coil3 异步加载。
 *
 * [imageBitmaps] 与 [maxElements] 为旧签名兼容参数，已不再参与渲染。
 */
@Composable
fun MarkdownPreviewText(
    markdown: String,
    imageBitmaps: Map<String, Bitmap> = emptyMap(),
    onOpenExternalLink: (String) -> Unit = {},
    renderImages: Boolean = true,
    maxElements: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = markdown,
        colors = markdownColor(),
        typography = markdownTypography(),
        imageTransformer = Coil3ImageTransformerImpl,
        components = if (renderImages) {
            markdownComponents()
        } else {
            // 不渲染图片时占位为空（issue/PR 正文里的表情图片等不显示）
            markdownComponents(image = { })
        },
        modifier = modifier
    )
}
