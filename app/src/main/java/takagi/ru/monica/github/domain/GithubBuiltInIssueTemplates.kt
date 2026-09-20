package takagi.ru.monica.github.domain

enum class GithubIssueTemplateLanguage(val code: String) { CHINESE("zh"), ENGLISH("en") }

/** Generic starter forms never assume that a repository has particular labels or assignees. */
object GithubBuiltInIssueTemplates {
    private val byLanguage = GithubIssueTemplateLanguage.entries.associateWith(::create)

    fun forLanguage(language: GithubIssueTemplateLanguage): List<GithubIssueTemplate> = byLanguage.getValue(language)

    fun find(id: String): GithubIssueTemplate? = byLanguage.values.flatten().firstOrNull { it.id == id }

    private fun create(language: GithubIssueTemplateLanguage): List<GithubIssueTemplate> {
        fun text(chinese: String, english: String) = if (language == GithubIssueTemplateLanguage.CHINESE) chinese else english
        fun field(
            id: String, chinese: String, english: String, required: Boolean = false,
            hintChinese: String = "", hintEnglish: String = "", singleLine: Boolean = false
        ) = GithubIssueFormField(
            id = id, label = text(chinese, english), required = required,
            type = if (singleLine) GithubIssueFormFieldType.INPUT else GithubIssueFormFieldType.TEXTAREA,
            placeholder = text(hintChinese, hintEnglish)
        )
        fun template(kind: String, name: String, description: String, title: String, fields: List<GithubIssueFormField>) =
            GithubIssueTemplate(
                id = "builtin:$kind:${language.code}", name = name, description = description,
                title = title, fields = fields, isForm = true, isBuiltIn = true
            )
        val environment = field(
            "environment", "环境与版本", "Environment and version", singleLine = true,
            hintChinese = "项目版本、操作系统或浏览器", hintEnglish = "Project version, operating system or browser"
        )
        val context = field(
            "context", "补充信息", "Additional context",
            hintChinese = "相关链接、日志或其他有助于理解的信息", hintEnglish = "Relevant links, logs or other helpful information"
        )
        return listOf(
            template(
                "bug", text("问题反馈", "Bug report"),
                text("描述遇到的问题、复现步骤和预期结果。", "Describe a bug, how to reproduce it, and the expected behavior."),
                text("[问题反馈] ", "[Bug] "),
                listOf(
                    field("behavior", "遇到了什么问题？", "What happened?", required = true,
                        hintChinese = "描述实际发生的情况", hintEnglish = "Describe the actual behavior"),
                    field("steps", "如何复现？", "Steps to reproduce", required = true,
                        hintChinese = "1. 打开…\n2. 操作…\n3. 出现…", hintEnglish = "1. Open…\n2. Perform…\n3. Observe…"),
                    field("expected", "预期结果", "Expected behavior", required = true,
                        hintChinese = "你原本期望发生什么？", hintEnglish = "What did you expect to happen?"),
                    environment, context
                )
            ),
            template(
                "feature", text("功能建议", "Feature request"),
                text("说明希望解决的需求，并提出具体方案。", "Explain a need and propose a concrete improvement."),
                text("[功能建议] ", "[Feature] "),
                listOf(
                    field("problem", "希望解决什么问题？", "Problem or use case", required = true,
                        hintChinese = "描述使用场景和目前的不足", hintEnglish = "Describe the use case and the current limitation"),
                    field("solution", "建议的方案", "Proposed solution", required = true,
                        hintChinese = "说明你希望增加或调整的功能", hintEnglish = "Describe the feature or change you would like"),
                    field("alternatives", "考虑过的替代方案", "Alternatives considered"),
                    context
                )
            ),
            template(
                "question", text("使用求助", "Question"),
                text("说明使用中的疑问、尝试过的方法和相关环境。", "Ask a question and share what you have already tried."),
                text("[使用求助] ", "[Question] "),
                listOf(
                    field("question", "需要什么帮助？", "What do you need help with?", required = true,
                        hintChinese = "说明你想完成的事情和遇到的疑问", hintEnglish = "Explain what you are trying to do and where you are stuck"),
                    field("attempts", "已经尝试过的方法", "What have you tried?"),
                    environment, context
                )
            )
        )
    }
}
