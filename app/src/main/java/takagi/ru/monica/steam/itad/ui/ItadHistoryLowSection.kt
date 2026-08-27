package takagi.ru.monica.steam.itad.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.util.Currency
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.itad.data.ItadHistoryLowRepository
import takagi.ru.monica.steam.itad.domain.ItadHistoricalLow
import takagi.ru.monica.steam.itad.domain.ItadHistoryLowCompatibility
import takagi.ru.monica.steam.itad.domain.ItadHistoryLowFailureKind
import takagi.ru.monica.steam.itad.domain.ItadHistoryLowLoadResult
import takagi.ru.monica.steam.itad.domain.ItadMoney
import takagi.ru.monica.steam.itad.domain.resolveItadHistoryLowCompatibility

@Composable
fun ItadHistoryLowSection(
    appId: Int,
    countryCode: String?,
    expectedCurrency: String?,
    currentSteamPriceMinor: Long?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        ItadHistoryLowRepository(context.applicationContext)
    }
    var reloadToken by remember(appId, countryCode) { mutableIntStateOf(0) }
    val result by produceState<ItadHistoryLowLoadResult?>(
        initialValue = null,
        key1 = appId,
        key2 = countryCode,
        key3 = reloadToken
    ) {
        value = null
        value = repository.load(
            appId = appId,
            countryCode = countryCode,
            force = reloadToken > 0
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.itad_history_low_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.itad_history_low_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (val current = result) {
            null -> ItadHistoryLowLoading()
            is ItadHistoryLowLoadResult.Success -> when (
                resolveItadHistoryLowCompatibility(
                    historicalLow = current.historicalLow,
                    expectedCurrency = expectedCurrency,
                    currentSteamPriceMinor = currentSteamPriceMinor
                )
            ) {
                ItadHistoryLowCompatibility.COMPATIBLE -> ItadHistoryLowSuccess(
                    result = current,
                    onOpenSource = {
                        openOfficialItadUrl(context, current.historicalLow.sourceUrl)
                    }
                )
                ItadHistoryLowCompatibility.CURRENCY_MISMATCH -> ItadHistoryLowMismatch(
                    message = stringResource(R.string.itad_history_low_region_mismatch),
                    onRetry = { reloadToken++ }
                )
                ItadHistoryLowCompatibility.CURRENT_STEAM_PRICE_IS_LOWER ->
                    ItadHistoryLowMismatch(
                        message = stringResource(
                            R.string.itad_history_low_current_price_is_lower
                        ),
                        onRetry = { reloadToken++ }
                    )
            }
            is ItadHistoryLowLoadResult.Failure -> ItadHistoryLowFailure(
                failure = current,
                onOpenSettings = onOpenSettings,
                onRetry = { reloadToken++ }
            )
        }
    }
}

@Composable
private fun ItadHistoryLowLoading() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(
            text = stringResource(R.string.itad_history_low_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItadHistoryLowSuccess(
    result: ItadHistoryLowLoadResult.Success,
    onOpenSource: () -> Unit
) {
    val low = result.historicalLow
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = formatItadMoney(low.price),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (low.regular.amountInt != low.price.amountInt) {
            Text(
                text = formatItadMoney(low.regular),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ItadInfoPill(
            text = low.shopName,
            icon = Icons.Default.PriceCheck
        )
        if (low.discountPercent > 0) {
            ItadInfoPill(text = "-${low.discountPercent}%")
        }
        if (result.fromCache) {
            ItadInfoPill(
                text = stringResource(
                    if (result.stale) {
                        R.string.itad_history_low_stale_cache
                    } else {
                        R.string.itad_history_low_cached
                    }
                ),
                icon = Icons.Default.Cached
            )
        }
    }
    Text(
        text = stringResource(
            R.string.itad_history_low_date,
            formatItadTimestamp(low.timestamp)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TextButton(onClick = onOpenSource) {
        Icon(Icons.Default.OpenInNew, contentDescription = null)
        Text(
            text = stringResource(R.string.itad_history_low_source),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ItadHistoryLowFailure(
    failure: ItadHistoryLowLoadResult.Failure,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit
) {
    val requiresSettings = failure.kind == ItadHistoryLowFailureKind.API_KEY_MISSING ||
        failure.kind == ItadHistoryLowFailureKind.CREDENTIAL_STORAGE ||
        failure.kind == ItadHistoryLowFailureKind.UNAUTHORIZED
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (requiresSettings) Icons.Default.Key else Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(
                    when (failure.kind) {
                        ItadHistoryLowFailureKind.API_KEY_MISSING ->
                            R.string.itad_history_low_key_missing
                        ItadHistoryLowFailureKind.CREDENTIAL_STORAGE ->
                            R.string.itad_history_low_credential_error
                        ItadHistoryLowFailureKind.UNAUTHORIZED ->
                            R.string.itad_history_low_unauthorized
                        ItadHistoryLowFailureKind.GAME_NOT_MAPPED ->
                            R.string.itad_history_low_not_found
                        ItadHistoryLowFailureKind.RATE_LIMITED ->
                            R.string.itad_history_low_rate_limited
                        ItadHistoryLowFailureKind.NETWORK ->
                            R.string.itad_history_low_network_error
                        ItadHistoryLowFailureKind.SERVICE,
                        ItadHistoryLowFailureKind.INVALID_RESPONSE ->
                            R.string.itad_history_low_service_error
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (failure.kind == ItadHistoryLowFailureKind.RATE_LIMITED &&
                failure.retryAfterEpochMillis != null
            ) {
                Text(
                    text = stringResource(
                        R.string.itad_history_low_retry_after,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(
                            java.util.Date(failure.retryAfterEpochMillis)
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (requiresSettings) {
        FilledTonalButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.itad_history_low_open_settings))
        }
    } else if (failure.kind != ItadHistoryLowFailureKind.GAME_NOT_MAPPED) {
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.itad_history_low_retry))
        }
    }
}

@Composable
private fun ItadHistoryLowMismatch(
    message: String,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    FilledTonalButton(onClick = onRetry) {
        Text(stringResource(R.string.itad_history_low_retry))
    }
}

@Composable
private fun ItadInfoPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

internal fun formatItadMoney(money: ItadMoney, locale: Locale = Locale.getDefault()): String {
    val currency = runCatching { Currency.getInstance(money.currency) }.getOrNull()
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        val digits = currency?.defaultFractionDigits?.coerceIn(0, 4) ?: 2
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    return "${money.currency} ${formatter.format(money.amount)}"
}

private fun formatItadTimestamp(rawTimestamp: String): String = runCatching {
    val instant = OffsetDateTime.parse(rawTimestamp).toInstant()
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(
        java.util.Date.from(instant)
    )
}.getOrDefault(rawTimestamp)

private fun openOfficialItadUrl(context: Context, rawUrl: String) {
    val uri = Uri.parse(rawUrl)
    val host = uri.host.orEmpty().lowercase(Locale.ROOT)
    if (uri.scheme != "https" ||
        (host != "isthereanydeal.com" && !host.endsWith(".isthereanydeal.com"))
    ) return
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(intent) }.isFailure) {
        android.widget.Toast.makeText(
            context,
            R.string.itad_open_link_failed,
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
