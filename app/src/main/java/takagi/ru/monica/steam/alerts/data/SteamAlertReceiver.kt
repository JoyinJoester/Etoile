package takagi.ru.monica.steam.alerts.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.network.SteamAuthorizedDeviceService
import takagi.ru.monica.steam.network.SteamConfirmationService
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.alerts.domain.*
import takagi.ru.monica.steam.notifications.data.SteamNotificationService
import takagi.ru.monica.steam.store.data.SteamStoreCache
import takagi.ru.monica.steam.store.data.SteamStoreService
import takagi.ru.monica.steam.store.domain.SteamWishlistSnapshot

class SteamAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                checkAlerts(appContext)
            } catch (error: Throwable) {
                // BroadcastReceiver work runs outside the activity/ViewModel
                // scope.  A transient Room, Keystore, or network failure must
                // not become an uncaught process-level exception.
                SteamDiagLogger.append(
                    "alert_check failed type=${error::class.java.simpleName}"
                )
                android.util.Log.e(
                    "EtoileAlert",
                    "Steam alert check failed",
                    error
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun checkAlerts(context: Context) {
        val preferences = SteamAlertPreferences(context)
        val settings = preferences.settings.first()
        if (!settings.enabled) return

        val sourceRepository = SteamAccountSourceRepository.get(context)
        val sessionSnapshot = SteamAlertAccountSessionProvider(
            loadHandles = { sourceRepository.loadAllSessionHandles() },
            resolve = { handle -> sourceRepository.sessionManager.resolve(handle) }
        ).load(refreshSessions = settings.sessionEnabled)
        val usableAccounts = sessionSnapshot.usableAccounts
        val sessionIssues = sessionSnapshot.sessionIssues
        val confirmationService = SteamConfirmationService()
        val deviceService = SteamAuthorizedDeviceService()
        val notificationService = SteamNotificationService()

        var unreadNotifications = 0
        if (settings.notificationsEnabled) {
            usableAccounts.filter { it.hasRealSteamId && !it.accessToken.isNullOrBlank() }
                .forEach { account ->
                    runCatching { notificationService.fetch(account) }
                        .onSuccess { snapshot ->
                            unreadNotifications += maxOf(
                                snapshot.unreadCount,
                                snapshot.pendingGiftCount +
                                    snapshot.pendingFriendCount +
                                    snapshot.pendingFamilyInviteCount
                            )
                        }
                }
        }

        var pendingConfirmations = 0
        if (settings.confirmationsEnabled) {
            usableAccounts.filter {
                it.canUseConfirmations && !it.accessToken.isNullOrBlank()
            }.forEach { account ->
                pendingConfirmations += runCatching {
                    confirmationService.fetch(account).size
                }.getOrDefault(0)
            }
        }

        var deviceCount = 0
        var deviceChecksSucceeded = true
        if (settings.devicesEnabled) {
            usableAccounts.filter { it.hasRealSteamId && !it.accessToken.isNullOrBlank() }
                .forEach { account ->
                    runCatching { deviceService.fetch(account).size }
                        .onSuccess { deviceCount += it }
                        .onFailure { deviceChecksSucceeded = false }
                }
        }

        val wishlistDiscountNames = mutableListOf<String>()
        if (settings.wishlistDiscountsEnabled) {
            val store = SteamStoreService()
            val cache = SteamStoreCache(context)
            usableAccounts.filter { it.hasRealSteamId && !it.accessToken.isNullOrBlank() }
                .forEach { account ->
                    runCatching {
                        val previous = cache.readWishlist(account.id)
                        val current = store.wishlist(
                            steamId = account.steamId,
                            steamLoginSecure = account.steamLoginSecure,
                            accessToken = account.accessToken
                        )
                        val discounts = SteamWishlistDiscountPolicy.newlyDiscounted(
                            previous = previous?.items,
                            current = current
                        )
                        cache.writeWishlist(account.id, SteamWishlistSnapshot(current))
                        discounts.mapTo(wishlistDiscountNames) { it.name }
                    }
                }
        }

        val decision = SteamAlertPolicy.evaluate(
            settings = settings,
            observation = SteamAlertObservation(
                unreadNotifications = unreadNotifications,
                pendingConfirmations = pendingConfirmations,
                sessionIssues = sessionIssues,
                authorizedDeviceCount = deviceCount.takeIf {
                    settings.devicesEnabled && deviceChecksSucceeded
                },
                wishlistDiscountNames = wishlistDiscountNames.distinct()
            )
        )
        val now = System.currentTimeMillis()
        val shouldNotify = SteamAlertPolicy.shouldNotify(settings, decision, now)
        if (shouldNotify) {
            SteamAlertNotifier.show(context, decision)
        }
        preferences.recordDecision(decision, now.takeIf { shouldNotify })
    }
}
