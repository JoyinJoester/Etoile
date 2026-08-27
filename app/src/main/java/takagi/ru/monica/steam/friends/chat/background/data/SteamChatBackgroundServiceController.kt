package takagi.ru.monica.steam.friends.chat.background.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

object SteamChatBackgroundServiceController {
    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        val enabled = SteamChatBackgroundPreferences(appContext).settings.first().enabled
        if (enabled) start(appContext) else stop(appContext)
    }

    fun start(context: Context): Boolean = try {
        val appContext = context.applicationContext
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, SteamChatBackgroundService::class.java)
        )
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        SteamDiagLogger.append(
            "chat_background_start failed type=${error::class.java.simpleName}"
        )
        false
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, SteamChatBackgroundService::class.java))
    }
}
