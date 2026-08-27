package takagi.ru.monica.steam.web.data

import android.webkit.CookieManager
import java.util.concurrent.atomic.AtomicInteger
import takagi.ru.monica.steam.web.domain.SteamWebCookieWrite

/**
 * WebView exposes one process-wide cookie jar. Cookie replacement is serialized
 * and latest-wins so a slow account switch cannot reinstall an older session.
 */
private object SteamWebCookieSessionCoordinator {
    private data class Request(
        val generation: Long,
        val manager: CookieManager,
        val writes: List<SteamWebCookieWrite>,
        val onCookiesReady: () -> Unit,
    )

    private val lock = Any()
    private var active = false
    private var pending: Request? = null
    private var latestGeneration = 0L

    fun replace(
        manager: CookieManager,
        writes: List<SteamWebCookieWrite>,
        onCookiesReady: () -> Unit,
    ) {
        val next = synchronized(lock) {
            latestGeneration += 1
            pending = Request(latestGeneration, manager, writes, onCookiesReady)
            if (active) {
                null
            } else {
                active = true
                pending.also { pending = null }
            }
        }
        next?.let(::execute)
    }

    private fun execute(request: Request) {
        request.manager.removeAllCookies {
            request.manager.flush()
            request.manager.installSteamCookies(request.writes) {
                complete(request)
            }
        }
    }

    private fun complete(request: Request) {
        var notifyReady = false
        val next = synchronized(lock) {
            val queued = pending
            pending = null
            if (queued == null) {
                active = false
                notifyReady = true
            }
            queued
        }
        val stillLatest = notifyReady && synchronized(lock) {
            request.generation == latestGeneration && !active && pending == null
        }
        if (stillLatest) request.onCookiesReady()
        next?.let(::execute)
    }
}

internal fun CookieManager.replaceSteamCookies(
    writes: List<SteamWebCookieWrite>,
    onCookiesReady: () -> Unit,
) {
    SteamWebCookieSessionCoordinator.replace(this, writes, onCookiesReady)
}

internal fun CookieManager.clearSteamCookies(onCookiesCleared: () -> Unit = {}) {
    SteamWebCookieSessionCoordinator.replace(this, emptyList(), onCookiesCleared)
}

private fun CookieManager.installSteamCookies(
    writes: List<SteamWebCookieWrite>,
    onCookiesReady: () -> Unit
) {
    if (writes.isEmpty()) {
        flush()
        onCookiesReady()
        return
    }
    val remaining = AtomicInteger(writes.size)
    writes.forEach { write ->
        setCookie(write.url, write.value) {
            if (remaining.decrementAndGet() == 0) {
                flush()
                onCookiesReady()
            }
        }
    }
}
