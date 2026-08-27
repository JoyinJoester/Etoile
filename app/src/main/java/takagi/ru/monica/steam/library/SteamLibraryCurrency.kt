package takagi.ru.monica.steam.library

import java.util.Currency
import java.util.Locale

private const val DEFAULT_STEAM_LIBRARY_COUNTRY_CODE = "US"
private const val DEFAULT_STEAM_LIBRARY_CURRENCY = "USD"

private val STEAM_USD_PRICING_COUNTRIES = setOf(
    "AR", "TR",
    "PK", "BD", "BT", "NP", "LK",
    "BO", "EC", "GY", "PY", "SR", "VE",
    "DZ", "BH", "EG", "IQ", "JO", "LB", "LY", "MA", "OM", "PS", "SD", "TN", "YE"
)

internal fun resolveSteamLibraryCountryCode(
    accountCountry: String?,
    cachedCountry: String?,
    deviceCountry: String?
): String = sequenceOf(accountCountry, cachedCountry, deviceCountry)
    .mapNotNull(::normalizeSteamLibraryCountryCode)
    .firstOrNull()
    ?: DEFAULT_STEAM_LIBRARY_COUNTRY_CODE

internal fun steamCurrencyForCountry(countryCode: String): String {
    val country = normalizeSteamLibraryCountryCode(countryCode)
        ?: return DEFAULT_STEAM_LIBRARY_CURRENCY
    if (country in STEAM_USD_PRICING_COUNTRIES) return DEFAULT_STEAM_LIBRARY_CURRENCY
    return runCatching {
        Currency.getInstance(Locale("", country)).currencyCode
    }.getOrDefault(DEFAULT_STEAM_LIBRARY_CURRENCY)
}

internal fun resolvedSteamLibraryCurrency(snapshot: SteamLibrarySnapshot?): String {
    if (snapshot == null) return DEFAULT_STEAM_LIBRARY_CURRENCY
    return snapshot.currency.trim().takeIf(String::isNotBlank)
        ?.uppercase(Locale.ROOT)
        ?: snapshot.games.firstNotNullOfOrNull { game ->
            game.price?.currency?.trim()?.takeIf(String::isNotBlank)
        }?.uppercase(Locale.ROOT)
        ?: steamCurrencyForCountry(snapshot.region)
}

private fun normalizeSteamLibraryCountryCode(countryCode: String?): String? = countryCode
    ?.trim()
    ?.uppercase(Locale.ROOT)
    ?.takeIf { it.length == 2 && it.all(Char::isLetter) }
