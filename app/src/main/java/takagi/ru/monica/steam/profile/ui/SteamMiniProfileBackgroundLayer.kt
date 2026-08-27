package takagi.ru.monica.steam.profile.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.AttributeSet
import android.util.LruCache
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.profile.SteamMiniProfileBackgroundRepository
import takagi.ru.monica.steam.profile.SteamMiniProfilePreparedMedia

@Composable
internal fun SteamMiniProfileBackgroundLayer(
    steamId: String,
    enabled: Boolean,
    allowMotion: Boolean,
    onAvailabilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = LocalContext.current
    val repository = remember(context) {
        SteamMiniProfileBackgroundRepository.get(context.applicationContext)
    }
    val media by produceState<SteamMiniProfilePreparedMedia?>(
        initialValue = null,
        key1 = steamId,
        key2 = enabled
    ) {
        value = if (!enabled) {
            null
        } else {
            try {
                repository.load(steamId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
    }
    val poster by produceState<Bitmap?>(
        initialValue = null,
        key1 = media?.posterFile?.absolutePath
    ) {
        value = try {
            media?.posterFile?.let { file ->
                withContext(Dispatchers.IO) { SteamMiniProfilePosterMemoryCache.load(file) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }
    val motionAllowed = rememberSteamMiniProfileMotionAllowed(allowMotion)
    val mediaAvailable = media?.videoFile?.isFile == true
    LaunchedEffect(mediaAvailable) {
        onAvailabilityChanged(mediaAvailable)
    }
    val hasPlaybackSlot = rememberSteamMiniProfilePlaybackSlot(
        requested = motionAllowed && mediaAvailable
    )

    Box(modifier = modifier.clearAndSetSemantics { }) {
        poster?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        val videoFile = media?.videoFile
        if (videoFile != null && hasPlaybackSlot) {
            SteamMiniProfileVideo(
                file = videoFile,
                play = motionAllowed,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun rememberSteamMiniProfileMotionAllowed(requested: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var powerSave by remember { mutableStateOf(powerManager.isPowerSaveMode) }

    DisposableEffect(lifecycleOwner, powerManager) {
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        runCatching {
            context.registerReceiver(
                powerReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            )
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { context.unregisterReceiver(powerReceiver) }
        }
    }
    return requested && resumed && !powerSave
}

@Composable
private fun rememberSteamMiniProfilePlaybackSlot(requested: Boolean): Boolean {
    val granted by produceState(initialValue = false, key1 = requested) {
        if (!requested) {
            value = false
            return@produceState
        }
        SteamMiniProfilePlaybackSlots.acquire()
        try {
            value = true
            awaitCancellation()
        } finally {
            value = false
            SteamMiniProfilePlaybackSlots.release()
        }
    }
    return granted
}

@Composable
private fun SteamMiniProfileVideo(
    file: File,
    play: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SteamMiniProfileTextureView(context).apply {
                isClickable = false
                isFocusable = false
            }
        },
        update = { view -> view.setMedia(file, play) },
        onRelease = SteamMiniProfileTextureView::release,
        modifier = modifier.clearAndSetSemantics { }
    )
}

private object SteamMiniProfilePlaybackSlots {
    private val semaphore = Semaphore(2)

    suspend fun acquire() = semaphore.acquire()
    fun release() = semaphore.release()
}

private object SteamMiniProfilePosterMemoryCache {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun load(file: File): Bitmap? {
        val key = file.absolutePath + ':' + file.lastModified()
        cache.get(key)?.let { return it }
        return BitmapFactory.decodeFile(file.absolutePath)?.also { cache.put(key, it) }
    }
}

private class SteamMiniProfileTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var mediaPath: String? = null
    private var playRequested: Boolean = false
    private var prepared: Boolean = false
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var playbackRetryCount: Int = 0
    private var retryRunnable: Runnable? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
        alpha = 0f
    }

    fun setMedia(file: File, play: Boolean) {
        playRequested = play
        val path = file.absolutePath
        if (mediaPath != path) {
            cancelPlaybackRetry()
            playbackRetryCount = 0
            mediaPath = path
            createPlayerIfPossible()
        } else if (player == null && surface != null) {
            cancelPlaybackRetry()
            createPlayerIfPossible()
        } else {
            updatePlayback()
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        cancelPlaybackRetry()
        surface?.release()
        surface = Surface(texture)
        createPlayerIfPossible()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        applyCenterCrop()
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        cancelPlaybackRetry()
        releasePlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        cancelPlaybackRetry()
        animate().cancel()
        releasePlayer()
        surface?.release()
        surface = null
        mediaPath = null
        playbackRetryCount = 0
        alpha = 0f
    }

    private fun createPlayerIfPossible() {
        val path = mediaPath ?: return
        val targetSurface = surface ?: return
        cancelPlaybackRetry()
        releasePlayer()
        animate().cancel()
        alpha = 0f
        prepared = false
        player = runCatching {
            MediaPlayer().apply {
                isLooping = true
                setVolume(0f, 0f)
                setSurface(targetSurface)
                setDataSource(path)
                setOnVideoSizeChangedListener { _, width, height ->
                    this@SteamMiniProfileTextureView.videoWidth = width
                    this@SteamMiniProfileTextureView.videoHeight = height
                    applyCenterCrop()
                }
                setOnPreparedListener {
                    prepared = true
                    updatePlayback()
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        playbackRetryCount = 0
                        animate().alpha(1f).setDuration(180L).start()
                    }
                    false
                }
                setOnErrorListener { _, what, extra ->
                    SteamDiagLogger.append(
                        "mini_profile_playback error file=${File(path).name} " +
                            "what=$what extra=$extra retry=$playbackRetryCount"
                    )
                    handlePlaybackError(path)
                    true
                }
                prepareAsync()
            }
        }.getOrElse { error ->
            SteamDiagLogger.append(
                "mini_profile_playback prepare_failed file=${File(path).name} " +
                    "type=${error.javaClass.simpleName} retry=$playbackRetryCount"
            )
            handlePlaybackError(path)
            null
        }
    }

    private fun handlePlaybackError(path: String) {
        animate().cancel()
        alpha = 0f
        releasePlayer()
        if (playRequested) schedulePlaybackRetry(path)
    }

    private fun schedulePlaybackRetry(path: String) {
        if (playbackRetryCount >= MAX_PLAYBACK_RETRIES || retryRunnable != null) return
        playbackRetryCount += 1
        retryRunnable = Runnable {
            retryRunnable = null
            if (mediaPath == path && playRequested && surface != null && player == null) {
                createPlayerIfPossible()
            }
        }.also { postDelayed(it, PLAYBACK_RETRY_DELAY_MILLIS) }
    }

    private fun cancelPlaybackRetry() {
        retryRunnable?.let(::removeCallbacks)
        retryRunnable = null
    }

    private fun updatePlayback() {
        val path = mediaPath ?: return
        val active = player ?: return
        if (!prepared) return
        runCatching {
            if (playRequested) {
                if (!active.isPlaying) active.start()
            } else if (active.isPlaying) {
                active.pause()
            }
        }.onFailure { error ->
            SteamDiagLogger.append(
                "mini_profile_playback update_failed file=${File(path).name} " +
                    "type=${error.javaClass.simpleName} retry=$playbackRetryCount"
            )
            handlePlaybackError(path)
        }
    }

    private fun applyCenterCrop() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val transform = calculateSteamMiniProfileCenterCrop(
            viewWidth = width,
            viewHeight = height,
            mediaWidth = videoWidth,
            mediaHeight = videoHeight,
        )
        val matrix = Matrix().apply {
            setScale(
                transform.scaleX,
                transform.scaleY,
                width / 2f,
                height / 2f,
            )
        }
        setTransform(matrix)
    }

    private fun releasePlayer() {
        prepared = false
        val current = player
        player = null
        current?.let {
            // stop() and reset() make synchronous media-service calls and can
            // stall the UI thread when several animated cards leave composition.
            runCatching { current.release() }
        }
    }

    private companion object {
        const val MAX_PLAYBACK_RETRIES = 2
        const val PLAYBACK_RETRY_DELAY_MILLIS = 650L
    }
}
