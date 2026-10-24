# Actions / Release 账号归属验证

2026-10-22，本机 JVM、MockWebServer 和 Robolectric 原生绘制证据。没有读取真实凭据、操作远端项目、生成或安装 APK。

## 缺陷与修复

界面账号订阅与请求启动之间存在调度间隙。Actions / Release 已会在订阅到切号后取消旧任务，但旧回调和先排队的写协程可能在订阅器运行前进入仓库；仓库随后在 IO 读取当前令牌，不能保证它属于确认时的账号。先新增四项回归，立即切号与排队后切号的 Actions Dispatch / Release Save **四项均失败**，日志 `.codex-tmp/acceptance-action-release-account-red.log`。

现为 Actions 的重跑/取消、Workflow 启停和 Dispatch，以及 Release 的创建、编辑、删除、附件上传/删除补齐：

- 界面回调携带确认时显示的数值账号 ID，ViewModel 拒绝空账号、旧账号和不同的实时 StateFlow 会话；在排队协程真正调用仓库前再次核对，结果回写前也核对。
- 两个仓库提供 `forAccount`，保留原客户端、JSON、缓存及报告器配置，使用绑定账号的 `GithubAuthenticatedRequests`。后台实际构造请求时，在与凭据更新共用的存储锁内核对活动账号、该账号凭据和读取令牌的一致性；禁止退回另一个账号或匿名请求。同账号正常轮换后的令牌仍可使用。
- 原有未绑定请求构造行为保留；绑定实例的普通/可选构造器与缓存 scope 均遵守绑定，读取也不静默转匿名。单次请求构造后已有账号标签，凭据刷新仍按该数值 ID 处理；不声称切号能撤销已到达服务端的操作。

普通 Flow 只能复核已观察到的账号；生产导航注入的是认证 StateFlow，因此能检查当前值。没有会话源的预览/内存测试宿主沿用原行为，不能据此当作凭据安全证据。测试替身的 `forAccount` 明确返回自身，生产安全行为由真实仓库的网络测试证明。

## Workflow 表单及确认

Workflow 行自身的启停确认原来没有账号键，Dispatch 表单打开标志也可保存恢复。生产导航外层曾用账号 key 重建整个页面，部分掩盖组件自身的缺口；本次让组件独立保证确认归属，不能把组件测试发现描述成全部生产路径均已复现的远端误操作。

Workflow 启停确认绑定账号和工作流 ID。Dispatch 改为页面持有的整页表单，展示仓库/工作流、ref、inputs 及可能触发部署或费用的说明；仅点击 Run 提交，返回不提交。打开目标不保存，切号及权限失效即关闭。字段和列表保存区由仓库全名、数值账号及工作流 ID 区分；恢复后手动打开可读同账号字段，另一账号不能读到旧字段。移除生产 Workflow 页外层重复账号 key，让页面内部保留分账号保存区；其他导航不在本次调整范围。

三个新增主机测试在原 AlertDialog 打开后连续无法空闲，日志 `.codex-tmp/acceptance-action-release-dialog-failure.log`。整页布局后相同输入/账号/恢复断言通过；随后一次失败是同名标题和按钮定位歧义，改为定位可点击按钮。截图测试首次使用扩展函数方式错误导致编译失败，修正 import/接收者后通过。未移除断言、禁用动画或扩大超时。主机空闲问题不能单独证明真机卡顿已复现或修复。

## 测试证据

| 层次 | 覆盖与断言 |
| --- | --- |
| 真实仓库，32 项 | 8 类写操作 × 4：独立绑定 A/B 且不回退令牌；IO 排队后切号不读替代令牌、零网络；空/无效账号、缺失/空/错配凭据零网络；同账号轮换后使用最新令牌 |
| ViewModel，新增 8 项 | 入口即时切号、先排队后切号、切号订阅完成后使用旧确认均不调用写仓库；同账号三类 Actions、五类 Release 正常调用且选中绑定仓库。部分测试内循环覆盖多个操作/时序，不能按循环次数增加测试总数 |
| 原生组件，新增 4 项 | Workflow 启停及 Dispatch 确认不跨账号/返回继承；同账号/另一账号的 Parcel 保存 Bundle 后全新 Activity 恢复；320dp、1.5 倍字体下输入及 Run 可滚动到达且正文正确提交 |

本轮扩展定向命令包括 `GithubResourceAccountBindingTest`、Actions / Release ViewModel 和仓库类、Discussions 传输类、`RepositoryConfirmationIsolationTest`、公共变更传输矩阵及辅助类；**9 类 / 168 项，零失败、错误、跳过**，日志 `.codex-tmp/acceptance-action-release-account-targeted.log`。截图 `app/build/reports/acceptance/workflow-dispatch-narrow-large-font.png` 已人工查看。后续完整套件实际统计以 [本轮验证记录](VERIFICATION_2026-10-22.md) 和 [机器结果](verification-2026-10-22.json) 为准。

## 尚缺的验收

测试没有使用完整 Actions 生产 NavHost、真实账号选择页或操作系统进程死亡；新 Activity 的保存 Bundle 恢复仅证明指定组件字段归属。真实 GitHub 权限、状态冲突、安装升级、性能和无障碍仍需验收。

本轮不是全部 GitHub 写入口的账号审查，其他资源仍需逐入口推进。Actions / Release 对响应丢失的持久未确认状态及再次提交确认也未在本轮完整实现；公共禁止重试及错误文案不能代替它。已发送的请求无法因切号撤销，也不保证批量操作原子性。
