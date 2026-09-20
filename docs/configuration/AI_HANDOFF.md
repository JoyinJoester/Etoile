# Etoile AI 交接文档

更新时间：2026-09-19（Asia/Shanghai）。本次交接只整理状态，不代表完整客户端目标完成。

## 1. 目标与工作环境

继续把 Etoile 做成可日常使用的第三方 GitHub Android 客户端，包含完整协作、代码管理、发布、Actions、账号/组织管理以及内置软件商店。

- 工作区：`D:\Etoile`；PowerShell；Kotlin / Jetpack Compose / Gradle Android 项目。
- 当前分支：`main`。交接检查时 `git status --short` 有 266 行，包含大量已修改和未跟踪文件。不要 reset、clean 或用 HEAD 覆盖工作区；重要的新导航、功能、测试和文档尚未跟踪。
- 功能包路径：`app/src/main/java/takagi/ru/monica/github/`。
- 总规划：`docs/configuration/FEATURE_PLAN.md`。历史：`CLIENT_ROADMAP.md`。历史中的勾选和完成措辞可能过度乐观，必须以代码和验证结果为准。
- 用户偏好中文、直接实施；紧凑 M3E、图标文字对齐、单顶栏、合理返回行为。不要每一步重复询问是否继续。
- 用户已要求正式客户端级功能，包括管理功能；不要把整体目标缩减成只读客户端。应用权限不等于用户实际权限，要核对令牌类型、用户角色及仓库授权范围。
- 用户要求本轮写交接文档，交接后由另一 AI 继续。

## 2. 安全与状态保存

- 聊天曾出现 Client Secret；不要复制到交接、日志、源码或 APK。若仍有效，应在 GitHub 轮换。不要输出 `local.properties`、生成的 BuildConfig 或服务端环境变量。
- GitHub App：App ID `4978930`；公开 Client ID `Iv23litmCSITWQoOAjio`。旧 OAuth Client ID `Ov23li4M7AzA4EZjLsne`。
- GitHub App 授权交换/刷新服务位于 `server/github-auth/`。公网部署和真实回调未有完成证据；不要声称正式 GitHub App 登录已经上线。
- 本地测试/模拟器内存示例不等于真实 GitHub 联调。开发功能不代表授权执行真实仓库写入、删除或发布。
- 使用 UTF-8 读写。历史中 Python 默认 GBK 写文件失败曾将 `RepositoryContentsScreens.kt` 清空，随后从 `.codex-tmp/large-screen-baseline-08/` 备份恢复并重新加编辑功能。必须复核此文件是否丢失原有布局、保存提示等改动，不能仅凭编译通过认定恢复完整。

## 3. 已有实现概览（不等于全部验收）

- 登录：浏览器/内置 WebView、设备代码自动填充、回调、PKCE、授权服务客户端、刷新协调、加密存储、多账号隔离。Passkey 主要依赖系统浏览器能力，不内置 Chromium。
- Issues：列表、模板创建、详情、评论编辑删除、状态/锁定、标签、负责人、里程碑编辑链路已有。接手前先搜索，避免重复添加。
- PR：创建、草稿、模板、分支比较、Fork 来源、差异展开、评审及合并基础。创建页与模板相关文件在 `feature/pullrequest/`、`data/GithubPullRequestTemplates.kt`。
- Discussions：列表、分类、发帖、回复、嵌套评论、编辑删除、采纳回答、草稿与并发加载保护。
- 通知：已读、全部已读、完成、退订基础已存在；未逐项真实验收。
- Actions：运行/工作流列表、日志、启停、触发、取消、重跑已有代码；不能再把这些当作全新缺失模块。
- 商店：F-Droid 多源、Release APK 回溯和兼容过滤、下载及阅读式详情基础；来源、动效、下载队列和真实设备仍需完善。
- 文件：Contents API PUT/DELETE、文本编辑、新建/删除对话框、文件行更多菜单基础。
- 分支：创建、删除、重命名接口、页面菜单和导航回调已接入。

## 4. 最近修改与关键文件

分支相关：

- `domain/GithubRepositoryContents.kt`：createBranch / deleteBranch / renameBranch 默认接口。
- `data/GithubRepositoryContentsRepositoryImpl.kt`：Git refs 创建/删除、branches 重命名。
- `feature/repository/RepositoryBranchesScreen.kt`：创建/删除/重命名对话框，默认和受保护分支隐藏删除/重命名入口；失败保留输入。
- `feature/repository/RepositoryBranchesViewModel.kt`：新增 Refresh，确保写入成功后重新加载第一页。
- `GithubRepositoryNavGraph.kt`：三个写操作回调。
- `app/src/test/java/takagi/ru/monica/github/data/GithubRepositoryContentsRepositoryImplTest.kt`：现有 12 项接口测试，包括分支操作。

重命名路径此前错误，现已改为 `/repos/{owner}/{repo}/branches/{branch}/rename`，branch 用单路径段编码，含斜杠的测试期望 `%2F`。创建/删除/重命名成功现在调用 Refresh，而非原来可能无操作的 Retry。

## 5. 接手应优先修的已知问题

