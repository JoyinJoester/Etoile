package takagi.ru.monica.steam.library.analytics.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage
import takagi.ru.monica.steam.library.SteamGame
import takagi.ru.monica.steam.library.analytics.domain.SteamGameDistributionBucket
import takagi.ru.monica.steam.library.analytics.domain.SteamGameDistributionMode
import takagi.ru.monica.steam.library.analytics.domain.SteamGameDistributionRange
import takagi.ru.monica.ui.components.MonicaModalBottomSheet
import takagi.ru.monica.ui.theme.GoogleSansFlexFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamGameDistributionDetailSheet(
    bucket: SteamGameDistributionBucket,
    mode: SteamGameDistributionMode,
    currency: String,
    onOpenGame: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val rangeLabel = distributionRangeLabel(bucket.range, currency)
    val modeLabel = stringResource(
        if (mode == SteamGameDistributionMode.PLAYTIME) {
            R.string.steam_analytics_playtime
        } else {
            R.string.steam_analytics_price
        }
    )
    val openStoreLabel = stringResource(R.string.steam_library_open_store)
    val games = remember(bucket.games, mode) {
        when (mode) {
            SteamGameDistributionMode.PLAYTIME -> bucket.games.sortedWith(
                compareByDescending<SteamGame>(SteamGame::playtimeForeverMinutes)
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            SteamGameDistributionMode.PRICE -> bucket.games.sortedWith(
                compareByDescending<SteamGame> {
                    it.price?.takeIf { price -> price.isAvailable }?.originalPriceMinor
                        ?: Long.MIN_VALUE
                }.thenBy { it.name.lowercase(Locale.ROOT) }
            )
        }
    }
    val sheetHeightFraction = when {
        games.size <= 2 -> 0.48f
        games.size <= 7 -> 0.66f
        else -> 0.82f
    }

    MonicaModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeightFraction)
                .heightIn(max = 720.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = GoogleSansFlexFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "$modeLabel · ${stringResource(
                        R.string.steam_library_games_short,
                        bucket.gameCount
                    )}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            if (games.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.steam_distribution_no_games),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(games, key = SteamGame::appId) { game ->
                        val metric = when (mode) {
                            SteamGameDistributionMode.PLAYTIME ->
                                formatDistributionPlaytime(game.playtimeForeverMinutes)
                            SteamGameDistributionMode.PRICE -> formatDistributionPrice(game)
                        }
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = game.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(
                                        R.string.steam_distribution_app_id,
                                        game.appId
                                    ),
                                    maxLines = 1
                                )
                            },
                            leadingContent = { SteamDistributionGameIcon(game) },
                            trailingContent = {
                                Text(
                                    text = metric,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = GoogleSansFlexFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 68.dp)
                                .clickable(
                                    onClickLabel = openStoreLabel,
                                    onClick = {
                                        onDismiss()
                                        onOpenGame(game.appId)
                                    }
                                ),
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun distributionRangeLabel(
    range: SteamGameDistributionRange,
    currency: String
): String {
    val label = stringResource(
        when (range) {
            SteamGameDistributionRange.UNPLAYED -> R.string.steam_distribution_unplayed
            SteamGameDistributionRange.UNDER_ONE_HOUR -> R.string.steam_distribution_under_1h
            SteamGameDistributionRange.ONE_TO_THREE_HOURS -> R.string.steam_distribution_1_3h
            SteamGameDistributionRange.THREE_TO_TEN_HOURS -> R.string.steam_distribution_3_10h
            SteamGameDistributionRange.TEN_TO_THIRTY_HOURS -> R.string.steam_distribution_10_30h
            SteamGameDistributionRange.THIRTY_TO_HUNDRED_HOURS ->
                R.string.steam_distribution_30_100h
            SteamGameDistributionRange.OVER_HUNDRED_HOURS ->
                R.string.steam_distribution_over_100h
            SteamGameDistributionRange.FREE -> R.string.steam_distribution_free
            SteamGameDistributionRange.PRICE_UNDER_25 ->
                R.string.steam_distribution_price_under_25
            SteamGameDistributionRange.PRICE_25_TO_50 ->
                R.string.steam_distribution_price_25_50
            SteamGameDistributionRange.PRICE_50_TO_100 ->
                R.string.steam_distribution_price_50_100
            SteamGameDistributionRange.PRICE_100_TO_200 ->
                R.string.steam_distribution_price_100_200
            SteamGameDistributionRange.PRICE_200_TO_400 ->
                R.string.steam_distribution_price_200_400
            SteamGameDistributionRange.PRICE_OVER_400 ->
                R.string.steam_distribution_price_over_400
            SteamGameDistributionRange.PRICE_UNKNOWN ->
                R.string.steam_distribution_price_unknown
        }
    )
    val isPricedRange = when (range) {
        SteamGameDistributionRange.PRICE_UNDER_25,
        SteamGameDistributionRange.PRICE_25_TO_50,
        SteamGameDistributionRange.PRICE_50_TO_100,
        SteamGameDistributionRange.PRICE_100_TO_200,
        SteamGameDistributionRange.PRICE_200_TO_400,
        SteamGameDistributionRange.PRICE_OVER_400 -> true
        else -> false
    }
    return if (isPricedRange && currency.isNotBlank()) {
        "${currency.uppercase(Locale.ROOT)} $label"
    } else {
        label
    }
}

@Composable
private fun formatDistributionPlaytime(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.steam_distribution_playtime_minutes, minutes)
        remainingMinutes == 0 -> stringResource(R.string.steam_distribution_playtime_hours, hours)
        else -> stringResource(
            R.string.steam_distribution_playtime_hours_minutes,
            hours,
            remainingMinutes
        )
    }
}

@Composable
private fun formatDistributionPrice(game: SteamGame): String {
    val price = game.price?.takeIf { it.isAvailable }
        ?: return stringResource(R.string.steam_library_price_unavailable)
    if (price.originalPriceMinor == 0L) return stringResource(R.string.steam_library_free)
    val minor = (price.cnyOriginalPriceMinor ?: price.originalPriceMinor.takeIf {
        price.currency.equals("CNY", ignoreCase = true)
    })
        ?: return stringResource(R.string.steam_library_price_unavailable)
    val amount = "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"
    return "CNY $amount"
}

@Composable
private fun SteamDistributionGameIcon(game: SteamGame) {
    val context = LocalContext.current
    val imageUrls = remember(game.appId, game.iconHash, game.headerImageUrl) {
        buildList {
            game.iconHash.takeIf(String::isNotBlank)?.let { iconHash ->
                add(
                    "https://media.steampowered.com/steamcommunity/public/images/apps/" +
                        "${game.appId}/$iconHash.jpg"
                )
            }
            game.headerImageUrl.takeIf(String::isNotBlank)?.let(::add)
            add(
                "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/" +
                    "${game.appId}/capsule_184x69.jpg"
            )
        }.distinct()
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageUrls) {
        value = imageUrls.firstNotNullOfOrNull { url -> loadSteamRemoteImage(context, url) }
    }
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap),
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
