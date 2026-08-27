package takagi.ru.monica.github.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.design.GithubExpressiveSizes
import takagi.ru.monica.github.domain.GithubListSort
import takagi.ru.monica.github.domain.GithubSortDirection

@Composable
fun GithubListSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    clearContentDescription: String,
    orderingContentDescription: String,
    onOpenOrdering: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GithubSearchField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.weight(1f),
            trailingIcon = if (value.isNotEmpty()) {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = clearContentDescription)
                    }
                }
            } else {
                null
            }
        )
        FilledTonalIconButton(
            onClick = onOpenOrdering,
            modifier = Modifier.sizeIn(
                minWidth = GithubExpressiveSizes.minimumTouchTarget,
                minHeight = GithubExpressiveSizes.minimumTouchTarget
            )
        ) {
            Icon(Icons.Default.Tune, contentDescription = orderingContentDescription)
        }
    }
}

@Composable
fun GithubListOrderingSheet(
    title: String,
    subtitle: String,
    sort: GithubListSort,
    direction: GithubSortDirection,
    onSelectOrdering: (GithubListSort, GithubSortDirection) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    filterContent: @Composable ColumnScope.() -> Unit = {}
) {
    GithubModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            GithubSheetHeader(title = title, subtitle = subtitle)
            filterContent()
            Text(
                text = stringResource(R.string.github_list_order),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
            )
            GithubOrderingOption(
                label = stringResource(R.string.github_sort_recently_updated),
                selected = sort == GithubListSort.UPDATED && direction == GithubSortDirection.DESC,
                onClick = { onSelectOrdering(GithubListSort.UPDATED, GithubSortDirection.DESC) }
            )
            GithubOrderingOption(
                label = stringResource(R.string.github_sort_least_recently_updated),
                selected = sort == GithubListSort.UPDATED && direction == GithubSortDirection.ASC,
                onClick = { onSelectOrdering(GithubListSort.UPDATED, GithubSortDirection.ASC) }
            )
            GithubOrderingOption(
                label = stringResource(R.string.github_sort_newest_created),
                selected = sort == GithubListSort.CREATED && direction == GithubSortDirection.DESC,
                onClick = { onSelectOrdering(GithubListSort.CREATED, GithubSortDirection.DESC) }
            )
            GithubOrderingOption(
                label = stringResource(R.string.github_sort_oldest_created),
                selected = sort == GithubListSort.CREATED && direction == GithubSortDirection.ASC,
                onClick = { onSelectOrdering(GithubListSort.CREATED, GithubSortDirection.ASC) }
            )
        }
    }
}

@Composable
fun GithubListFilterSection(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        GithubFilterRow(
            labels = labels,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun GithubOrderingOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = GithubExpressiveMotion.quickTween(),
        label = "github-ordering-option"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = GithubExpressiveShapes.control,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
