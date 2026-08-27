package takagi.ru.monica.steam.token.loginchallenge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteImage

@Composable
internal fun SteamLoginCaptchaContent(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var reloadToken by remember(imageUrl) { mutableStateOf(0) }
    val imageState by produceState<SteamLoginCaptchaImageState>(
        initialValue = SteamLoginCaptchaImageState.Loading,
        key1 = imageUrl,
        key2 = reloadToken
    ) {
        value = if (imageUrl.isBlank()) {
            SteamLoginCaptchaImageState.Failed
        } else {
            loadSteamRemoteImage(context, imageUrl)
                ?.let(SteamLoginCaptchaImageState::Loaded)
                ?: SteamLoginCaptchaImageState.Failed
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_login_captcha_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = imageState) {
                        SteamLoginCaptchaImageState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }

                        SteamLoginCaptchaImageState.Failed -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.steam_login_captcha_load_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(onClick = { reloadToken += 1 }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Text(
                                        text = stringResource(R.string.refresh),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        is SteamLoginCaptchaImageState.Loaded -> {
                            Image(
                                bitmap = state.bitmap,
                                contentDescription = stringResource(
                                    R.string.steam_login_captcha_image_description
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface SteamLoginCaptchaImageState {
    object Loading : SteamLoginCaptchaImageState
    object Failed : SteamLoginCaptchaImageState
    data class Loaded(val bitmap: ImageBitmap) : SteamLoginCaptchaImageState
}
