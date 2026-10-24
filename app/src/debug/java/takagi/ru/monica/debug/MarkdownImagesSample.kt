package takagi.ru.monica.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.github.component.GithubContributionHeatmap
import takagi.ru.monica.github.domain.*
import takagi.ru.monica.ui.components.MarkdownImageContext
import takagi.ru.monica.ui.components.MarkdownPreviewText
import java.time.LocalDate

@Composable
internal fun MarkdownImagesSample() {
    val calendar = remember {
        GithubContributionCalendar((0..52).map { week ->
            GithubContributionWeek((0..6).map { day ->
                GithubContributionDay(LocalDate.of(2025, 9, 28).plusDays((week * 7 + day).toLong()).toString(), (week + day) % 5, (week + day) % 5)
            })
        }, 730)
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Contribution calendar · horizontal scroll")
        GithubContributionHeatmap(calendar, Modifier.padding(vertical = 12.dp))
        MarkdownPreviewText(
            markdown = """
                ## Relative PNG
                ![Android](./android.png)

                ## SVG badge
                ![Build](https://img.shields.io/badge/build-passing-brightgreen)

                ## HTML image
                <p align="center"><img src="./android.png" alt="HTML Android"></p>

                ## Code stays literal
                `<img src="not-loaded.png">`
            """.trimIndent(),
            imageContext = MarkdownImageContext("github/explore", "main", "topics/android/README.md")
        )
    }
}
