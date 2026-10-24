# Webhook 投递记录与重投

入口：仓库详情 → Webhooks → 选择 Hook 或点击“投递记录”。顶栏仍可打开对应 GitHub 官方设置页。

记录展示事件、活动、投递 ID、HTTP 状态、耗时、投递时间、GUID 和重投标识；仅读取元数据，不读取请求/响应正文或 secret。API 使用 `Link` 中的不透明 `cursor`，小数秒耗时转换为毫秒。支持加载更多、手势刷新和失败重试；刷新或下一页失败时保留已加载记录。

重投按钮仅在当前账号的仓库管理权限确认后可用。点击后须确认仓库、Hook 和投递 ID，并提示可能重复触发接收端的构建、部署等操作。提交期间拦截重复请求。GitHub 的 202 只表示接受重投请求，客户端刷新记录但不把原投递伪装为成功；后续读取失败可重试 GET，不会再次 POST。HTTP 超时等写入错误也不会自动重发。

会话恢复中、切换账号或退出登录时，清空旧记录、权限和操作反馈，取消旧请求并忽略迟到结果。确认状态不跨账号、权限变化或进程重建保留。

投递读取和写入仍受 GitHub 的仓库权限与令牌权限约束；页面会区分无权限、Hook 不可用、限流和一般读取错误。该功能不扩展现有登录授权范围。

## 验证

2026-10-21：最终定向测试 63 项通过，0 失败、错误或跳过；`lintDebug` 通过，0 errors/fatal、104 warnings。日志：`.codex-tmp/github-webhook-deliveries-final.log`。本次未生成新 APK。

- `RepositoryWebhookDeliveriesViewModelTest`：游标合并、刷新/分页失败重试、并发请求、管理权限、未知投递 ID、202 后刷新、刷新失败不重复写入、会话切换及迟到响应。
- `GithubRepositoryDetailsRepositoryImplTest`：真实格式小数耗时、认证、游标转义、ETag/304 保留分页、写入成功才清缓存、权限失效不使用旧缓存、202 空正文。
- `GithubActionConfirmationTest` 与 `GithubUiArchitectureTest`：明确确认才执行、取消不执行、通用组件和本地化约束。

本轮使用 MockWebServer 和 JVM 测试验证，未向真实 GitHub 仓库发送重投，也未完成新增页面的设备视觉验收。
