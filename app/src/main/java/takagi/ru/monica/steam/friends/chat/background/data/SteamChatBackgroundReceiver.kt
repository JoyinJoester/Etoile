package takagi.ru.monica.steam.friends.chat.background.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

class SteamChatBackgroundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SteamChatBackgroundServiceController.sync(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SteamDiagLogger.append(
                    "chat_background_receiver failed type=${error::class.java.simpleName}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
