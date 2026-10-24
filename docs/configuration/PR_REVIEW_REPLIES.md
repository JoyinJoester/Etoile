# PR 评审协作

## Diff 行评论与批量评审

Files 页为可定位的 Diff 行提供左/右行号按钮，分别表示修改前和修改后；点击选择精确文件、侧及行号。可以立即发布，也可以加入本地评审草稿。没有 patch、被截断而未显示的行、hunk 标头和元数据不提供评论定位。单行评论暂不提供跨行范围选择。

Files 和 Activity 页均提供“评审草稿”入口，可编辑或移除暂存评论、填写总结、选择 Comment / Approve / Request changes，然后确认一次发布。Request changes 必须填写总结，自己的 PR 只允许 Comment。每份评审最多 50 条行评论，评论与总结正文合计最多 65,536 字符。所有行评论、总结和评审结论使用一次 REST 请求发布；这是本地批量草稿，不同步 GitHub 服务端 pending review，也不支持跨设备同步。

行号绑定选中时的提交 SHA。发送前先以认证请求重新读取 PR，不经过 ETag 缓存或离线回退；Head 已变化、PR 关闭/合并或锁定都会停止发送并保留草稿。用户需要刷新 PR 与文件；Head 已变化时，先复制仍有用的笔记，清理旧草稿后重新选行，不能自动迁移旧行号。真正发送的请求始终带 `commit_id`，避免把旧行号提交到新的提交。

未发送的行编辑内容、暂存评论、总结、结论和页面打开状态同样按账号、仓库、PR 隔离，通过 `SavedStateHandle` 恢复。退出任务后的永久保存仍不在本功能范围。发送中不能修改、清理或重复提交；恢复被中断的发送不自动重发。请求超时或响应无法解析时，草稿保留并提示先检查远端是否已存在，避免手动重试造成重复发布。

## 解决与重新打开讨论

Activity 页将 GraphQL Review Thread 状态与 REST 根评论 ID 对应，展示已解决/未解决和旧版本标识。通过 `fullDatabaseId` 保留完整 64 位评论 ID，不使用已废弃的 32 位 `databaseId`；字段与权限已核对 [GitHub GraphQL schema](https://github.com/octokit/graphql-schema/blob/master/schema.graphql)。读取 `viewerCanResolve` / `viewerCanUnresolve` 后提供对应按钮。点击后确认目标再调用 `resolveReviewThread` 或 `unresolveReviewThread`，以服务端返回状态更新页面，失败保留旧状态。线程状态独立使用游标分页，可刷新状态与权限或继续加载更多；加载失败不影响阅读既有评审评论。

讨论状态写入时取消在途旧读取，拒绝迟到结果。切换账号清空旧账号的线程权限、待确认操作与错误提示；旧账号请求返回不能改变新账号页面。HTTP 200 但带 GraphQL errors、缺少返回数据或返回状态不符合目标，均不算成功。

## 使用方式

进入 PR 详情的 Activity 页，评审行评论按原讨论分组。登录且 PR 未锁定时，点击评论下方“回复”进入独立编辑页；回复某条回复也会提交到它所属的原讨论。编辑页显示原评论的文件路径和正文预览，支持 Markdown 输入。

点击返回会保留未发送正文，Activity 页提供“继续回复草稿”。同一个账号在同一个 PR 中保存一份待回复草稿。改为回复另一讨论且已有非空正文时，必须确认丢弃；取消不改变目标或正文。账号切换和退出后，旧草稿不出现在当前账号页面，切回原账号可继续。

## 接口及状态约束

- 使用 REST `POST /repos/{owner}/{repo}/pulls/{pull_number}/comments/{comment_id}/replies`，只发送 `body`，`comment_id` 必须是顶层评审评论 ID。读取 `in_reply_to_id` 建立分组；根评论不在已加载分页时，已有回复仍可显示和回复。
- 正文 trim 后非空，最多 65,536 字符；发送前再次校验登录、PR 锁定状态与讨论是否已加载。实际仓库权限由 GitHub 判定，403/404/422 等错误保留草稿，由用户明确重试。
- 发送中禁止重复提交、修改正文或关闭编辑器。成功将服务端返回的评论去重加入列表并关闭编辑器，失败保留正文与目标。不会因为读取失败、页面恢复或账号切换自动重发。
- 成功后取消在途评论读取并忽略迟到结果，保留下一页游标供用户继续加载。切换账号也会取消旧请求并拒绝旧结果。
- `SavedStateHandle` 只保存目标 ID、正文及编辑器打开状态，按账号 ID、仓库和 PR 隔离。不保存发送进度、确认操作、错误和成功提示；属于导航任务状态恢复，不是退出任务后的永久草稿库。
- 当前行号不可用时展示 `original_line`，所有行号均不可用时只展示路径，不显示第 0 行；旧版本评论带提示。

## 验证

2026-10-21 定向单元测试与 `:app:lintDebug` 均通过，日志 `.codex-tmp/pr-review-replies-check.log`。共 57 项测试，失败、错误和跳过均为 0；Lint 无 Error/Fatal，104 项既有 Warning，未增加警告。

测试类为 `GithubReviewCommentReplyTest`、`GithubPullRequestsRepositoryImplTest`、`PullRequestsViewModelTest`、`GithubDomainArchitectureTest` 和 `GithubUiArchitectureTest`。覆盖输入及分组规则、MockWebServer 请求与响应/缓存、ViewModel 提交和失败、发送中单飞、锁定拦截、草稿任务恢复、丢弃确认、账号隔离及迟到回复/分页响应。中英文资源各新增 11 项，XML 解析及资源名称重复检查通过。

本轮不生成 APK，不向真实 GitHub 仓库发送回复。真实权限、设备交互与系统实际回收进程后的恢复仍待验证。

## 本次扩展验证

2026-10-21 最终验证：93 项定向测试全部通过，失败、错误、跳过均为 0；`:app:lintDebug` 无 Error/Fatal，保留 104 项既有 Warning，没有增加警告。最终代码编译通过。日志为 `.codex-tmp/pr-review-workspace-complete.log`，测试 XML 位于 `app/build/test-results/testDebugUnitTest/`，Lint 报告位于 `app/build/reports/lint-results-debug.xml`。

覆盖：左右侧及多 hunk 行号、无效/截断定位、单条行评论、批量请求内容与单次发布、无缓存预检、Head 变化与锁定/关闭拦截、自己的 PR 评审限制、草稿编辑/删除/清空确认、任务恢复与账号隔离、迟到写入和列表响应、线程权限与确认、GraphQL 部分错误、游标循环、缓存失效及 64 位根评论 ID。中英文资源包含数量复数，资源类型、缺失和重复检查通过。

没有向真实 GitHub 写入或生成 APK。设备交互、真实账号权限、网络中断后的远端核对、系统实际回收进程后的恢复仍需联调验收；不代表整个客户端已完成发布验收。
