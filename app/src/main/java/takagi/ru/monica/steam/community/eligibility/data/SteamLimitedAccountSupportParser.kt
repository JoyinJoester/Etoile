package takagi.ru.monica.steam.community.eligibility.data

import kotlin.math.roundToInt
import org.jsoup.Jsoup
import takagi.ru.monica.steam.community.eligibility.domain.DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS
import takagi.ru.monica.steam.community.eligibility.domain.SteamLimitedAccountSupportProgress

internal object SteamLimitedAccountSupportParser {
    fun parse(html: String): SteamLimitedAccountSupportProgress? {
        if (html.isBlank()) return null
        val document = Jsoup.parse(html)
        val text = normalizeText(document.body()?.text().orEmpty())
        if (text.isBlank() || isLoginPage(document.location(), html, text)) return null

        val explicitlyLimited = document.allElements.any { element ->
            normalizeText(element.text()).matches(EXPLICIT_LIMITED_STATEMENT)
        }
        val explicitlyUnrestricted = document.allElements.any { element ->
            normalizeText(element.text()).matches(EXPLICIT_UNRESTRICTED_STATEMENT)
        }
        val spendRatio = findSpendRatio(text)
        val spent = spendRatio?.first ?: findAmount(SPENT_AMOUNT, text)
        val threshold = spendRatio?.second ?: findAmount(THRESHOLD_AMOUNT, text)
            ?: if (spent != null) DEFAULT_STEAM_UNLOCK_THRESHOLD_USD_CENTS else null
        val explicitRemaining = findAmount(REMAINING_AMOUNT, text)
        val remaining = when {
            explicitRemaining != null -> explicitRemaining
            spent != null && threshold != null -> (threshold - spent).coerceAtLeast(0)
            explicitlyUnrestricted -> 0
            else -> null
        }
        val limited = when {
            spent != null && threshold != null -> spent < threshold
            explicitRemaining != null -> explicitRemaining > 0
            explicitlyLimited -> true
            explicitlyUnrestricted -> false
            else -> null
        }
        if (limited == null && spent == null && remaining == null) return null
        return SteamLimitedAccountSupportProgress(
            limited = limited,
            spentUsdCents = spent,
            thresholdUsdCents = threshold,
            remainingUsdCents = remaining
        )
    }

    private fun findAmount(pattern: Regex, text: String): Int? = pattern.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::amountToCents)

    private fun findSpendRatio(text: String): Pair<Int, Int>? {
        val match = SPEND_RATIO.find(text) ?: return null
        val spent = match.groupValues.getOrNull(1)?.let(::amountToCents) ?: return null
        val threshold = match.groupValues.getOrNull(2)?.let(::amountToCents) ?: return null
        return spent to threshold
    }

    private fun amountToCents(value: String): Int? = value
        .replace(",", "")
        .toDoubleOrNull()
        ?.times(100.0)
        ?.roundToInt()

    private fun normalizeText(value: String): String = value
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isLoginPage(location: String, html: String, text: String): Boolean {
        val normalizedHtml = html.lowercase()
        return location.contains("/login", ignoreCase = true) ||
            "need_password=1" in normalizedHtml ||
            ("sign in to steam support" in text.lowercase() && "action=\"/login" in normalizedHtml)
    }

    private val SPENT_AMOUNT = Regex(
        "(?i)(?:your account\\s+has\\s+spent|you(?:'ve| have)\\s+spent|amount\\s+spent|" +
            "(?:your\\s+)?total\\s+spend(?:ing)?\\s*(?:is|:|equals)|" +
            "qualifying\\s+spend\\s*(?:is|:))" +
            "[^$]{0,80}(?:US)?\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val SPEND_RATIO = Regex(
        "(?i)(?:US\\s*)?\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)" +
            "\\s*/\\s*(?:US\\s*)?\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)" +
            "\\s*USD"
    )
    private val THRESHOLD_AMOUNT = Regex(
        "(?i)(?:out\\s+of(?:\\s+the)?|minimum|required|at\\s+least)" +
            "[^$]{0,80}(?:US)?\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val REMAINING_AMOUNT = Regex(
        "(?i)(?:remaining|left\\s+to\\s+spend|still\\s+need(?:s)?\\s+to\\s+spend|" +
            "need(?:s)?\\s+to\\s+spend\\s+an?\\s+additional|" +
            "must\\s+spend\\s+(?:another|an?\\s+additional)|spend\\s+another)" +
            "[^$]{0,80}(?:US)?\\$\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    )
    private val EXPLICIT_LIMITED_STATEMENT = Regex(
        "(?i)^your(?: steam)? account is (?:currently )?" +
            "(?:(?:a )?limited(?: user)? account|limited)[.!]?$"
    )
    private val EXPLICIT_UNRESTRICTED_STATEMENT = Regex(
        "(?i)^your(?: steam)? account is " +
            "(?:(?:currently )?not limited|not currently limited)[.!]?$"
    )
}
