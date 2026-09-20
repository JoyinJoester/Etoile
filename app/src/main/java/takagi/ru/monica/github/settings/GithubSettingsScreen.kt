package takagi.ru.monica.github.settings

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.design.LocalDesignStyle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GithubSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsPage(
        title = stringResource(R.string.github_settings),
        subtitle = stringResource(R.string.github_settings_subtitle),
        onBack = onBack,
        modifier = modifier
    ) {
        Spacer(Modifier.height(12.dp))
        SettingsDestinationRow(
            title = stringResource(R.string.github_appearance),
            summary = listOfNotNull(
                designStyleLabel(settings.designStyle),
                themeLabel(settings.themeMode),
                paletteLabel(settings.colorScheme).takeUnless { settings.designStyle == DesignStyle.NOTHING }
            ).joinToString(" · "),
            icon = Icons.Default.Palette,
            onClick = onOpenAppearance
        )
        Spacer(Modifier.height(12.dp))
        SettingsDestinationRow(
            title = stringResource(R.string.github_language),
            summary = languageLabel(settings.language),
            icon = Icons.Default.Language,
            onClick = onOpenLanguage
        )
    }
}

@Composable
fun GithubAppearanceScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onPaletteSelected: (ColorScheme) -> Unit,
    onDesignStyleSelected: (DesignStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val miuixStyled = settings.designStyle == DesignStyle.MIUIX
    SettingsPage(
        title = stringResource(R.string.github_appearance),
        subtitle = stringResource(R.string.github_appearance_subtitle),
        onBack = onBack,
        modifier = modifier
    ) {
        if (settings.designStyle == DesignStyle.MATERIAL) {
            ExpressiveStylePreview()
        }
        GithubSectionHeader(stringResource(R.string.github_design_style))
        SettingsGroup(miuixStyled) {
            listOf(DesignStyle.NOTHING, DesignStyle.MATERIAL, DesignStyle.MIUIX).forEach { style ->
                ChoiceRow(miuixStyled, designStyleLabel(style), settings.designStyle == style) {
                    onDesignStyleSelected(style)
                }
            }
        }
        GithubSectionHeader(stringResource(R.string.github_theme))
        SettingsGroup(miuixStyled) {
            ThemeMode.entries.forEach { theme ->
                ChoiceRow(miuixStyled, themeLabel(theme), settings.themeMode == theme) {
                    onThemeSelected(theme)
                }
            }
        }
        // Nothing keeps its monochrome palette; the other styles allow color choices.
        if (settings.designStyle != DesignStyle.NOTHING) {
            GithubSectionHeader(stringResource(R.string.github_palette))
            SettingsGroup(miuixStyled) {
                PaletteRow(settings.colorScheme, onPaletteSelected)
            }
        }
    }
}

@Composable
fun GithubLanguageScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    val miuixStyled = settings.designStyle == DesignStyle.MIUIX
    SettingsPage(
        title = stringResource(R.string.github_language),
        onBack = onBack,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.github_language_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
        )
        SettingsGroup(miuixStyled) {
            Language.entries.forEach { language ->
                ChoiceRow(miuixStyled, languageLabel(language), settings.language == language) {
                    onLanguageSelected(language)
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GithubDetailScaffold(
        title = title,
        subtitle = subtitle,
        backContentDescription = stringResource(R.string.github_back),
        onBack = onBack,
        contentMaxWidth = GithubAdaptiveLayout.formMaxWidth,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsDestinationRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    if (LocalDesignStyle.current == DesignStyle.MIUIX) {
        Card(cornerRadius = 16.dp) {
            BasicComponent(
                title = title,
                summary = summary,
                onClick = onClick,
                endActions = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
            )
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = GithubExpressiveShapes.container,
            color = if (LocalDesignStyle.current == DesignStyle.NOTHING) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.heightIn(min = 80.dp).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExpressiveStylePreview() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = GithubExpressiveShapes.prominent,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.github_design_material), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.github_appearance_subtitle), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary
                ).forEach { color ->
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = GithubExpressiveShapes.compact,
                        color = color
                    ) {}
                }
            }
        }
    }
}

// Nothing uses spacing; the other styles retain their native containers.
@Composable
private fun SettingsGroup(miuixStyled: Boolean, content: @Composable () -> Unit) {
    if (LocalDesignStyle.current == DesignStyle.NOTHING) {
        Column(Modifier.fillMaxWidth().selectableGroup()) { content() }
    } else if (miuixStyled) {
        Card(cornerRadius = 16.dp) {
            Column(Modifier.selectableGroup()) { content() }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.selectableGroup()) { content() }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChoiceRow(miuixStyled: Boolean, label: String, selected: Boolean, onClick: () -> Unit) {
    if (miuixStyled) {
        BasicComponent(
            modifier = Modifier.semantics { this.selected = selected; role = Role.RadioButton },
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
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (LocalDesignStyle.current == DesignStyle.MATERIAL) {
                RadioButton(selected = selected, onClick = null)
            } else if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
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
    if (LocalDesignStyle.current == DesignStyle.MATERIAL) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp).selectableGroup()) {
            val columns = if (maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 340.dp) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                palettes.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (palette, color) ->
                            Row(
                                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp)
                                    .clip(GithubExpressiveShapes.control)
                                    .background(if (selected == palette) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .selectable(selected = selected == palette, role = Role.RadioButton) { onSelected(palette) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(modifier = Modifier.size(24.dp), shape = GithubExpressiveShapes.compact, color = color) {}
                                Text(paletteLabel(palette), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (selected == palette) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        palettes.forEach { (palette, color) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = selected == palette, role = Role.RadioButton) { onSelected(palette) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(modifier = Modifier.size(24.dp), shape = GithubExpressiveShapes.control, color = color) {}
                Text(paletteLabel(palette), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (selected == palette) Icon(Icons.Default.Check, contentDescription = null)
            }
        }
    }
}

@Composable
private fun paletteLabel(palette: ColorScheme): String = stringResource(when (palette) {
    ColorScheme.DEFAULT -> R.string.github_palette_default
    ColorScheme.OCEAN_BLUE -> R.string.github_palette_ocean
    ColorScheme.SUNSET_ORANGE -> R.string.github_palette_sunset
    ColorScheme.FOREST_GREEN -> R.string.github_palette_forest
    ColorScheme.TECH_PURPLE -> R.string.github_palette_purple
    ColorScheme.MIUI_BLUE -> R.string.github_palette_miui
    else -> R.string.github_design_nothing
})

@Composable
private fun designStyleLabel(style: DesignStyle): String = stringResource(when (style) {
    DesignStyle.NOTHING -> R.string.github_design_nothing
    DesignStyle.MATERIAL -> R.string.github_design_material
    DesignStyle.MIUIX -> R.string.github_design_miunix
})

@Composable
private fun themeLabel(theme: ThemeMode): String = stringResource(when (theme) {
    ThemeMode.SYSTEM -> R.string.github_system
    ThemeMode.LIGHT -> R.string.github_light
    ThemeMode.DARK -> R.string.github_dark
})

@Composable
private fun languageLabel(language: Language): String = when (language) {
    Language.SYSTEM -> stringResource(R.string.language_system)
    Language.CHINESE -> "中文"
    Language.ENGLISH -> "English"
    Language.JAPANESE -> "日本語"
    Language.RUSSIAN -> "Русский"
    Language.VIETNAMESE -> "Tiếng Việt"
}
