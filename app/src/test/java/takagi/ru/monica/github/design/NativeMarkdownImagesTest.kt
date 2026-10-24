package takagi.ru.monica.github.design

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.drawToBitmap
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import takagi.ru.monica.R
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.ui.components.MarkdownPreviewText
import takagi.ru.monica.ui.theme.EtoileTheme
import java.util.concurrent.atomic.AtomicInteger

@OptIn(coil3.annotation.DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeMarkdownImagesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var host: ActivityController<ComponentActivity>
    private lateinit var loader: ImageLoader
    @Before fun setup() { host = Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    @After fun teardown() { host.pause().stop().destroy(); loader.shutdown(); SingletonImageLoader.reset() }

    @Test fun slowImageShowsLoadingThenRendersDecodedPixels() {
        val gate = CompletableDeferred<Unit>()
        val pixels = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        install(Interceptor { chain -> gate.await(); SuccessResult(pixels.asImage(), chain.request) })
        render("![Chart](https://example.invalid/chart.png)")
        compose.waitUntil(10_000) { compose.onAllNodesWithText(label(R.string.markdown_image_loading)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(label(R.string.markdown_image_loading)).assertIsDisplayed()
        gate.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Chart").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Chart").assertIsDisplayed()
        val bitmap = compose.runOnIdle { host.get().window.decorView.drawToBitmap() }
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (bitmap.getPixel(x, y) == Color.MAGENTA) count++
        assertTrue("The renderer must draw decoded image pixels", count >= 400)
    }

    @Test fun imageFailureOffersRetryAndOnlyExplicitTapStartsAnotherRequest() {
        val calls = AtomicInteger()
        val pixels = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        install(Interceptor { chain ->
            if (calls.incrementAndGet() == 1) ErrorResult(null, chain.request, java.io.IOException("offline"))
            else SuccessResult(pixels.asImage(), chain.request)
        })
        render("![Chart](https://example.invalid/chart.png)")
        compose.waitUntil(10_000) { compose.onAllNodesWithText(label(R.string.markdown_image_retry)).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(1, calls.get()) }
        compose.onNodeWithText(label(R.string.markdown_image_retry)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Chart").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(2, calls.get()) }
    }

    @Test fun blockedImageSchemeIsExplainedWithoutLaunchingRequest() {
        val calls = AtomicInteger()
        install(Interceptor { chain -> calls.incrementAndGet(); ErrorResult(null, chain.request, java.io.IOException()) })
        render("![Private file](file:///private/example.png)")
        compose.waitUntil(10_000) { compose.onAllNodesWithText(label(R.string.markdown_image_unavailable)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Private file").onLast().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.markdown_image_retry)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls.get()) }
    }

    private fun install(interceptor: Interceptor) {
        loader = ImageLoader.Builder(host.get()).components { add(interceptor) }.build()
        SingletonImageLoader.setUnsafe(loader)
    }
    private fun render(markdown: String) {
        host.get().setContent { EtoileTheme(darkTheme = false, designStyle = DesignStyle.MATERIAL) {
            Surface(Modifier.fillMaxSize()) { MarkdownPreviewText(markdown) }
        } }
    }
    private fun label(id: Int) = host.get().getString(id)
}
