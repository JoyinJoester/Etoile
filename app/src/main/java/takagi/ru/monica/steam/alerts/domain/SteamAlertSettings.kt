package takagi.ru.monica.steam.alerts.domain

data class SteamAlertSettings(
    val enabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val loginRequestsEnabled: Boolean = true,
    val confirmationsEnabled: Boolean = true,
    val sessionEnabled: Boolean = true,
    val devicesEnabled: Boolean = true,
    val wishlistDiscountsEnabled: Boolean = true,
    val intervalHours: Int = 12,
    val lastDeviceCount: Int? = null,
    val lastAlertSignature: String = "",
    val lastNotificationAt: Long = 0L
) {
    val normalizedIntervalHours: Int
        get() = intervalHours.takeIf { it in allowedIntervals } ?: 12

    companion object {
        val allowedIntervals = setOf(6, 12, 24)
    }
}

enum class SteamAlertKind {
    NOTIFICATIONS,
    CONFIRMATIONS,
    SESSION,
    DEVICES,
    WISHLIST_DISCOUNTS
}

data class SteamAlertObservation(
    val unreadNotifications: Int = 0,
    val pendingConfirmations: Int = 0,
    val sessionIssues: Int = 0,
    val authorizedDeviceCount: Int? = null,
    val wishlistDiscountNames: List<String> = emptyList()
)

data class SteamAlertDecision(
    val kinds: Set<SteamAlertKind>,
    val deviceBaseline: Int?,
    val wishlistDiscountNames: List<String> = emptyList()
) {
    val signature: String
        get() = (kinds.map(Enum<*>::name) + wishlistDiscountNames)
            .sorted()
            .joinToString(";")
}

object SteamAlertPolicy {
    const val REPEAT_SUPPRESSION_MS = 24L * 60L * 60L * 1000L
    fun evaluate(
        settings: SteamAlertSettings,
        observation: SteamAlertObservation
    ): SteamAlertDecision {
        if (!settings.enabled) return SteamAlertDecision(emptySet(), settings.lastDeviceCount)
        val kinds = buildSet {
            if (settings.notificationsEnabled && observation.unreadNotifications > 0) {
                add(SteamAlertKind.NOTIFICATIONS)
            }
            if (settings.confirmationsEnabled && observation.pendingConfirmations > 0) {
                add(SteamAlertKind.CONFIRMATIONS)
            }
            if (settings.sessionEnabled && observation.sessionIssues > 0) {
                add(SteamAlertKind.SESSION)
            }
            if (
                settings.devicesEnabled &&
                settings.lastDeviceCount != null &&
                observation.authorizedDeviceCount != null &&
                settings.lastDeviceCount != observation.authorizedDeviceCount
            ) {
                add(SteamAlertKind.DEVICES)
            }
            if (settings.wishlistDiscountsEnabled && observation.wishlistDiscountNames.isNotEmpty()) {
                add(SteamAlertKind.WISHLIST_DISCOUNTS)
            }
        }
        return SteamAlertDecision(
            kinds = kinds,
            deviceBaseline = observation.authorizedDeviceCount ?: settings.lastDeviceCount,
            wishlistDiscountNames = observation.wishlistDiscountNames
        )
    }

    fun shouldNotify(
        settings: SteamAlertSettings,
        decision: SteamAlertDecision,
        nowMillis: Long
    ): Boolean {
        if (decision.kinds.isEmpty()) return false
        if (decision.signature != settings.lastAlertSignature) return true
        return nowMillis - settings.lastNotificationAt >= REPEAT_SUPPRESSION_MS
    }
}
