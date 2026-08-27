package takagi.ru.monica.github.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ColorScheme
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubExpressiveShapes

@Composable
fun GithubSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onPaletteSelected: (ColorScheme) -> Unit,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    GithubDetailScaffold(
        title = stringResource(R.string.github_settings),
        subtitle = stringResource(R.string.github_settings_subtitle),
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            GithubSectionHeader(stringResource(R.string.github_theme))
            ChoiceRow(stringResource(R.string.github_system), settings.themeMode == ThemeMode.SYSTEM) { onThemeSelected(ThemeMode.SYSTEM) }
            ChoiceRow(stringResource(R.string.github_light), settings.themeMode == ThemeMode.LIGHT) { onThemeSelected(ThemeMode.LIGHT) }
            ChoiceRow(stringResource(R.string.github_dark), settings.themeMode == ThemeMode.DARK) { onThemeSelected(ThemeMode.DARK) }
            GithubSectionHeader(stringResource(R.string.github_palette))
            PaletteRow(settings.colorScheme, onPaletteSelected)
            GithubSectionHeader(stringResource(R.string.github_language))
            Language.entries.forEach { language ->
                ChoiceRow(languageLabel(language), settings.language == language) { onLanguageSelected(language) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PaletteRow(selected: ColorScheme, onSelected: (ColorScheme) -> Unit) {
    val palettes = listOf(
        ColorScheme.DEFAULT to MaterialTheme.colorScheme.primary,
        ColorScheme.OCEAN_BLUE to Color(0xFF1976D2),
        ColorScheme.SUNSET_ORANGE to Color(0xFFF06B3D),
        ColorScheme.FOREST_GREEN to Color(0xFF388E3C),
        ColorScheme.TECH_PURPLE to Color(0xFF7655C9)
    )
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        palettes.forEach { (palette, color) ->
            Surface(
                modifier = Modifier.size(52.dp).clickable { onSelected(palette) },
                shape = GithubExpressiveShapes.control,
                color = color,
                tonalElevation = if (selected == palette) 6.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected == palette) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun languageLabel(language: Language): String = when (language) {
    Language.SYSTEM -> stringResource(R.string.language_system)
    Language.CHINESE -> "中文"
    Language.ENGLISH -> "English"
    Language.JAPANESE -> "日本語"
    Language.RUSSIAN -> "Русский"
    Language.VIETNAMESE -> "Tiếng Việt"
}
