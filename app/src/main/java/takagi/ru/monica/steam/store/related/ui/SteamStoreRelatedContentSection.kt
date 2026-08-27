package takagi.ru.monica.steam.store.related.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreBaseGame
import takagi.ru.monica.steam.store.purchase.domain.SteamStoreDemo
import takagi.ru.monica.steam.store.related.domain.SteamStoreRelatedApp
import takagi.ru.monica.steam.store.ui.SteamStoreImage

@Composable
fun SteamStoreRelatedContentSection(
    fullGame: SteamStoreBaseGame?,
    demos: List<SteamStoreDemo>,
    relatedDlc: List<SteamStoreRelatedApp>,
    onOpenApp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (fullGame == null && demos.isEmpty() && relatedDlc.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (fullGame != null) {
                RelatedSectionTitle(R.string.steam_store_related_game)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RelatedAppButton(
                        label = fullGame.name.ifBlank {
                            stringResource(R.string.steam_store_base_game)
                        },
                        onClick = { onOpenApp(fullGame.appId) }
                    )
                }
            }
            if (demos.isNotEmpty()) {
                RelatedSectionTitle(R.string.steam_store_related_demo)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(demos.size, key = { demos[it].appId }) { index ->
                        val demo = demos[index]
                        RelatedAppButton(
                            label = demo.description.ifBlank {
                                stringResource(R.string.steam_store_demo_number, demo.appId)
                            },
                            demo = true,
                            onClick = { onOpenApp(demo.appId) }
                        )
                    }
                }
            }
            if (relatedDlc.isNotEmpty()) {
                RelatedSectionTitle(R.string.steam_store_related_dlc)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(relatedDlc.size, key = { relatedDlc[it].appId }) { index ->
                        RelatedDlcCard(
                            item = relatedDlc[index],
                            onClick = { onOpenApp(relatedDlc[index].appId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedSectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RelatedDlcCard(item: SteamStoreRelatedApp, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(240.dp).heightIn(min = 176.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        SteamStoreImage(
            url = item.headerImageUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f),
            contentScale = ContentScale.Crop,
            contentDescription = item.name
        )
        Text(
            text = item.name,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RelatedAppButton(
    label: String,
    demo: Boolean = false,
    onClick: () -> Unit
) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(
            imageVector = if (demo) Icons.Default.SportsEsports else Icons.Default.Inventory2,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
