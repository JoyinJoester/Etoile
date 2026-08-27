package takagi.ru.monica.steam.store.bundle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundle
import takagi.ru.monica.steam.store.bundle.domain.SteamStoreBundleItem
import takagi.ru.monica.steam.store.domain.formatSteamPrice
import takagi.ru.monica.steam.store.ui.SteamStoreImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamStoreBundleSection(
    bundles: List<SteamStoreBundle>,
    currency: String,
    onOpenApp: (Int) -> Unit,
    onOpenBundle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bundles.isEmpty()) return
    var selectedBundle by remember(bundles) { mutableStateOf<SteamStoreBundle?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.steam_store_bundles),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            contentPadding = PaddingValues(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(bundles, key = SteamStoreBundle::bundleId) { bundle ->
                BundleCard(
                    bundle = bundle,
                    currency = currency,
                    onClick = { selectedBundle = bundle }
                )
            }
        }
    }

    selectedBundle?.let { bundle ->
        ModalBottomSheet(
            onDismissRequest = { selectedBundle = null },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            tonalElevation = 0.dp
        ) {
            BundleDetail(
                bundle = bundle,
                currency = currency,
                onOpenBundle = { onOpenBundle(bundle.storeUrl) },
                onOpenApp = { appId ->
                    selectedBundle = null
                    onOpenApp(appId)
                }
            )
        }
    }
}

@Composable
private fun BundleCard(
    bundle: SteamStoreBundle,
    currency: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(276.dp).heightIn(min = 190.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        if (bundle.imageUrl.isNotBlank()) {
            SteamStoreImage(
                url = bundle.imageUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f),
                contentScale = ContentScale.Crop,
                contentDescription = bundle.title
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = bundle.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.steam_store_bundle_items, bundle.items.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BundlePrice(bundle, currency)
            }
        }
    }
}

@Composable
private fun BundlePrice(bundle: SteamStoreBundle, currency: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bundle.discountPercent > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = stringResource(
                        R.string.steam_store_bundle_discount,
                        bundle.discountPercent
                    ),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = formatSteamPrice(bundle.finalPriceCents, currency),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BundleDetail(
    bundle: SteamStoreBundle,
    currency: String,
    onOpenBundle: () -> Unit,
    onOpenApp: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = bundle.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.steam_store_bundle_contents,
                            bundle.items.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BundlePrice(bundle, currency)
                }
                Button(
                    onClick = onOpenBundle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.steam_store_bundle_buy))
                }
            }
        }
        items(bundle.items, key = SteamStoreBundleItem::appId) { item ->
            Surface(
                onClick = { onOpenApp(item.appId) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SteamStoreImage(
                        url = item.imageUrl,
                        modifier = Modifier.width(112.dp).height(52.dp),
                        contentDescription = item.name
                    )
                    Text(
                        text = item.name.ifBlank { "App ${item.appId}" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.steam_store_bundle_open_item)
                    )
                }
            }
        }
    }
}
