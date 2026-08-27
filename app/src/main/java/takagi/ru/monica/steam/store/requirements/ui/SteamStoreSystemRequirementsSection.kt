package takagi.ru.monica.steam.store.requirements.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.requirements.domain.SteamStoreSystemRequirements

private enum class RequirementLevel { MINIMUM, RECOMMENDED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamStoreSystemRequirementsSection(
    requirements: SteamStoreSystemRequirements,
    modifier: Modifier = Modifier
) {
    if (!requirements.hasContent) return

    val levels = buildList {
        if (requirements.minimum.isNotBlank()) add(RequirementLevel.MINIMUM)
        if (requirements.recommended.isNotBlank()) add(RequirementLevel.RECOMMENDED)
    }
    var selectedLevel by rememberSaveable(requirements.minimum, requirements.recommended) {
        mutableStateOf(
            if (requirements.recommended.isNotBlank()) {
                RequirementLevel.RECOMMENDED
            } else {
                RequirementLevel.MINIMUM
            }
        )
    }
    val content = when (selectedLevel) {
        RequirementLevel.MINIMUM -> requirements.minimum
        RequirementLevel.RECOMMENDED -> requirements.recommended
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_store_system_requirements),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (levels.size > 1) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    levels.forEachIndexed { index, level ->
                        SegmentedButton(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            shape = SegmentedButtonDefaults.itemShape(index, levels.size),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(
                                stringResource(
                                    if (level == RequirementLevel.MINIMUM) {
                                        R.string.steam_store_minimum_requirements
                                    } else {
                                        R.string.steam_store_recommended_requirements
                                    }
                                )
                            )
                        }
                    }
                }
            }
            SelectionContainer {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
