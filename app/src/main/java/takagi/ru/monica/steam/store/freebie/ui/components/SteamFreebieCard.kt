package takagi.ru.monica.steam.store.freebie.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimMethod
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimResult
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimStatus
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieItem
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieOfferKind
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieProductType
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreOwnershipStatus
import takagi.ru.monica.steam.store.ui.SteamStoreImage

@Composable
internal fun SteamFreebieCard(
    item: SteamFreebieItem,
    claiming: Boolean,
    verifying: Boolean,
    claimResult: SteamFreebieClaimResult?,
    onOpenDetail: () -> Unit,
    onOpenOfficial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpenDetail,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            SteamStoreImage(
                url = item.imageUrl,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(460f / 215f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                contentDescription = item.name
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SteamFreebieLabel(
                        text = stringResource(
                            if (item.offerKind == SteamFreebieOfferKind.KEEP_FOREVER) {
                                R.string.steam_freebie_kind_keep
                            } else {
                                R.string.steam_freebie_kind_weekend
                            }
                        ),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (item.productType == SteamFreebieProductType.DLC) {
                        SteamFreebieLabel(
                            text = stringResource(R.string.steam_freebie_product_dlc),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                FreebiePriceRow(item)
                Text(
                    text = freebieExpiryText(item.endsAtEpochMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = freebieStatusText(item, claimResult),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = freebieStatusColor(claimResult),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            SteamFreebieAction(
                item = item,
                claiming = claiming,
                verifying = verifying,
                claimResult = claimResult,
                onOpenOfficial = onOpenOfficial
            )
        }
    }
}

@Composable
internal fun SteamFreebieLoadingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 136.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun FreebiePriceRow(item: SteamFreebieItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.originalPriceText.isNotBlank()) {
            Text(
                text = item.originalPriceText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1
            )
        }
        Text(
            text = item.finalPriceText.takeIf(String::isNotBlank)
                ?: stringResource(R.string.steam_freebie_free_price),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun SteamFreebieAction(
    item: SteamFreebieItem,
    claiming: Boolean,
    verifying: Boolean,
    claimResult: SteamFreebieClaimResult?,
    onOpenOfficial: () -> Unit
) {
    when {
        item.isOwned -> FilledTonalButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.steam_freebie_owned), maxLines = 1)
        }
        claiming -> Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.steam_freebie_claiming), maxLines = 1)
        }
        verifying -> Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.steam_freebie_checking), maxLines = 1)
        }
        claimResult?.status == SteamFreebieClaimStatus.PENDING_VERIFICATION -> Button(
            onClick = onOpenOfficial,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.steam_freebie_claim_on_steam), maxLines = 1)
        }
        item.isPermanentlyClaimable && !item.needsBaseGame -> Button(
            onClick = onOpenOfficial,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.steam_freebie_claim_on_steam), maxLines = 1)
        }
        else -> FilledTonalButton(
            onClick = onOpenOfficial,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.steam_freebie_open_store), maxLines = 1)
        }
    }
}

@Composable
private fun SteamFreebieLabel(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun freebieStatusText(
    item: SteamFreebieItem,
    claimResult: SteamFreebieClaimResult?
): String = when (claimResult?.status) {
    SteamFreebieClaimStatus.CLAIMED -> stringResource(R.string.steam_freebie_claimed)
    SteamFreebieClaimStatus.ALREADY_OWNED -> stringResource(R.string.steam_freebie_already_owned)
    SteamFreebieClaimStatus.PENDING_VERIFICATION ->
        stringResource(R.string.steam_freebie_pending_verification)
    SteamFreebieClaimStatus.SESSION_REQUIRED -> stringResource(R.string.steam_freebie_session_required)
    SteamFreebieClaimStatus.RATE_LIMITED -> stringResource(R.string.steam_freebie_rate_limited)
    SteamFreebieClaimStatus.REGION_RESTRICTED ->
        stringResource(R.string.steam_freebie_region_restricted)
    SteamFreebieClaimStatus.NEEDS_BASE_GAME -> stringResource(R.string.steam_freebie_needs_base_game)
    SteamFreebieClaimStatus.FAILED -> stringResource(R.string.steam_freebie_claim_failed)
    null -> when {
        item.isOwned -> stringResource(R.string.steam_freebie_already_owned)
        item.needsBaseGame -> stringResource(R.string.steam_freebie_needs_base_game)
        item.ownership == SteamStoreOwnershipStatus.FAMILY_SHARED ->
            stringResource(R.string.steam_freebie_family_shared)
        item.offerKind == SteamFreebieOfferKind.FREE_WEEKEND ->
            stringResource(R.string.steam_freebie_weekend_notice)
        item.claimMethod == SteamFreebieClaimMethod.OFFICIAL_CHECKOUT ->
            stringResource(R.string.steam_freebie_checkout_required)
        else -> stringResource(R.string.steam_freebie_official_source)
    }
}

@Composable
private fun freebieStatusColor(claimResult: SteamFreebieClaimResult?): Color = when (
    claimResult?.status
) {
    SteamFreebieClaimStatus.CLAIMED,
    SteamFreebieClaimStatus.ALREADY_OWNED -> MaterialTheme.colorScheme.primary
    SteamFreebieClaimStatus.PENDING_VERIFICATION -> MaterialTheme.colorScheme.tertiary
    SteamFreebieClaimStatus.SESSION_REQUIRED,
    SteamFreebieClaimStatus.RATE_LIMITED,
    SteamFreebieClaimStatus.REGION_RESTRICTED,
    SteamFreebieClaimStatus.NEEDS_BASE_GAME,
    SteamFreebieClaimStatus.FAILED -> MaterialTheme.colorScheme.error
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun freebieExpiryText(epochMillis: Long?): String {
    if (epochMillis == null) return stringResource(R.string.steam_freebie_expiry_unknown)
    val locale = Locale.getDefault()
    val formatted = remember(epochMillis, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date(epochMillis))
    }
    return stringResource(R.string.steam_freebie_ends_at, formatted)
}
