package takagi.ru.monica.steam.alerts

import android.content.Context
import takagi.ru.monica.steam.alerts.data.SteamAlertScheduler

object SteamAlerts {
    suspend fun sync(context: Context) {
        SteamAlertScheduler.sync(context.applicationContext)
    }
}
