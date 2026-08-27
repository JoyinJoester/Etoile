# Etoile GitHub 模块维护约定

这份文档是 GitHub 第三方客户端模块的人工维护入口。目标是让新功能沿用现有边界，而不是在页面里重新实现网络、分页或错误处理。

## 分层

```text
github/feature/*  ->  github/domain  <-  github/data
        |                  ^
        +-> github/component/design/navigation
```

- `domain` 只放纯 Kotlin 的模型、仓库接口和分页规则；不能依赖 Android、Compose 或 DTO。
- `GithubDomainArchitectureTest` 会阻止 Android、AndroidX、Java/JVM API 和 Compose 注解重新进入 `domain`，新增领域模型时必须保持 KMP 可共享。
- `data` 实现仓库接口，负责 URL、认证请求、DTO 映射和 `GithubPage`。
- `feature` 只负责 ViewModel 状态、事件和页面组合；页面不直接创建 OkHttp 请求。
- `component` 是跨页面复用的 Material 3 Expressive 组件；新增列表先检查这里是否已有行、空态、错误态或分页组件。
- `navigation` 只定义类型安全路由和链接解析，不把 `NavController` 传入叶子组件。

## 列表和分页规范

分页 ViewModel 的状态至少区分：

- `isLoading`：第一页/重置请求；
- `isLoadingMore`：追加请求；
- `nextPage`：服务端 `Link` 中的下一页；
- `error`：列表请求错误；写操作错误必须使用独立字段。

页面使用 `GithubPagedListStatus`，不要重复拼接错误、空态和“加载更多”按钮。该组件已经处理以下优先级：

1. 请求错误（保留已有数据）；
2. 筛选后无结果但仍有下一页；
3. 加载更多进度；
4. 下一页按钮；
5. 真正的空态。

分页合并统一调用 `GithubPage.mergeItems(existing, reset, keySelector)`，保证重置替换、追加保序和稳定 ID 去重一致。

Issue / Pull Request 列表查询统一使用纯 Kotlin `GithubIssueListQuery` / `GithubPullRequestListQuery`：状态、排序字段和方向由 Repository 发送给 GitHub，任一服务端排序变化都必须清空现有分页并从第 1 页重新加载。

标题搜索与 Pull Request Draft/Ready 属于“已加载结果筛选”，只更新 `UiState.visibleItems`，不得伪装成 GitHub 全局搜索或额外触发网络请求；筛选后无结果但仍有 `nextPage` 时继续允许加载更多。真正的全文搜索必须使用独立 Search/GraphQL 结果模型。

## UI 约定

