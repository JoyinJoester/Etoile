package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable

/**
 * Draws an animated Steam asset at its native pixel size before scaling it to
 * the Compose slot. APNG4Android's drawable uses a filtering draw pass of its
 * own; that pass makes 150px stickers visibly soft on a high-density screen.
 * Keeping the intermediate frame at native size and using a nearest-neighbour
 * paint for the final pass preserves the original sticker edges and still
 * forwards every frame invalidation from the decoder.
 */
internal class SteamPixelAnimatedDrawable(
    private val source: Drawable,
    private val sourceWidth: Int,
    private val sourceHeight: Int
) : Drawable(), Animatable, Drawable.Callback {
    private val sourceAnimation = source as? Animatable
    private val pixelPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }
    private val sourceBounds = Rect(0, 0, sourceWidth, sourceHeight)
    private var frameBitmap: Bitmap? = null
    private var frameCanvas: Canvas? = null

    init {
        source.callback = this
        source.setBounds(sourceBounds)
    }

    override fun draw(canvas: Canvas) {
        val target = bounds
        if (target.isEmpty || sourceWidth <= 0 || sourceHeight <= 0) return

        val bitmap = obtainFrameBitmap()
        val offscreen = frameCanvas ?: return
        bitmap.eraseColor(Color.TRANSPARENT)
        // APNGDrawable may update its bounds when it changes sample size. Keep
        // it at the exact source dimensions so its own draw never upscales a
        // frame before our nearest-neighbour pass.
        if (source.bounds != sourceBounds) source.setBounds(sourceBounds)
        source.draw(offscreen)
        canvas.drawBitmap(bitmap, null, target, pixelPaint)
    }

    override fun setAlpha(alpha: Int) {
        pixelPaint.alpha = alpha
        source.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        pixelPaint.colorFilter = colorFilter
        source.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = sourceWidth

    override fun getIntrinsicHeight(): Int = sourceHeight

    override fun start() {
        sourceAnimation?.start()
    }

    override fun stop() {
        sourceAnimation?.stop()
    }

    override fun isRunning(): Boolean = sourceAnimation?.isRunning == true

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        source.setVisible(visible, restart)
        if (visible) {
            if (restart && sourceAnimation?.isRunning == true) sourceAnimation.stop()
            if (sourceAnimation?.isRunning != true) sourceAnimation?.start()
        } else {
            sourceAnimation?.stop()
        }
        return changed
    }

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    /** Releases the decoder callback and the intermediate frame when a row is
     * removed from a Lazy list. */
    fun release() {
        sourceAnimation?.stop()
        source.callback = null
        frameCanvas?.setBitmap(null)
        frameCanvas = null
        frameBitmap?.takeIf { !it.isRecycled }?.recycle()
        frameBitmap = null
    }

    private fun obtainFrameBitmap(): Bitmap {
        frameBitmap?.takeIf { !it.isRecycled }?.let { return it }
        return Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888).also {
            frameBitmap = it
            frameCanvas = Canvas(it).apply {
                drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
        }
    }
}
