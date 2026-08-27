package takagi.ru.monica.ui.screens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class SettingsNavigationEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val searchTexts: List<String> = emptyList(),
    val onClick: () -> Unit
)

@Immutable
data class SettingsNavigationSection(
    val title: String,
    val entries: List<SettingsNavigationEntry>
)
