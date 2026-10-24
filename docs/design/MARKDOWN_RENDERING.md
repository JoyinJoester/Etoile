# Markdown 阅读渲染

GitHub 的 README、Issue、PR、评论及 Release，以及 GitLab 的 Markdown 文件、Issue / MR 正文与评论共用 `MarkdownPreviewText`。文档使用独立的系统阅读字体：正文 16sp/24sp，H1 28sp/36sp，H2 24sp/32sp，H3 20sp/28sp，代码使用等宽字体；颜色跟随当前主题，文字大小仍遵循系统字体缩放。

## 混排处理

- 基于 GFM AST 转换常用 HTML：链接、图片、强调、换行、标题、表格、列表与代码。转换跳过 Markdown 代码示例。
- HTML 转换、图片索引与引用索引在 `Dispatchers.Default` 上准备，避免进入/返回个人页和文档页时阻塞主线程。文档或明暗主题变化会取消旧任务，并创建独立状态，旧结果不能覆盖新内容。
- `&nbsp;`、`&emsp;`、数字字符实体等在文本注解阶段解码；不在解析前全局替换，防止实体中的 Markdown/HTML 字符改变文档结构。代码内容与转义的实体保留原文。
- HTML 图片保留链接、替代文本和宽高。`picture` 优先选择匹配当前明暗主题的图片，缺省使用 `img`。
- 每张图片使用独立的行内占位符；同段徽章与截图不再共用尺寸。显式尺寸按逻辑像素处理，超过容器宽度时按比例缩小。相同 URL 的不同尺寸独立计算，图片地址不附加尺寸参数。
- 相对图片仍通过原有的仓库、分支和文档路径上下文解析。普通与引用式图片均经过 HTTP(S) 地址校验。
- 图片加载期间显示说明；失败后显示可点击的重试入口，点击前不重复请求。被拒绝的 URI 显示不可用状态且不调用加载器。加载成功后按解码尺寸更新各图片的占位。
- GitLab 文件将链接基址设为当前修订的 `blob` 地址，图片基址设为同一修订的 `raw` 地址；保留自建实例子路径、文档目录和编码路径。Issue / MR 使用 API 返回且通过实例边界校验的 `web_url` 解析普通相对链接和图片。此处使用标准 URL 相对解析，尚未实现所有 GitLab 专有引用或上传路径规则。
- 链接保留调用方的导航回调，处理尖括号目标和 HTML 实体。公共打开入口仅允许 HTTP(S)、mailto 和相对目标。

这仍是原生文档阅读组件，不执行网页脚本或任意 CSS；HTML 容器按移动端阅读顺序排版，`details` 内容展开显示。

## 回归检查

`MarkdownRenderingTest` 覆盖截图中出现的实体、字号、徽章尺寸、HTML 图片链接，并覆盖明暗图片、引用式链接/图片、HTML 表格、代码原文、重复图片不同尺寸及图片禁用。`MarkdownImagesTest` 保留相对路径、分支、仓库边界和资源协议校验。

`GitLabWebUrlsTest` 覆盖实例子路径、修订和文件名编码及文档来源校验；`GitLabWorkRepositoryImplTest` 验证正文规范地址的解析。`NativeDesignRenderingTest` 在 Robolectric 中绘制实际 GitLab Markdown 文件与协作详情，并检查从编辑页返回后保留阅读位置。GitHub 讨论使用懒加载列表，滚出屏幕后保留回复线程的展开状态、已加载回复和草稿。

`NativeMarkdownImagesTest` 使用受控 Coil 加载器检查加载提示、解码后实际绘出的位图像素、失败后的显式重试和禁止协议零请求。模拟器截图及其验证范围见 [2026-10-24 复查](../acceptance/RELEASE_MARKDOWN_2026-10-24.md)。

验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'takagi.ru.monica.ui.components.MarkdownImagesTest' --tests 'takagi.ru.monica.ui.components.MarkdownRenderingTest' --tests 'takagi.ru.monica.github.component.GithubUiArchitectureTest' --console=plain --max-workers=1
.\gradlew.bat :app:lintDebug --console=plain --max-workers=1
```

上述主机检查不生成 APK。设备截图属于另外授权的模拟器调试包验证，不能代替真实账号和长文性能验收。