以下问题来自代码检查，尚未修复或验收，不要被历史“完成”描述误导。

1. **Actions 手动触发路径错误**：`GithubActionsRepositoryImpl.kt` 约 322 行仍使用 `dispatch`，GitHub 端点应为 `dispatches`。先核对官方文档，修接口和协议测试。输入目前会 trim 值、截断数量，也应检查是否改变合法输入语义。
2. **文件读取与 SHA 不可靠**：`file()` 请求 raw，却从正文尝试猜测 JSON `content`/`sha`，还有 `quotedField()` 字符串截取。普通 JSON 源文件可能被误当元数据解码；正常 raw 无 SHA。应明确读取元数据与正文，避免猜测。文件大小限制也应避免无界读取。
3. **连续文件编辑丢失 SHA**：`RepositoryContentsViewModels.kt` 保存成功构造 `GithubFileContent.Text(text)`，没保存返回的 `result.contentSha`，第二次保存可能错误。补连续保存和并发冲突测试。
4. **文件列表 mutation UI 尚粗糙**：顶栏仍可能有文本 +/-；删除路径可编辑且提交时从当前列表查 SHA，没有固定选择时的目标版本；新建不允许空文件但 API 支持；缺少角色控制、准确错误分类、持久状态和设备验收。
5. **分支创建来源不是实时解析**：UI 只从已加载 `state.items` 找来源 SHA；未加载分页中的合法来源会报错，缓存 SHA 也不是承诺的“当前最新”。需要选择/分页及提交时解析来源策略，显示明确基点。
6. **分支名校验不完整**：ASCII 正则误拒绝合法 Unicode 名称，又允许部分 Git 非法格式（如 `.lock`、连续斜杠等）。删除还会 filter 空路径段，可能把错误输入规范化成另一目标。应共用符合 Git ref 规则的校验并测试。
7. **分支写操作状态在 Composable**：rememberCoroutineScope + remember，缺少进程恢复、统一忙状态、角色/账号隔离和过期响应保护；默认/保护状态也只是列表快照。应迁入 ViewModel，并按功能验证实际权限。
8. **路由/布局恢复需复核**：见前文文件被清空恢复的历史。文件编辑错误是否在对话框内可见、成功 SHA 是否显示、大屏布局是否仍保留，需直接检查和设备验证。
9. **文档权限措辞需纠偏**：CLIENT_ROADMAP 新增的“首次使用分别申请”不一定符合 GitHub App 安装权限模型。以 FEATURE_PLAN 中逐端点核对、重新授权/安装批准说明为准，不能把 App 权限当 Android 运行时权限。

## 6. 验证证据与限制

最近能够确认的日志：

- `.codex-tmp/branch-rename-check.log`：BUILD SUCCESSFUL，相关仓库内容测试 12 项通过。
- `.codex-tmp/branch-final-compile.log`：compileDebugKotlin，BUILD SUCCESSFUL。
- `app/build/test-results/testDebugUnitTest/TEST-takagi.ru.monica.github.data.GithubRepositoryContentsRepositoryImplTest.xml`：12 tests，0 failures/errors（测试结果会被后续运行覆盖）。

更早上下文记载全量 514 项及 Lint 通过，但后续文件/分支改动并未完成可靠全量复核。先前回复曾把没有捕获到最终结果的 Lint 当成功；不要沿用该结论。新分支功能尚无设备或真实 GitHub 写操作验收。

运行命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*GithubRepositoryContentsRepositoryImplTest'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

工具注意：保存 exec_command 返回的 session_id，使用 write_stdin 轮询直到 exit_code。functions.exec 的 cell 完成不代表内部 Gradle session 已结束。不要丢失 session_id 后再启动同工作区第二个 Gradle：历史中这样导致 Windows kotlin-classes 文件锁冲突。将输出写入 `.codex-tmp/` 日志并核实退出码。

设备参考：此前使用 `emulator-5556`，需重新执行 adb devices 核实在线。debug 示例在 `app/src/debug/java/`，包括 `create-pr`、`repository-writer`；脚本 `.codex-tmp/m3e-interactions.py`，截图 `.codex-tmp/m3e-screens/`。不要假设设备还装着最新包。

## 7. 推荐继续顺序

1. 先读本文件、FEATURE_PLAN、仓库本地指令，检查 git 状态与后台进程，保存已有工作。
2. 修 Actions dispatches 和文件 SHA/误解析问题，补能揭示错误的协议及连续操作测试。
3. 完善文件/分支 ViewModel 状态、权限、校验和回读，设备验证失败、取消、重复点击、切号与大字体。
4. 继续 Release 草稿/发布/编辑/删除及附件、Actions 产物下载、标签管理等真正缺失功能，按 FEATURE_PLAN 推进；已有的 Issue 元数据和通知操作先验收，不重复实现。
5. 随后推进仓库/组织/账号管理和商店体验；授权服务部署与真实账号联调作为上线前置条件跟踪。

不要把这几个修复当作完整目标；完整路线图仍有大量未实现项。交接中的建议是实现顺序，不是缩小用户需求。
