package takagi.ru.monica.github.component

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import takagi.ru.monica.R
import takagi.ru.monica.data.DesignStyle
import takagi.ru.monica.github.design.LocalDesignStyle
import takagi.ru.monica.github.design.GithubAdaptiveLayout
import takagi.ru.monica.github.design.GithubExpressiveMotion
import takagi.ru.monica.github.design.GithubExpressiveShapes
import takagi.ru.monica.github.design.GithubExpressiveSizes
import takagi.ru.monica.github.domain.GithubRepository
import takagi.ru.monica.github.domain.GithubIssueLabel

val LocalGithubUserNavigator = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun GithubUserLink(
    login: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.primary,
    avatarUrl: String? = null,
    maxLines: Int = 1
) {
    val navigate = LocalGithubUserNavigator.current
    Row(
        modifier = modifier.clickable { navigate(login) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarUrl != null) {
            GithubAvatar(login = login, avatarUrl = avatarUrl, size = 20.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = login,
            color = color,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GithubUserMetadataLine(
    prefix: String,
    login: String,
    suffix: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    avatarUrl: String? = null
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (prefix.isNotEmpty()) {
            Text(prefix.trim(), style = style, color = color)
        }
        GithubUserLink(login = login, style = style, avatarUrl = avatarUrl)
        if (suffix.isNotEmpty()) {
            Text(
                text = suffix.trim(),
                style = style,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GithubModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = GithubExpressiveShapes.prominent,
        content = content
    )
}

@Composable
fun GithubSheetHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GithubDetailScaffold(
    title: String,
    backContentDescription: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentMaxWidth: Dp = GithubAdaptiveLayout.contentMaxWidth,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val separateSubtitle = subtitle != null && LocalDensity.current.fontScale > 1.2f
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TopAppBar(
                    modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backContentDescription)
                        }
                    },
                    title = {
                        Column {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            subtitle?.takeUnless { separateSubtitle }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                if (separateSubtitle) {
                    Text(
                        text = subtitle.orEmpty(),
                        modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GithubServiceStatusNotices(
                    modifier = Modifier.widthIn(max = contentMaxWidth)
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = { padding ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = contentMaxWidth).fillMaxSize()) {
                    content(padding)
                }
            }
        }
    )
}

/** Toolbar refresh action sharing the accessible touch target of the other actions. */
@Composable
fun GithubRefreshButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .sizeIn(
                minWidth = GithubExpressiveSizes.minimumTouchTarget,
                minHeight = GithubExpressiveSizes.minimumTouchTarget
            )
            .then(modifier)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.github_web_refresh),
            modifier = Modifier.size(GithubExpressiveSizes.standardIcon)
        )
    }
}

@Composable
fun GithubOpenOnGithubButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(
                minWidth = GithubExpressiveSizes.minimumTouchTarget,
                minHeight = GithubExpressiveSizes.minimumTouchTarget
            )
            .then(modifier)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.github_open_on_github),
            modifier = Modifier.size(GithubExpressiveSizes.standardIcon)
        )
    }
}

@Composable
fun GithubSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
    actionEnabled: Boolean = true
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(top = if (compact) 16.dp else 24.dp, bottom = 8.dp)
    ) {
        val heading: @Composable (Modifier) -> Unit = { textModifier ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = textModifier.semantics { heading() }
            )
        }
        val actionContent: @Composable () -> Unit = {
            if (action != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    enabled = actionEnabled,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (action != null && (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                heading(Modifier.fillMaxWidth())
                Box(Modifier.align(Alignment.End)) { actionContent() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                heading(Modifier.weight(1f))
                actionContent()
            }
        }
    }
}

@Composable
fun GithubMetric(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = GithubExpressiveShapes.control,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            GithubMetricContent(value = value, label = label, accent = accent, compact = compact)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = GithubExpressiveShapes.control,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            GithubMetricContent(value = value, label = label, accent = accent, compact = compact)
        }
    }
}

