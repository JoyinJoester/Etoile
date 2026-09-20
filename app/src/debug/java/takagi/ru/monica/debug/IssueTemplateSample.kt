package takagi.ru.monica.debug

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import takagi.ru.monica.github.data.GithubIssueTemplateParser
import takagi.ru.monica.github.domain.GithubIssue
import takagi.ru.monica.github.domain.GithubIssueContactLink
import takagi.ru.monica.github.domain.GithubIssueDraft
import takagi.ru.monica.github.domain.GithubIssueState
import takagi.ru.monica.github.domain.GithubIssueTemplate
import takagi.ru.monica.github.domain.GithubIssueTemplateCatalog
import takagi.ru.monica.github.domain.GithubIssueTemplateLanguage
import takagi.ru.monica.github.domain.GithubIssueTemplatesRepository
import takagi.ru.monica.github.domain.GithubIssuesRepository
import takagi.ru.monica.github.domain.GithubUserSummary
import takagi.ru.monica.github.feature.issues.CreateIssueScreen
import takagi.ru.monica.github.feature.issues.CreateIssueViewModel
import takagi.ru.monica.ui.components.MarkdownPreviewText
import java.lang.reflect.Proxy
import java.util.Locale

/** The real composer and parser backed by local repositories; no remote issue can be posted. */
@Composable
internal fun IssueTemplateSample(sample: String, localeTag: String, onBack: () -> Unit, onReport: (String) -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(configuration, localeTag) { Configuration(configuration).apply { setLocale(Locale.forLanguageTag(localeTag)) } }
    val localizedContext = remember(context, localized) { context.createConfigurationContext(localized) }
    val catalog = remember {
        GithubIssueTemplateCatalog(
            templates = if (sample == "issue-templates-empty") emptyList() else listOf(
                GithubIssueTemplateParser.parse(".github/ISSUE_TEMPLATE/bug.yml", BUG_FORM),
                GithubIssueTemplateParser.parse(".github/ISSUE_TEMPLATE/feature.md", FEATURE_TEMPLATE),
                GithubIssueTemplate(".github/ISSUE_TEMPLATE/upload.yml", "Upload diagnostic files", isSupported = false)
            ),
            blankIssuesEnabled = sample != "issue-templates-required",
            contactLinks = listOf(GithubIssueContactLink("Ask the community", "https://github.com/sample/android-client/discussions", "Get help or discuss an idea."))
        )
    }
    val templates = remember {
        var attempts = 0
        GithubIssueTemplatesRepository { _, _ ->
            delay(100)
            if (sample == "issue-templates-load-error" && attempts++ == 0) Result.failure(IllegalStateException("Local offline simulation"))
            else Result.success(catalog)
        }
    }
    val issues = remember {
        val unused = Proxy.newProxyInstance(GithubIssuesRepository::class.java.classLoader, arrayOf(GithubIssuesRepository::class.java)) {
            _, method, _ -> error("Unexpected fixture call: ${method.name}")
        } as GithubIssuesRepository
        var attempts = 0
        object : GithubIssuesRepository by unused {
            override suspend fun createIssue(owner: String, name: String, draft: GithubIssueDraft): Result<GithubIssue> {
                delay(300)
                if (sample == "issue-templates-submit-error" && attempts++ == 0) return Result.failure(IllegalStateException("Local write failure"))
                return Result.success(GithubIssue(
                    id = 77L, number = 77, title = draft.title, body = draft.body, state = GithubIssueState.OPEN,
                    author = GithubUserSummary("sample", null, "https://github.com/sample"),
                    labels = emptyList(), assignees = emptyList(), comments = 0, isLocked = false,
                    createdAt = "2026-09-17T00:00:00Z", updatedAt = "2026-09-17T00:00:00Z", closedAt = null,
                    htmlUrl = "https://github.com/sample/android-client/issues/77"
                ))
            }
        }
    }
    val templateLanguage = if (localeTag == "zh") GithubIssueTemplateLanguage.CHINESE else GithubIssueTemplateLanguage.ENGLISH
    val model: CreateIssueViewModel = viewModel(factory = CreateIssueViewModel.Factory("sample", "android-client", issues, templates, templateLanguage))
    val state by model.state.collectAsState()
    var created by remember { mutableStateOf<GithubIssue?>(null) }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides localized) {
        if (created != null) {
            Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Local issue #77", style = MaterialTheme.typography.headlineSmall)
                Text(created!!.title, style = MaterialTheme.typography.titleLarge)
                MarkdownPreviewText(created!!.body.orEmpty(), renderImages = false)
            }
        } else CreateIssueScreen(
            state = state, canSubmit = true, onAction = model::onAction, onBack = onBack,
            onCreated = { created = it; onReport("Created local issue #77") }, onSignIn = {}, onOpenExternal = onReport
        )
    }
}

private val BUG_FORM = """
name: Bug report
description: Tell us what went wrong and help us reproduce it.
title: "[Bug] "
labels: [bug, needs-triage]
body:
  - type: markdown
    attributes:
      value: Please search existing issues before submitting a report.
  - type: input
    id: version
    attributes:
      label: Version
      value: 0.1.0
      placeholder: e.g. 0.1.0
    validations:
      required: true
  - type: textarea
    id: steps
    attributes:
      label: What happened?
      description: Include the steps to reproduce and what you expected.
      placeholder: Describe the problem…
    validations:
      required: true
  - type: dropdown
    id: device
    attributes:
      label: Android version
      options: [Android 15, Android 14, Android 13, Other]
    validations:
      required: true
  - type: dropdown
    id: areas
    attributes:
      label: Affected areas
      multiple: true
      options: [Issues, Repository files, Actions]
  - type: checkboxes
    id: checks
    attributes:
      label: Before submitting
      options:
        - label: I have searched for existing issues.
          required: true
        - label: I can help test a fix.
""".trimIndent()

private val FEATURE_TEMPLATE = """
---
name: Feature request
about: Suggest an improvement to the app.
title: '[Feature] '
labels: enhancement
---
## Problem

Describe the problem you want to solve.

## Proposed solution

Describe your idea.
""".trimIndent()
