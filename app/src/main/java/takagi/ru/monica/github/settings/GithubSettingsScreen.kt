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
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.data.Language
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.github.component.GithubDetailScaffold
import takagi.ru.monica.github.component.GithubSectionHeader
import takagi.ru.monica.github.design.GithubExpressiveShapes
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GithubSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onPaletteSelected: (ColorScheme) -> Unit,
    onDesignStyleSelected: (DesignStyle) -> Unit,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    val miuixStyled = settings.designStyle == DesignStyle.MIUIX
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
            GithubSectionHeader(stringResource(R.string.github_design_style))
            SettingsGroup(miuixStyled) {
                ChoiceRow(miuixStyled, stringResource(R.string.github_design_material), settings.designStyle == DesignStyle.MATERIAL) { onDesignStyleSelected(DesignStyle.MATERIAL) }
                ChoiceRow(miuixStyled, stringResource(R.string.github_design_nothing), settings.designStyle == DesignStyle.NOTHING) { onDesignStyleSelected(DesignStyle.NOTHING) }
                ChoiceRow(miuixStyled, stringResource(R.string.github_design_miunix), settings.designStyle == DesignStyle.MIUIX) { onDesignStyleSelected(DesignStyle.MIUIX) }
            }
            GithubSectionHeader(stringResource(R.string.github_theme))
            SettingsGroup(miuixStyled) {
                ChoiceRow(miuixStyled, stringResource(R.string.github_system), settings.themeMode == ThemeMode.SYSTEM) { onThemeSelected(ThemeMode.SYSTEM) }
                ChoiceRow(miuixStyled, stringResource(R.string.github_light), settings.themeMode == ThemeMode.LIGHT) { onThemeSelected(ThemeMode.LIGHT) }
                ChoiceRow(miuixStyled, stringResource(R.string.github_dark), settings.themeMode == ThemeMode.DARK) { onThemeSelected(ThemeMode.DARK) }
            }
            // Nothing 设计锁定单色配色，不支持配色选择，直接隐藏
            if (settings.designStyle != DesignStyle.NOTHING) {
                GithubSectionHeader(stringResource(R.string.github_palette))
                SettingsGroup(miuixStyled) {
                    PaletteRow(settings.colorScheme, onPaletteSelected)
                }
            }
            GithubSectionHeader(stringResource(R.string.github_language))
            SettingsGroup(miuixStyled) {
                Language.entries.forEach { language ->
                    ChoiceRow(miuixStyled, languageLabel(language), settings.language == language) { onLanguageSelected(language) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// 分组容器：Miuix 设计用 miuix Card，其余用 M3 Surface
@Composable
private fun SettingsGroup(miuixStyled: Boolean, content: @Composable () -> Unit) {
    if (miuixStyled) {
        Card(cornerRadius = 16.dp) {
            content()
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column { content() }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChoiceRow(miuixStyled: Boolean, label: String, selected: Boolean, onClick: () -> Unit) {
    if (miuixStyled) {
        BasicComponent(
            title = label,
            onClick = onClick,
            endActions = {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PaletteRow(selected: ColorScheme, onSelected: (ColorScheme) -> Unit) {
    val palettes = listOf(
        ColorScheme.DEFAULT to MaterialTheme.colorScheme.primary,
        ColorScheme.OCEAN_BLUE to Color(0xFF1976D2),
        ColorScheme.SUNSET_ORANGE to Color(0xFFF06B3D),
        ColorScheme.FOREST_GREEN to Color(0xFF388E3C),
        ColorScheme.TECH_PURPLE to Color(0xFF7655C9),
        ColorScheme.MIUI_BLUE to Color(0xFF3482FF),
        ColorScheme.NOTHING to Color(0xFFD71921)
    )
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