- `UiState` 使用一个不可变 `data class`，通过 `StateFlow` 暴露。
- 页面使用单一 `Action` sealed interface；无状态组件接收状态和回调，不读取 ViewModel。
- `LazyColumn` 的每个实体都必须提供稳定 `key`。
- 外部链接通过 `GithubWebUrls`；能识别的 GitHub Issue、PR、Actions Run/Job 优先走 `GithubLinkRouter` 的原生路由。
- 单段 GitHub 用户主页（`github.com/{login}`）在通过保留路径校验后进入 `GithubUserProfileRoute`；`?tab=followers` / `?tab=following` 分别进入原生关系列表；未知站点和保留路径继续外部打开。
- 只读 GET 请求优先使用 `GithubCachedGetExecutor`；缓存键必须包含 `GithubAuthenticatedRequests.cacheScope()`，退出登录或切换账号时清空缓存。
- ETag/304 可复用缓存正文；只有网络异常或 5xx 才允许回退，401/403/4xx 不得展示旧账号数据。
- 缓存回退必须通过共享的 `GithubCacheFallbackStore` 上报，并使用 `GithubCacheFallbackNotice` / `GithubServiceStatusNotices` 告知用户；成功响应或 304 验证后按缓存键清除状态，不能静默展示旧数据。
- `GithubServiceStatusProvider` 是速率限制和缓存回退的 UI 作用域；首页、弹层直接消费共享状态，所有使用 `GithubDetailScaffold` 的详情页自动获得同一提示，禁止逐页传递或复制状态横幅。
- 用户列表统一使用 `GithubUserRow`；公开资料的 Followers / Following 使用 `GithubUserConnectionsViewModel` 和 `GithubUserConnectionsScreen`，列表项只传用户名回调，不把导航控制器下传到 feature。
- GitHub 底部弹层统一使用 `GithubModalBottomSheet`，标题/副标题统一使用可插槽的 `GithubSheetHeader`；业务页面只组合内容，不重复定义容器颜色、标题字号和基础间距。
- Issue / Pull Request 列表的搜索、服务端排序和局部筛选统一复用 `GithubListSearchField`、`GithubListOrderingSheet` 与 `GithubListFilterSection`；PR 通过 slot 增加 Draft/Ready，不复制弹层骨架。
- 结构化只读 GET（收件箱、搜索、Star、个人仓库、仓库详情、仓库目录、Issue、PR、Actions 元数据、Releases、Commits）统一复用 `GithubCachedGetExecutor` 和 `withCacheValidator`。README 的 404 语义、原始文件内容和 Actions 日志的重定向/大小限制保留专用路径，不强行套用通用缓存。
- 写操作成功后统一使用 `GithubCacheStore.invalidateAfter` 清除缓存；解析响应失败时不会提前清除，便于定位服务端异常，同时绝不在写失败时伪装为成功刷新。
- 文案必须进入 `values` 和 `values-zh`，页面不写用户可见的错误正文。
- 仓库文件浏览的 Branch/Tag 引用选择统一使用可搜索的 `GithubModalBottomSheet`；引用列表按 API `Link` 分页，Tags 延迟到用户切换标签页时加载，不在普通文件页初始化时额外请求。
- 仓库详情提供独立的原生 Branches 页面，复用 `GithubRepositoryContentsRepository.branches` 的 ETag/分页数据；页面支持本地搜索、默认分支和保护状态标识，并可直接进入所选 ref 的代码浏览。保护规则编辑仍打开 GitHub 官方设置页。
- 仓库 Collaborators 页面只在登录态提供入口，通过认证 GET `/repos/{owner}/{repo}/collaborators` 分页读取并映射 Read/Triage/Write/Maintain/Admin 角色；列表复用 `GithubUserRow` 并进入原生用户资料。无仓库权限时显示错误，成员增删和权限修改继续使用 GitHub Access 设置页。
- 仓库 Webhooks 页面只读展示 Hook 名称、启用状态、订阅事件和最近响应；API 响应中的 config URL、secret 等敏感配置不进入 domain/UI。Ping、删除、编辑等具有外部副作用的操作继续通过 GitHub Webhooks 设置页完成。
- Explore 搜索通过 `ExploreSearchKind` 区分 Repositories、Users、Code、Issues、Pull Requests；五类结果共享查询防抖、分页和错误状态。Issue/PR 使用独立轻量 `GithubIssueSearchResult`，只保存搜索列表字段，点击后进入现有原生详情并重新加载完整领域对象；不得把 Search API 的不完整 DTO 强塞进 `GithubIssue` / `GithubPullRequest`。Users 行进入原生公开资料页，Code 行继续跳转 GitHub 外链，因为搜索结果不保证提供可安全恢复的 ref 上下文。
- 公开用户资料统一使用 `GithubPublicUserRepository` 与 `GithubUserProfileRoute`；用户详情、公开仓库及 Followers / Following 分页共享 ETag 缓存，资料页不复用已登录账号模型，避免把权限字段误显示给其他用户。登录用户查看他人资料时通过 `/user/following/{login}` 查询和切换关注状态，写操作成功后清理 GitHub 响应缓存。
- 作者用户名统一使用 `GithubUserLink` 或 `GithubUserMetadataLine`，由应用根部的 `LocalGithubUserNavigator` 进入 `GithubUserProfileRoute`；feature 和共享组件不得持有或接收 `NavController`。
- Issue / PR 的 assignees、requested reviewers 等用户集合统一使用可换行的 `GithubUserGroup`，不得把登录名拼成不可点击的整段文本。
- 头像展示统一使用 `GithubAvatar`；图片加载只依赖 `GithubAvatarRepository` 接口，data 层负责 HTTPS 校验、协程错误边界、内存/磁盘上限和旧缓存回退，UI 始终保留固定尺寸的首字母占位，避免列表布局跳动。
- 支持手势刷新的列表统一使用 `GithubPullToRefreshBox`；刷新已有数据时保留当前内容并使用独立 `isRefreshing` 状态，首次加载继续使用 `isLoading`，避免列表闪空和重复进度指示。
- 动效使用 `GithubExpressiveMotion` 的短/标准规格，避免在大列表里创建无限动画或重复对象。
- Inbox 单条分拣使用独立 `triageBusyIds` / `triageErrorIds`，不得用一个全局写入状态锁住整页；Done 调用 DELETE `/notifications/threads/{id}`，取消订阅先 DELETE `/subscription` 再将线程标记 Done。取消订阅必须经过确认弹层，成功后再从本地列表和未读集合移除，失败保留原行并就地提示。
- 外部 GitHub 链接按钮统一使用 `GithubOpenOnGithubButton`，由组件保证 48dp 触控区、统一图标和 TalkBack 标签；页面不得自行复制 `OpenInNew` 的 `IconButton`。
- 文本输入计数统一使用 `GithubCharacterCounter`，视觉短格式与无障碍完整描述分别本地化；UI 中禁止直接拼接 `length/max`。
- 动态字号下优先换行而不是截断：共享指标标签允许两行，仓库元数据使用 `FlowRow`；避免对会推动页面布局的状态区域使用尺寸动画。
- Issue 评论 Reaction 使用 `GithubReactionContent` / `GithubReactionCounts` 领域模型和可复用的 `GithubReactionBar`；删除操作必须先按当前登录名查询 Reaction 列表取得 Reaction ID，再调用 DELETE，不能仅凭 content 删除。
- Issue 关闭/重开和会话锁定使用相互独立的写入状态；锁定通过 PUT/DELETE `/issues/{number}/lock` 后重新读取服务端 Issue，避免覆盖同时变化的 state。锁定后共享评论编辑器显示只读原因，ViewModel 也必须拒绝评论提交。
- Issue 标签编辑只在用户打开选择器时分页加载仓库 Labels，不随详情初始化额外请求；选择器复用 `GithubModalBottomSheet` / `GithubSheetHeader`，保存期间保持打开，失败保留选择并就地提示，成功后重新读取 Issue 再关闭。
- Issue Assignees 编辑同样按需分页加载 `/repos/{owner}/{repo}/assignees`，选择行复用 `GithubAvatar`；更新 PATCH Issue 前规范化登录名并限制最多 10 人，服务端返回的完整 Issue 是唯一成功状态来源。
- Issue Milestone 编辑按需分页加载开放里程碑，使用单选弹层并提供“无里程碑”；详情展示当前标题，选择器展示截止日期与开放/关闭数量。清除操作必须显式序列化 `{"milestone":null}`，不能省略字段。
- Issue 标题/正文编辑复用 `GithubIssueDraft` 的创建校验规则；详情页所有管理动作收口到单一“管理 Issue”弹层，再分别进入内容、Labels、Assignees、Milestone 子弹层或执行状态/锁定操作，避免主内容卡堆叠大量按钮。
- Pull Request Conversation 使用同一 `GithubReactionBar` 和 `GithubIssuesRepository` Reaction API，避免 Issue 与 PR 评论出现两套交互或网络实现。
- Issue 与 Pull Request 标题/正文编辑统一使用 `GithubConversationContentEditor`；各自的 domain draft 负责相同的长度与空标题校验，写入成功后以服务端返回对象替换详情状态并清理缓存。
- Pull Request 的内容编辑、关闭/重开和会话锁定统一收口到“管理 PR”弹层；锁定复用 Issues API 并以服务端返回的 `isLocked` 更新 PR 状态，锁定后 ViewModel 与共享评论编辑器双重拒绝新评论。
- Pull Request 的 Labels、Assignees 与 Milestone 复用 Issues API 和共享 `Github*EditorSheet` 选择器；只在用户打开子弹层时分页加载候选项，成功后仅用服务端 Issue 响应中的对应元数据更新当前 PR。
- Pull Request 请求审阅者使用专用 `/pulls/{number}/requested_reviewers` API；domain 更新模型规范化登录名并限制 15 人，Repository 先 DELETE 移除项再 POST 新增项，每个成功写入都立即清缓存。用户选择器与 Assignees 共享内部实现，但使用独立标题、校验和错误状态。
- Pull Request 合并属于不可逆高风险写操作；UI 必须先显示包含源分支、目标分支和合并方式的确认弹层，确认后才触发 ViewModel 的单次 merge 请求。
- 合并请求必须通过纯 Kotlin `GithubMergeDraft` 绑定当前 Head SHA，并允许 MERGE/SQUASH 提供可选提交标题与说明；Repository 将 `sha`、`merge_method`、`commit_title`、`commit_message` 一次性序列化，GitHub 在 Head 已变化时拒绝请求，避免确认期间误合并新提交。REBASE 不发送自定义提交文本。
- Pull Request 行级 Review 评论使用 `/pulls/{number}/comments` 独立分页模型，列表 key 必须带资源类型前缀，避免 Review、Review Comment 和 Conversation Comment 的数字 ID 冲突。
- Actions 只读列表与详情继续使用 ETag 缓存；重新运行/取消 Run、Workflow 启停和手动 Dispatch 属于写操作，必须经过认证、成功后清理缓存，并在 UI 中显示独立忙碌/失败状态。Dispatch 至少要求非空 ref，可选 inputs 使用逐行 `key=value` 输入并在 API 层限制最多 20 项。
- 仓库详情的“仓库设置”使用分组下拉菜单提供 General、Branches、Actions、Collaborators 和 Webhooks 网页入口；这些入口只负责打开 GitHub 官方设置页，不在客户端复制网页权限模型或伪造写操作。
- 仓库 Topics 的编辑通过 `GithubRepositoryDetailsRepository.updateTopics` 使用认证 PUT `/repos/{owner}/{repo}/topics`；UI 只在登录态显示编辑入口，提交前规范化空白/大小写/重复项并限制最多 20 个，成功后更新当前详情，失败显示独立错误。
- 仓库详情的分支保护概览只读取默认分支规则；404 表示该分支未配置保护，不应当当作网络错误。规则写入继续通过 GitHub 设置网页入口，避免客户端在权限不明确时修改安全策略。
- Reaction 的忙碌和失败状态按评论 ID 隔离，不能用一个全局写入进度阻塞整页评论；登录状态变化时取消旧请求并清空当前账号的 Reaction 选中状态。
- 星标分类是本地 `GithubStarCategoryStore` 元数据，不修改 GitHub Star API；分类筛选和仓库搜索在 ViewModel 中完成，分类名称必须通过 `values` / `values-zh` 资源提供。

## 测试策略

先跑与改动直接相关的测试，避免无意义全量构建：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "takagi.ru.monica.github.<具体测试类>" `
  --console=plain --max-workers=1
```

网络仓库测试 MockWebServer 的请求路径、认证头和 `Link` 分页；ViewModel 测试加载、追加、失败保留数据和重试；纯规则（路由、分页合并、日志清理）优先使用无 Android 依赖的单测。

只有跨多个 GitHub 功能或发布前验收时才运行全量测试。不要在本地验证中生成 APK，除非任务明确要求打包。

## 新功能检查清单

1. 先确定 domain 接口和 UI 状态，再写页面。
2. 查找可复用的 `Github*` component，避免复制卡片和状态分支。
3. 为第一页、下一页、空结果、失败重试和重复 ID 写最小必要测试。
4. 检查中英文资源、动态字号、无障碍描述和手机/平板布局。
5. 只运行受影响的定向测试，并记录编译警告是否为既有问题。