@Composable
private fun GithubMetricContent(value: String, label: String, accent: Color, compact: Boolean) {
    Column(modifier = Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 10.dp else 12.dp)) {
        Text(
            value,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GithubFilterRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                label = { Text(label) },
                shape = GithubExpressiveShapes.control,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedIndex == index,
                    borderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

@Composable
fun GithubSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
    compact: Boolean = false
) {
    if (compact) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = trailingIcon,
            placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            shape = GithubExpressiveShapes.control,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = trailingIcon,
            label = { Text(label) },
            shape = GithubExpressiveShapes.control
        )
    }
}

@Composable
fun GithubMessageState(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
        description?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Full-width empty or error state for a list surface.
 *
 * [GithubMessageState] stays the right choice for short notes rendered inside a card or a row; this
 * one claims vertical space and centres a tonal icon, so it only suits a surface that has nothing
 * else to show.
 */
@Composable
fun GithubEmptyState(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    accent: Color = MaterialTheme.colorScheme.secondary,
    accentContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    if (LocalDesignStyle.current == DesignStyle.NOTHING) {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleLarge)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(accentContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(GithubExpressiveSizes.standardIcon)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                shape = GithubExpressiveShapes.control,
                modifier = Modifier.padding(top = 18.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun GithubCenteredProgress(
    modifier: Modifier = Modifier
) {
    if (LocalDesignStyle.current == DesignStyle.NOTHING) {
        GithubTechnicalLabel(
            text = "[${stringResource(R.string.github_loading)}]",
            modifier = modifier.fillMaxWidth().padding(vertical = 24.dp)
        )
        return
    }
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GithubRepositoryRow(
    repository: GithubRepository,
    descriptionFallback: String,
    languageFallback: String,
    updatedFallback: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = GithubExpressiveMotion.quickTween(),
        label = "repositoryPress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .scale(scale)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
                role = Role.Button
            )
            .padding(vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (repository.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = repository.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingContent != null) {
                trailingContent()
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
        Text(
            text = repository.description ?: descriptionFallback,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(
            modifier = Modifier.padding(start = 28.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(repository.language ?: languageFallback, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(formatStars(repository.stars), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(githubRelativeTimeOrElse(repository.updatedAt, updatedFallback), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
            }
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(top = 15.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
    }
}

@Composable
fun GithubPreferenceRow(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val material = LocalDesignStyle.current == DesignStyle.MATERIAL
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stacked = value.isNotBlank() &&
            ((maxWidth / LocalDensity.current.fontScale.coerceAtLeast(1f) < 280.dp && title.length + value.length > 12) || value.length > 18)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = if (material) 16.dp else 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(if (material) 32.dp else 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (stacked) {
                    Text(value, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!stacked && value.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 128.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun GithubMetadataRow(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueContent: @Composable RowScope.() -> Unit = {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
) {
    if (LocalDesignStyle.current == DesignStyle.NOTHING) {
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (maxWidth / fontScale < 300.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), content = valueContent)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.35f)
                    )
                    Row(modifier = Modifier.weight(0.65f), content = valueContent)
                }
            }
        }
        return
    }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        val stacked = maxWidth / fontScale < 320.dp
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    GithubExpressiveShapes.compact
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(19.dp)
                )
            }
            if (stacked) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), content = valueContent)
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.4f)
                )
                Row(
                    modifier = Modifier.weight(0.6f),
                    horizontalArrangement = Arrangement.End,
                    content = valueContent
                )
            }
        }
    }
}

@Composable
fun GithubLabelRow(
    labels: List<GithubIssueLabel>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        labels.forEach { label ->
            val background = githubLabelColor(label.color)
            val foreground = if (background.luminance() > 0.52f) Color.Black else Color.White
            Surface(shape = GithubExpressiveShapes.control, color = background) {
                Text(
                    text = label.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun githubLabelColor(value: String): Color = runCatching {
    Color(AndroidColor.parseColor("#${value.removePrefix("#")}"))
}.getOrDefault(Color(0xFF6E7781))

private fun formatStars(stars: Int): String = when {
    stars >= 1_000_000 -> "%.1fM".format(stars / 1_000_000f)
    stars >= 1_000 -> "%.1fk".format(stars / 1_000f)
    else -> stars.toString()
}
