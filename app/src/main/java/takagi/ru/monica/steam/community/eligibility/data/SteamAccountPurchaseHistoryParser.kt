package takagi.ru.monica.steam.community.eligibility.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityTransaction
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityTransactionKind
import takagi.ru.monica.steam.community.eligibility.domain.SteamCommunityPaymentSource

internal object SteamAccountPurchaseHistoryParser {
    fun parse(
        html: String,
        fallbackCurrencyCode: String
    ): List<SteamCommunityTransaction>? {
        if (html.isBlank()) return null
        val document = Jsoup.parse(html)
        val pageText = document.body()?.text().orEmpty()
        if (isLoginPage(html, pageText)) return null
        val rows = document.select("tr.wallet_table_row, .wallet_history_table tr.wallet_table_row")
        if (rows.isEmpty()) {
            return if (document.selectFirst(".wallet_history_table") != null) emptyList() else null
        }
        return rows.mapNotNull { row -> parseRow(row, fallbackCurrencyCode) }
    }

    private fun parseRow(
        row: Element,
        fallbackCurrencyCode: String
    ): SteamCommunityTransaction? {
        val typeText = row.selectFirst(".wht_type")?.text().orEmpty()
        val itemText = row.selectFirst(".wht_items")?.text().orEmpty()
        val normalized = "$typeText $itemText".lowercase()
        val kind = when {
            row.selectFirst(".wht_refunded, .wht_item_refunded, .wth_item_refunded") != null ||
                REFUND_WORDS.any(normalized::contains) -> SteamCommunityTransactionKind.REFUND
            MARKET_WORDS.any(normalized::contains) -> SteamCommunityTransactionKind.MARKET
            isWalletCredit(normalized) -> SteamCommunityTransactionKind.WALLET_CREDIT
            "gift" in normalized && "purchase" in normalized ->
                SteamCommunityTransactionKind.GIFT_PURCHASE
            "purchase" in normalized -> SteamCommunityTransactionKind.STORE_PURCHASE
            else -> SteamCommunityTransactionKind.OTHER
        }
        val amountText = when (kind) {
            SteamCommunityTransactionKind.WALLET_CREDIT ->
                row.selectFirst(".wht_wallet_change")?.text()
                    ?: row.selectFirst(".wht_total")?.text()
            else -> row.selectFirst(".wht_total")?.text()
                ?: row.selectFirst(".wht_wallet_change")?.text()
        }.orEmpty()
        val money = parseSteamHistoryMoney(amountText, fallbackCurrencyCode) ?: return null
        val paymentText = row.select(".wth_payment, .wht_payment")
            .joinToString(" ") { it.text() }
            .lowercase()
        return SteamCommunityTransaction(
            kind = kind,
            amountMinor = money.amountMinor,
            currencyCode = money.currencyCode,
            paymentSource = when {
                "steam wallet" in paymentText -> SteamCommunityPaymentSource.STEAM_WALLET
                paymentText.isNotBlank() -> SteamCommunityPaymentSource.EXTERNAL
                else -> SteamCommunityPaymentSource.UNKNOWN
            }
        )
    }

    private fun isWalletCredit(text: String): Boolean =
        ("wallet" in text && listOf("add", "credit", "redeem", "fund", "code").any(text::contains)) ||
            "funds added" in text

    private fun isLoginPage(html: String, text: String): Boolean {
        val normalizedHtml = html.lowercase()
        val normalizedText = text.lowercase()
        return "need_password=1" in normalizedHtml ||
            ("sign in to steam" in normalizedText && LOGIN_FORM.containsMatchIn(html))
    }

    private val REFUND_WORDS = listOf("refund", "refunded", "chargeback", "reversal")
    private val MARKET_WORDS = listOf("market transaction", "community market")
    private val LOGIN_FORM = Regex(
        "(?i)<form[^>]+action\\s*=\\s*['\"][^'\"]*/login"
    )
}

internal data class SteamHistoryMoney(
    val amountMinor: Int,
    val currencyCode: String
)

internal fun parseSteamHistoryMoney(
    text: String,
    fallbackCurrencyCode: String
): SteamHistoryMoney? {
    val raw = text.replace('\u00A0', ' ').trim()
    val number = Regex("[0-9][0-9., ]*").find(raw)?.value?.trim() ?: return null
    val normalizedNumber = normalizeSteamHistoryNumber(number) ?: return null
    val minor = normalizedNumber.toBigDecimalOrNull()
        ?.movePointRight(2)
        ?.toInt()
        ?: return null
    return SteamHistoryMoney(
        amountMinor = minor,
        currencyCode = steamHistoryCurrency(raw, fallbackCurrencyCode)
    )
}

private fun normalizeSteamHistoryNumber(value: String): String? {
    val compact = value.replace(" ", "")
    if (compact.isBlank()) return null
    val lastDot = compact.lastIndexOf('.')
    val lastComma = compact.lastIndexOf(',')
    return when {
        lastDot >= 0 && lastComma >= 0 && lastComma > lastDot ->
            compact.replace(".", "").replace(',', '.')
        lastDot >= 0 && lastComma >= 0 -> compact.replace(",", "")
        lastComma >= 0 && compact.length - lastComma - 1 in 1..2 -> compact.replace(',', '.')
        lastComma >= 0 -> compact.replace(",", "")
        else -> compact
    }
}

private fun steamHistoryCurrency(text: String, fallbackCurrencyCode: String): String {
    val fallback = fallbackCurrencyCode.trim().uppercase().ifBlank { "USD" }
    return when {
        "NT$" in text -> "TWD"
        "HK$" in text -> "HKD"
        "US$" in text -> "USD"
        "S$" in text -> "SGD"
        "A$" in text -> "AUD"
        "NZ$" in text -> "NZD"
        "C$" in text || "CDN$" in text -> "CAD"
        "Mex$" in text -> "MXN"
        "CLP$" in text -> "CLP"
        "COL$" in text -> "COP"
        "R$" in text -> "BRL"
        "€" in text -> "EUR"
        "£" in text -> "GBP"
        "₽" in text -> "RUB"
        "₴" in text -> "UAH"
        "₹" in text -> "INR"
        "₩" in text -> "KRW"
        "฿" in text -> "THB"
        "₫" in text -> "VND"
        "Rp" in text -> "IDR"
        "RM" in text -> "MYR"
        "₱" in text -> "PHP"
        "zł" in text -> "PLN"
        "¥" in text -> if (fallback == "JPY") "JPY" else "CNY"
        "$" in text -> fallback
        else -> fallback
    }
}
