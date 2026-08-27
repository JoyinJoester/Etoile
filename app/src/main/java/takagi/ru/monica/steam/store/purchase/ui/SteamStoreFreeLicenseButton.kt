package takagi.ru.monica.steam.store.purchase.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimResult
import takagi.ru.monica.steam.store.freebie.domain.SteamFreebieClaimStatus

@Composable
internal fun SteamStoreFreeLicenseButton(
    alreadyOwned: Boolean,
    claiming: Boolean,
    result: SteamFreebieClaimResult?,
    onOpenOfficial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            alreadyOwned || result?.status == SteamFreebieClaimStatus.CLAIMED ||
                result?.status == SteamFreebieClaimStatus.ALREADY_OWNED -> {
                FilledTonalButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Text(
                        text = stringResource(R.string.steam_freebie_owned),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            claiming -> Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(
                        if (result?.status == SteamFreebieClaimStatus.PENDING_VERIFICATION) {
                            R.string.steam_freebie_checking
                        } else {
                            R.string.steam_freebie_claiming
                        }
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            result?.status == SteamFreebieClaimStatus.PENDING_VERIFICATION -> Button(
                onClick = onOpenOfficial,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Redeem, contentDescription = null)
                Text(
                    text = stringResource(R.string.steam_freebie_claim_on_steam),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            else -> Button(
                onClick = onOpenOfficial,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Redeem, contentDescription = null)
                Text(
                    text = stringResource(R.string.steam_freebie_claim_on_steam),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        result?.takeUnless {
            it.status == SteamFreebieClaimStatus.CLAIMED ||
                it.status == SteamFreebieClaimStatus.ALREADY_OWNED
        }?.let { claimResult ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (claimResult.status == SteamFreebieClaimStatus.PENDING_VERIFICATION) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (claimResult.status == SteamFreebieClaimStatus.PENDING_VERIFICATION) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = freeLicenseResultText(claimResult.status),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun freeLicenseResultText(status: SteamFreebieClaimStatus): String = when (status) {
    SteamFreebieClaimStatus.CLAIMED -> stringResource(R.string.steam_freebie_claimed)
    SteamFreebieClaimStatus.ALREADY_OWNED -> stringResource(R.string.steam_freebie_already_owned)
    SteamFreebieClaimStatus.PENDING_VERIFICATION ->
        stringResource(R.string.steam_freebie_pending_verification)
    SteamFreebieClaimStatus.SESSION_REQUIRED ->
        stringResource(R.string.steam_freebie_session_required)
    SteamFreebieClaimStatus.RATE_LIMITED -> stringResource(R.string.steam_freebie_rate_limited)
    SteamFreebieClaimStatus.REGION_RESTRICTED ->
        stringResource(R.string.steam_freebie_region_restricted)
    SteamFreebieClaimStatus.NEEDS_BASE_GAME ->
        stringResource(R.string.steam_freebie_needs_base_game)
    SteamFreebieClaimStatus.FAILED -> stringResource(R.string.steam_freebie_claim_failed)
}
