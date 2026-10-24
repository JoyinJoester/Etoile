# GitHub 写入传输验证

2026-10-22，源码及本机 MockWebServer 验证；没有使用真实凭据或修改远端项目，没有生成或安装 APK。

## 缺陷与修复

多个 GitHub 资源请求沿用默认 OkHttp 策略。服务端已接收请求后返回 `503` 和 `Retry-After: 0`，客户端会再次提交写入；只关闭连接失败重试不足以解决该重放。

新增 `GithubApiTransport.kt`：GET/HEAD/OPTIONS 保留调用方策略；其他方法使用一次性请求体、禁止连接自动重试和重定向。无正文 DELETE 使用一次性空正文；GraphQL POST 查询也保守采用此策略。已有可取消响应桥接让取消贯穿实际 Call 及正文读取。派生客户端保留拦截器、认证、连接池、调度器和原有超时，包含上传专用超时。

11 个文件、56 个调用接入：Actions、Issues、Notifications、PullRequests、Releases、RepositoryActions、RepositoryContents、RepositoryDetails、UserRepositories、PublicUser 仓库实现及 ReviewThreads API。部分调用是读取，不应将 56 解读为 56 类写操作。已有 Discussions 专用传输未修改。14 项默认英文及中文写入错误资源改为提示先核对服务端结果，避免“失败意味着没有执行”的错误暗示。

## 实际执行与结果

修复前先运行 13 类操作的 503 回归，**13 项全部失败**。日志保存于 `.codex-tmp/acceptance-mutation-initial-red.log`；原 XML 后被重跑覆盖，日志保留失败项和统计。

`GithubMutationTransportTest` 的 13 类操作各运行以下 4 项，共 **52 项测试**：

| 场景 | 断言 |
| --- | --- |
| 503，Retry-After 为 0 | 返回第一次 503，服务端只收到一次请求 |
| 301/302/303/307/308 跨源重定向 | 每种状态保留原响应，目标服务端零请求 |
| 接收请求后断连 | 失败不自动重发，服务端只收到一次 |
| 无响应期间取消 | 实际 Call 被取消，不启动另一次尝试 |

代表操作：Actions 重跑、Issue 评论、PR 创建、Release 创建、附件上传、Release 删除、Webhook 重新投递、文件写入、取消 Star、创建仓库、屏蔽用户、通知已读、解决评审讨论。覆盖 POST/PUT/PATCH/DELETE。附件断言输入流每次明确尝试仅打开/关闭一次，并核对实际上传字节。

`GithubApiTransportTest` 另有 **3 项测试**：GET 保留原重试和重定向策略；变更响应正文读取期间取消实际 Call；保留拦截器、认证、77 秒超时、Content-Type 和正文，一次性请求体可见且原客户端设置不变。

慢响应测试开发时，长时间正文延迟阻止 MockWebServer 及时关闭；直接使用响应限速又同时限制了传入 POST。最终自定义 Dispatcher 只限速响应，等待实际响应头和正文开始读取后取消。未放宽超时或移除断言。

定向命令：

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*GithubMutationTransportTest' --tests '*GithubApiTransportTest' --tests '*Github*RepositoryImplTest' --tests '*GithubRepositoryDetails*Test' --console=plain
```

结果 **20 类 / 261 项，零失败、错误、跳过**，日志 `.codex-tmp/acceptance-mutation-targeted.log`。随后修改文案并运行 `./scripts/verify-client.ps1`，两个变体各 **127 类 / 1123 项**通过，Lint 零错误、129 / 131 警告，Rust 3 项及 Node 5 项通过。定向与全量结果重叠，不相加。

## 验收边界

测试证明代表性仓库入口的传输行为，不能判断断连前服务端是否已提交；多请求批量变更仍可能部分成功。除已有专门实现的入口外，持久未确认状态、再次发送确认、实际发送前账号绑定仍需逐入口检查。提示文案不替代这些状态保护。

OAuth 令牌端点、部分直接 execute 的只读请求及完整认证传输不在此次范围内。拒绝重定向后，资源移动等情况需先核对新目标；不自动跨地址重新执行变更。真实 GitHub 写入、完整应用导航、设备表现和安装升级仍未验收。正式发布门禁目前仅因外部正式签名缺失而失败。

机器统计、源码快照及日志哈希见 [机器可读结果](verification-2026-10-22.json)。
