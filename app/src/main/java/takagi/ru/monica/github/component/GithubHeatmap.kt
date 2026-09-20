package takagi.ru.monica.github.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.github.domain.GithubContributionCalendar
import takagi.ru.monica.github.domain.GithubContributionDay
import java.time.format.TextStyle
import java.util.Locale

private val HeatmapCellSize: Dp = 12.dp
private val HeatmapCellSpacing = 3.dp

/**
 * GitHub 风格的贡献热力图：横向滚动的周列，每列 7 格(周日起)，
 * 顶部按月标注。颜色强度跟随贡献等级 0-4；点按格子显示当日贡献数。
 */
@Composable
fun GithubContributionHeatmap(
    calendar: GithubContributionCalendar,
    modifier: Modifier = Modifier
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val monthsByWeek = remember(calendar, locale) { calendar.monthLabelsByWeek(locale) }
    val monthStarts = remember(monthsByWeek) {
        monthsByWeek.mapIndexedNotNull { index, label -> label?.let { index to it } }
    }
    val cellSize = HeatmapCellSize * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val weekWidth = cellSize + HeatmapCellSpacing
    val scrollState = rememberScrollState()
    var selectedDay by remember(calendar) { mutableStateOf<GithubContributionDay?>(null) }
    Column(modifier = modifier) {
        // A single scroll container keeps month spans aligned with the week grid.
        // Month names get the width of a month, rather than one 12dp day cell.
        Column(Modifier.horizontalScroll(scrollState)) {
            Row {
                monthStarts.firstOrNull()?.let { Spacer(Modifier.width(weekWidth * it.first)) }
                monthStarts.forEachIndexed { index, (weekIndex, label) ->
                    val nextWeek = monthStarts.getOrNull(index + 1)?.first ?: calendar.weeks.size
                    val weeksInLabel = (nextWeek - weekIndex).coerceAtLeast(if (index == monthStarts.lastIndex) 3 else 1)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(weekWidth * weeksInLabel)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(HeatmapCellSpacing)
            ) {
                calendar.weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(HeatmapCellSpacing)) {
                        repeat(7) { weekdayIndex ->
                            val day = week.days.firstOrNull {
                                it.parsedDate()?.dayOfWeek?.value == weekdayIndexOf(weekdayIndex)
                            }
                            if (day == null) {
                                Box(modifier = Modifier.size(cellSize))
                            } else {
                                val isSelected = selectedDay == day
                                val description = stringResource(R.string.github_heatmap_day_info, day.date, day.count)
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .background(
                                            color = githubContributionLevelColor(day.level),
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .semantics {
                                            contentDescription = description
                                            selected = isSelected
                                        }
                                        .clickable(role = Role.Button) { selectedDay = if (isSelected) null else day }
                                )
                            }
                        }
                    }
                }
            }
        }
        selectedDay?.let { day ->
            Text(
                text = stringResource(
                    R.string.github_heatmap_day_info,
                    day.date,
                    day.count
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun GithubContributionDay.parsedDate(): java.time.LocalDate? =
    runCatching { java.time.LocalDate.parse(date) }.getOrNull()

/**
 * 每一周列对应的月份简称：当该列包含某月第一天时记录该月，
 * 其余列为 null(不绘制标注，避免挤压)。
 */
private fun GithubContributionCalendar.monthLabelsByWeek(locale: Locale): List<String?> =
    weeks.map { week ->
        val firstOfMonth = week.days
            .mapNotNull { day -> day.parsedDate()?.let { it to day } }
            .filter { it.first.dayOfMonth <= 7 }
            .minByOrNull { it.first.dayOfMonth }
            ?.takeIf { it.first.dayOfMonth == 1 }
        firstOfMonth?.first?.month?.getDisplayName(TextStyle.SHORT, locale)
    }

/** 贡献等级 0-4 → 主题色阶；空档用轮廓色。 */
@Composable
fun githubContributionLevelColor(level: Int) = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceContainerHigh
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    else -> MaterialTheme.colorScheme.primary
}

/** UI 周索引(0 = 周日) → java.time 的 DayOfWeek 值(周日 = 7)。 */
private fun weekdayIndexOf(uiIndex: Int): Int = if (uiIndex == 0) 7 else uiIndex
