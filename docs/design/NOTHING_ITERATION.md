# Nothing 页面迭代记录

本轮以深色优先，浅色沿用对应主题色令牌。字体使用应用内打包的 Doto、Space Grotesk、Space Mono，不依赖在线加载。

## 已落地

- 首次启动和缺失设置的默认设计改为 Nothing，保留已有用户保存的风格选择。
- 共用详情框架将内容居中并限制为 920dp，覆盖仓库、代码、Issue、PR、Actions、Release、提交、组织、账号等使用该框架的页面；保留 840dp 双栏切换空间。
- Nothing 模式的列表初始加载和共用进度组件使用本地化状态文本；其他设计风格保留原有加载方式。
- Nothing 空状态采用左对齐的图标、标题、说明和操作，减少居中的装饰容器。
- 共用弹层使用主题圆角；排序弹层支持纵向滚动，筛选项可换行。
- “我的 Issue / PR”增加开放、关闭、全部筛选和刷新。筛选查询作用于 GitHub 服务端，分页沿用所选条件；切换筛选重置分页，账号加载或失效时取消旧请求并清空列表。
- 首页 Discussions 和 Projects 从提示型占位入口改为 GitHub 外部页面入口，明确显示“在 GitHub 上打开”。
- 登录和首页主要按钮采用最小高度，允许字体放大后增长；交互标签保留 48dp 触控高度与选中语义，纯展示标签不再伪装为禁用按钮。

## 后续逐页检查

下表是后续验收范围，不代表这些页面已完成运行时验收。

| 页面 | 主要任务 | 需重点检查 |
| --- | --- | --- |
| 首页 | 进入工作队列 | 贡献区与收藏区的层级、长分类列表 |
| 收件箱 | 分拣通知 | 长仓库名、批量状态、操作失败提示 |
| 探索 | 查找仓库与协作内容 | 五种搜索范围、筛选重置、翻卡模式 |
| 商店 | 浏览来源和安装包 | 下载状态、来源切换、窄屏详情 |
| 个人资料 | 账户与偏好 | 成就区大字号、热力图横向空间 |
| 仓库详情 | 代码和协作入口 | 手机单栏、折叠屏双栏、权限控制 |
| 文件与分支 | 切换引用并阅读代码 | 长路径、横向代码滚动、搜索空状态 |
| Issue / PR | 处理会话与评审 | 评论编辑键盘、Diff、管理弹层 |
| Actions | 工作流和运行操作 | 日志可读性、手动触发参数、运行状态 |
| Releases / Commits | 阅读版本和变更 | 资源列表、长正文、差异显示 |
| 星标 / 仓库 / 组织 / 用户关系 | 管理与查找列表 | 分类、分页、排序弹层 |
| 登录 / 账号 | 授权与切换 | 大字号按钮、错误恢复、账号隔离 |

## 验证记录

### 详情与商店后续迭代（2026-09-13）

- 分支/标签选择器改用共享分页状态组件：本页搜索无匹配时仍显示加载更多；分页失败不再隐藏已加载条目；加载中不提前显示空状态。增加清空搜索，并允许分支尚未加载时打开弹层切换标签。
- 标签单独失败后的外层重试改为加载标签。共享分页测试已明确覆盖 `EMPTY_WITH_LOAD_MORE`、加载期间不显示空状态及错误优先级；真实多页分支选择仍需在线验收。
- 分支选择修复后 `:app:testDebugUnitTest :app:assembleDebug` 成功，317 测试零失败（`iteration-refs-build.log`）。

- Actions 手动运行表单校验每个非空参数行，拒绝缺失等号、空名称和重复名称，并显示行号；阻止部分参数被静默丢弃后提交。保留空值和值内等号支持，提交前清理 ref 两端空白。
- 参数表单支持滚动、最多八行的输入窗口及旋转后恢复；新增四个参数解析回归测试。
- Actions 日志增加自动换行/横向滚动切换，继续支持文本选择和服务端截断提示。
- 参数表单首轮测试与构建成功（`iteration-actions-build.log`）；真实账号的工作流 dispatch 未执行，权限与在线运行验收仍待完成。
- 日志切换与弹窗状态恢复补齐后，317 个测试零失败，最终 APK 构建成功（`iteration-actions-verified.log`，`Etoile-Android-arm64-v8a-0.1.0-26091301-07.APK`）。中途一次 R8 因 classes.dex 占用失败，确认 Gradle 空闲后重试成功。日志切换和旋转后的表单恢复仍需运行时验收。

- 提交文件差异不再静默截断：超过 6000 字符时显示说明及展开/收起按钮，可查看 GitHub 返回的完整 patch；支持横向滚动与文本选择，统计行允许换行。
- 二进制/超大文件的不可预览状态增加直接打开 GitHub 的操作。
- 商店详情加载失败使用 Release 错误文案和重试操作，避免误报目录失败。
- 阅读改动首轮测试与 APK 构建成功（`iteration-reading-build.log`）。模拟器已呈现 GitHub 未登录配额耗尽提示（0/60），真实提交详情和长差异展开仍未完成运行时验收；不要据此将整个阅读流程标为完成。
- 最终文案修正后 `:app:testDebugUnitTest :app:assembleDebug` 成功，313 个测试零失败（`iteration-reading-final.log`）；APK 为 `Etoile-Android-arm64-v8a-0.1.0-26091301-05.APK`。

- 后续修复底部导航及侧栏标签的大字号拆行：单行省略显示，保留完整文本语义。API 35 模拟器 1.5 倍字号截图 `nav-large.png` 与无障碍树确认。
- 推荐目录查询改为 `topic:android-app stars:>100 archived:false fork:false sort:stars-desc`，减少 Android 框架和资料合集；API 与应用内均确认返回 SimpMusic、RTranslator 等应用项目。主题标签并不保证存在 APK，仍以详情中的 Release 资产为准。
- 本次 `:app:testDebugUnitTest :app:assembleDebug` 成功，313 测试零失败（`iteration-navigation-build.log`），最新版为 `Etoile-Android-arm64-v8a-0.1.0-26091301-03.APK`。

- 仓库标题允许两行，所有者可进入资料页；统计数据换行；Nothing 功能入口去除彩色底块；已登录但无管理权限的 Webhooks 入口明确禁用。
- 资料页成就卡根据可用宽度和字号调整为一至三列，取消固定高度和单行文字限制。
- Issue / PR 管理弹层操作区可滚动，避免横屏和大字号下底部操作不可达。
- 商店详情增加加载错误重试，版本及语言标签换行，预发布状态本地化。
- 商店来源区移入目录滚动，来源逐行显示完整仓库标识；应用网格根据字号增加最小列宽；F-Droid 版本信息放在名称下方。
- 商店顶部刷新现在按当前目录分别刷新推荐或 F-Droid，并在加载时禁用重复刷新。
- 上轮详情改动构建成功（`iteration-details-build.log`）；本轮来源区编译成功（`iteration-catalog-build.log`）。本批改动尚未完成模拟器视觉验收，不沿用之前首页截图作为完成证据。
- 最终 `:app:testDebugUnitTest :app:assembleDebug` 成功，313 个测试零失败（`iteration-catalog-final.log`）。ADB 端口 5041 可启动，但未连接到模拟器，直接连接 5555 被拒绝；视觉验收仍待继续。
- 后续定位模拟器监听端口为 5563，通过 ADB 5041 成功安装并启动。目录正常字号双列、1.5 倍字号单列已经截图确认（`catalog.png`、`catalog-large.png`）。同时发现大字号底部导航英文换行与推荐目录包含非应用仓库，仍需后续处理。
- 运行时发现首次默认 Nothing 与独立 DEFAULT 配色组合仍会使用动态颜色；主题颜色选择现优先判断 Nothing 设计风格，避免字体/形状与颜色不一致。
- 配色修复后测试和 APK 构建成功（`iteration-nothing-palette.log`），产物 `Etoile-Android-arm64-v8a-0.1.0-26091301-02.APK` 已安装；深色截图为 `nothing-fixed.png`。

- `:app:testDebugUnitTest`：313 个测试通过，包含 7 个“我的会话”测试。
- `:app:lintDebug`：未通过，报告 98 个错误、99 个警告，主要为已有 Material 内部 API 调用、主题资源 API 等级与 Manifest 删除节点检查。随后修正了商店 APK 版本号的 API 26/27 兼容调用，尚未重新统计 Lint。
- 构建日志存在 Kotlin / R8 元数据版本兼容警告。
- 最终 `:app:testDebugUnitTest :app:assembleDebug --max-workers=2` 成功（`iteration-default-build.log`）。
- Android API 35 模拟器安装成功；以手机尺寸检查 Nothing 深色首页、深浅色探索页、五种搜索范围菜单与 1.5 倍字号登录页。未操作远端登录或仓库写入。
- 本轮截图：`.codex-tmp/iteration-phone.png`、`iteration-explore.png`、`iteration-light.png`、`iteration-login-large.png`。

验证命令和本轮结果见 `.codex-tmp/iteration-*.log`。实际设备截图检查与已登录远端操作需单独记录，不以 JVM 测试代替。

### 2026-09-13 启动主题与系统栏兼容

- Nothing 默认启动背景统一为深色 #000000 / 浅色 #F5F5F5，移除旧 Material 紫灰底色。
- Android 8.0 基础主题仅保留支持的属性；8.1 导航栏图标、Android 10 系统栏对比设置分别放入 v27/v29 资源，保留 Android 12 Splash 资源。
- Android 8.0 导航栏使用黑底以支持白色系统按钮；运行时导航栏图标与底色跟随应用内主题切换。
- 完整 Lint 从此前 98 错误降为 90 错误、104 警告（iteration-startup-lint.log）；NewApi 和 SuspiciousIndentation 错误已消除。剩余错误为 89 个 CustomColorSchemeGenerator 内部 Material API 调用及 1 个 Manifest 删除节点 MissingClass。
- 上述 Lint 在最终运行时导航栏补丁前启动；启动资源编译已通过。API 26 和最终系统栏切换尚未完成设备验收。
- 最终 :app:assembleDebug 成功（iteration-startup-final.log），产物 Etoile-Android-arm64-v8a-0.1.0-26091301-09.APK；R8 元数据兼容警告仍在。

### 2026-09-13 设置页布局与无障碍

- Nothing 设置分组改为留白组织，移除卡片底块；其他设计保留各自容器。
- 主题、设计和语言选项增加单选角色与选中语义、56dp 最小触控高度。
- 配色从无名称横向色块改为具名纵向单选列表，支持文字换行；勾选使用主题文字色，避免白底白勾。新增中英文配色名称。
- 最终 assembleDebug 成功（iteration-settings-retry.log），APK 为 Etoile-Android-arm64-v8a-0.1.0-26091301-11.APK。
- API 35 模拟器验证 Nothing 深色/浅色设置及 Material 具名配色列表；无障碍树确认 checked=true 与 RadioButton 语义。截图 settings-final.png、settings-light.png、settings-palette.png。未运行真实 TalkBack 朗读或验证所有其他语言。
- 首次构建没有纳入最终分组补丁，后续重新编译并成功安装验收；打包期间再次出现 classes.dex 占用，重试成功。

### 2026-09-13 通知状态回滚与长标题布局

- 通知行时间移到标题下方，不再占用右侧标题宽度；右侧保留操作菜单。
- 已读操作记录会话版本，退出登录或切换账号后忽略旧请求回调；会话变化取消旧列表加载。
- 全部已读失败时合并当前未读集合，只恢复仍在列表中的原条目，避免覆盖分页新加载的未读通知。单条失败也不恢复已移除条目。
- 新增退出登录后单条失败、切换账号后批量失败、批量请求期间分页三项回归测试。InboxViewModelTest 共 13 项通过（iteration-inbox-tests.log），主代码与测试编译成功。
- 已登录通知行的实际视觉验收仍待完成；本轮未重新打包 APK，最新可安装产物仍为设置页迭代的 26091301-11。

### 2026-09-13 我的仓库筛选和失败重试

- 我的仓库增加页内搜索：匹配仓库全名、描述和语言，忽略大小写；关键词按账号保存，支持一键清除。
- 显示已加载仓库数及匹配数，明确本地筛选范围；搜索控件随列表滚动，零匹配时仍保留分页入口。
- 修复刷新失败后 Retry 错误请求下一页或无响应的问题：记录失败请求类型，刷新重试第一页并保留行，分页重试原页。
- UserRepositoriesViewModelTest 三项通过（iteration-user-repos-tests.log），包括新加入的刷新失败与分页失败两项回归。
- assembleDebug 成功（iteration-user-repos-build.log），包含上一轮通知修复。已登录仓库筛选页面尚未完成设备视觉验收。
- 本轮 APK：Etoile-Android-arm64-v8a-0.1.0-26091301-12.APK

### 2026-09-13 收藏标签弹层

- 标签分配、批量分配、标签管理三个弹层增加垂直滚动，避免长列表把创建输入框推到屏幕外。
- 批量标签使用两行结构，数量说明移到标签下方，保留标签名称宽度。
- 管理行以 label.id 建立稳定组合标识，删除前序标签不会把重命名/删除确认状态移到另一行。
- 名称输入、重命名展开及删除确认使用 rememberSaveable，支持配置变化后的状态恢复。
- 重命名校验失败保留编辑框，错误绑定对应 label.id；创建失败归属创建输入框，避免错误跑到另一个表单。
- StarredViewModelTest 全部通过（iteration-labels-tests.log），新增错误归属及修改后恢复测试；主代码编译通过。长列表、旋转和键盘弹出时的设备验收尚未完成，本轮未打包 APK。

### 2026-09-13 Release 下载阅读布局

- Release 说明标题增加资产跳转入口，长说明下无需逐段滚动寻找下载区；没有资产时不显示入口。
- 下载资产的显示名称及实际文件名完整换行，保留尾部架构、版本和扩展名。
- Nothing Release 标题去除容器底色和装饰图标，释放标题宽度；其他设计保持原容器。
- compileDebugKotlin 通过（iteration-releases-layout.log）。确认 MarkdownPreviewText 的 maxElements 只是兼容参数，不实际截断当前说明渲染。
- 长说明跳转及长文件名尚未完成设备视觉验收；需使用公开 Release 或专用界面样例补齐，不以编译通过代替。

### 2026-09-13 确定性界面样例与设备验收

- 新增仅 debug 构建的 DesignAuditActivity，复用生产页面、使用本地长内容样例，不创建会话或调用仓库接口。操作只显示 Toast。
- API 35 模拟器已验证 Release 资产快捷跳转、三种架构完整文件名，以及 30 个标签滚动到底部创建框；截图 audit-assets.png / audit-labels-bottom.png。
- 实际截图发现 Markdown 标题继承 Doto 展示字号，现改用 Nothing 正文字体及 28/24/20sp 标题层级。
- MarkdownPreviewText 原有 onOpenExternalLink 参数未使用，现通过 LocalUriHandler 接入；未传回调时保留系统 URI 处理器。
- 样例构建成功（iteration-audit-build.log）；Markdown 后续改动 compileDebugKotlin 成功（iteration-markdown-compile.log），尚未重新安装验收标题和链接点击。
- 使用方式及验证边界见 UI_AUDIT_SAMPLES.md。整体目标保持进行中，样例检查不替代真实接口验收。

### 2026-09-13 Markdown 与标签旋转复验

- 最终 APK 构建与安装成功（iteration-markdown-retry.log），API 35 截图 audit-markdown.png 确认正文标题已改为正常阅读字号，不再使用 Doto 大号展示标题。
- 标签样例输入 RotationDraft，设备横屏再转回竖屏后滚动到输入框，无障碍树 labels-restored.xml 确认草稿仍在且创建按钮可见。测试前后恢复 accelerometer_rotation=1、user_rotation=0。
- 相对链接点击未取得明确回调证据，仍待继续；不能将源码接入 LocalUriHandler 或下划线样式当成运行通过。
- 直接检查实际使用的 Markdown 0.39.2 字节码，确认其 annotatorSettings 使用 UriHandler 处理链接；下一步需要持久的调试回调记录定位点击问题。

### 2026-09-13 Markdown 点击回调证据补齐

- 调试样例新增原生链接、Markdown 绝对链接和相对链接对照页，增加页面底部持久回调结果。
- 发现 proguard-rules.pro 会移除 Android 日志，因此先前没有 Logcat 输出不能作为未点击的证据；Toast 同样不适合可靠验收。
- API 35 确认 native clicked、markdown: https://example.invalid/absolute、markdown: ../relative 三种回调；证据为 link-native-result.xml / link-absolute-result.xml / link-relative-result.xml。
- Release 点击样例触发仓库 Markdown 链接解析并生成完整 URL（link-release-result.xml）。该检查不代表已覆盖所有 GitHub 相对路径约定。
- 最终构建与安装成功（iteration-link-visible-retry.log）。本轮主要完成设备验证与改进调试观察方式，没有另改生产链接逻辑。

### 2026-09-13 Markdown 地址解析

- 显式 URI 协议直接交给外部处理器，支持大小写 HTTP(S) 和 mailto 等，不再拼入仓库文件地址；协议相对 URL 补 https。
- 相对文件链接通过 HttpUrl 保留百分号编码、查询参数、锚点，避免 %20 被二次编码与 ?raw=1 丢失。
- 空目标、仅查询、仅锚点保留当前文件；相对父目录解析限制在仓库根内，不吞掉 owner/ref。
- GithubWebUrlsTest 16 项通过（iteration-markdown-urls.log），新增四组协议、编码、当前文件和父目录边界测试。
- 本轮主代码及测试编译成功，尚未重新打包；前轮设备验证证明点击回调可达，本轮 URL 结果由纯函数测试覆盖。

### 2026-09-13 Nothing 详情元数据布局

- GithubMetadataRow 在 Nothing 模式移除装饰图标底块，使用标签/值两列；按可用宽度与字号判断，窄屏和大字号改为上下排列。
- 默认值文本不再单行省略；Release、提交、Issue、PR、Actions 等共享该布局。
- compileDebugKotlin 与 assembleDebug 成功（iteration-metadata-compile.log / iteration-metadata-build.log）。
- API 35 Release 样例正常字号与 1.5 倍字号截图检查通过：metadata-normal.png / metadata-large.png。设备字号恢复为 1.0。
- 验证覆盖共享组件的 Release 用法，其余详情页的复杂自定义 valueContent 仍需单独验收。

### 2026-09-13 全量回归与页面证据清单

- :app:testDebugUnitTest 全量 327 项测试通过，零失败（iteration-regression-audit.log 与当前 TEST-*.xml）。
- :app:lintDebug 仍未通过：90 个错误、105 个警告；错误准确归类为 89 个 CustomColorSchemeGenerator RestrictedApi 和 1 个 Manifest 删除节点 MissingClass，没有新的其他类别错误。
- 新增 PAGE_AUDIT.md，按当前 feature 页面及路由列出设备证据与缺口，避免把局部截图、样例回调或 JVM 测试扩展为完整功能验收。
- 发现 CopilotPlaceholderScreen 无导航调用，记录为未使用占位页，不声称已有 Copilot 支持。
- 下一批优先账号/创建表单、Actions 与 PR 确定性样例，继续保留整体目标及所有页面验收范围。

### 2026-09-13 创建 Issue 草稿与提交状态

- CreateIssueViewModel 使用 SavedStateHandle 保存标题和正文，Factory 从导航 CreationExtras 获取句柄；成功提交后移除保存的草稿，失败保留。
- 提交期间编辑框只读，ViewModel 同时拒绝编辑动作，避免提交成功跳转时丢失请求发出后的新输入。
- 编辑页面增加 IME 避让，便于键盘弹出后滚动访问表单操作。
- IssuesViewModelTest 15 项通过（iteration-issue-draft-tests.log），新增草稿句柄恢复/成功清理、提交锁定/失败保留两项回归。
- 主代码与测试编译成功；真正的 Android 进程回收恢复和键盘设备验收仍未完成，不能用句柄单测代替。

### 2026-09-13 账号管理布局与确认恢复

- 账号名称允许两行，完整显示用户名和当前账号文案；Surface 使用选中参数表达账号状态。
- 移除确认按账号 ID 保存并从当前账号列表解析，避免持有过期对象；不对当前账号显示移除确认，忙碌时禁用确认提交。
- 新增 debug accounts 样例。assembleDebug 成功（iteration-accounts-build.log），API 35 1.5 倍字号截图 accounts-large.png 确认长账号标识与操作布局。
- 对样例账号 2 打开移除确认，旋转横屏后 accounts-confirm-rotation.xml 仍指向账号 2，并包含 Remove/Cancel。未执行真实账号移除。
- 模拟器恢复字号 1.0、自动旋转 1、user_rotation 0。真实多账号切换/移除与浅色布局仍需后续验证。

### 2026-09-13 PR 评审阅读与提交保护

- 评审评论差异超过四行时显示展开/收起；使用等宽字体、横向滚动和文本选择，保留完整 diffHunk。展开状态按 comment.id 保存。
- 评审提交期间输入框只读，ViewModel 拒绝 ReviewBodyChanged，避免成功清空提交后的新输入。
- PullRequestsViewModelTest 11 项通过（iteration-pr-review-tests.log）；扩展评审提交用例确认请求正文保持原内容、成功后清空。
- 主代码及测试编译通过。长差异展开、横向滚动与旋转尚需设备样例验收；本轮未重新打包。

### 2026-09-13 PR 设备验收与作者信息间距

- API 35 debug 样例已验证长差异展开、横向阅读、旋转后展开状态保留，以及 1.5 倍字体表单/错误提示/三个评审操作的完整布局。
- 三个按钮分别反馈 APPROVE、REQUEST_CHANGES、COMMENT；仅为本地回调验证，未提交远端评审。
- 修正共用 GithubUserMetadataLine 中作者与时间粘连：使用 6dp 水平间距、4dp 换行间距，去除文案边缘空格并允许元数据换行。
- review-spacing.png 已确认作者与时间间隔；浅色样例已启动，完整浅色表单仍需验收。
- iteration-metadata-review.log：assembleDebug 与全量 testDebugUnitTest 成功，329 项测试、0 失败。已安装新 APK。未重跑 Lint，既有 Lint 错误仍待处理。
- 模拟器字号恢复 1.0，自动旋转恢复 1、user_rotation 恢复 0。

### 2026-09-13 Actions 工作流布局与触发草稿

- 工作流名称/完整路径和状态/开关/运行操作分行，避免多控件挤压名称。路径允许换行，名称区仍可进入运行列表。
- 触发分支和参数草稿提升至工作流行，按 workflow.id 使用 rememberSaveable 保存；关闭重开与旋转后保留，便于请求失败后重试。
- 新增 debug actions 样例；API 35、1.5 倍字号下验证长标题/路径/错误状态可读，取消重开并旋转后 target=arm64 仍保留。
- 日志从横向阅读切换为换行，log-wrap.png 确认可完整阅读宽行；未执行真实 workflow dispatch。
- 首次打包因既有 classes.dex 文件锁失败，终止后重试成功（iteration-actions-layout-retry.log），新 APK 已安装。本轮仅修改 Compose 状态/布局，未重跑全量单测；最近全量记录仍为 329 项通过。
- 字号和旋转设置已恢复。运行详情、步骤列表、浅色 Actions 与真实请求结果仍需后续验收。

### 2026-09-13 Actions 运行详情与 Release 排除检查

- 步骤名称独占内容宽度，状态移至下一行；编号使用最小宽度避免大字号固定宽度裁切。
- GithubUserLink 新增可选 maxLines，运行摘要明确请求完整用户名，其他调用保持默认。
- API 35 深色 1.5 倍字号验证运行摘要长元数据、三个步骤与状态；浅色正常字号运行摘要已检查。RERUN 样例本地回调已确认，未执行远端操作。
- assembleDebug 成功，新 APK 已安装；assembleRelease 成功（iteration-release-exclusion.log）。检查 arm64 Release APK 清单、DEX 定义包列表和 R8 mapping，没有 DesignAuditActivity 或 takagi.ru.monica.debug；未安装 Release 包。
- 字号恢复 1.0。完整浅色步骤截图被样例反馈遮挡末项，不能作为完整验收；其他路由横屏、创建表单进程恢复、Lint 仍待处理。

### 2026-09-13 配色公开 API 与 Lint 错误清理

- 自定义配色由 Material Android 内部 color.utilities 切换至公开的 com.materialkolor:material-color-utilities:2.0.2，保留五组种子色与完整动态颜色角色。该 Kotlin 实现源码已检查，构造器和角色 API 对应现有生成器。
- ProfileInstallReceiver 为 tools:node=remove 合并标记，不代表待实例化组件；仅在此标记添加 MissingClass 定点忽略和说明。未添加全局 Lint 基线或 RestrictedApi 抑制。
- 新增黑、白、红、绿、蓝、紫种子在深浅模式的文字对比度与表面亮度检查；覆盖主要文字角色，非所有色彩组合或设备视觉验收。
- iteration-color-lint.log：全量 330 项单测通过，lintDebug 通过，0 错误、106 警告。旧 90 项错误已清除；警告主要为未使用资源、复数候选、KTX 与依赖更新提示，尚未逐项处理。
- iteration-color-apk.log：assembleDebug 成功。Nothing 固定黑白主题不调用此自定义生成器；本轮新 APK 未安装，未做自定义配色的设备视觉验收。

### 2026-09-13 创建 Issue 键盘留白与进程恢复

- 新增 create-issue debug 样例，复用实际 CreateIssueScreen、CreateIssueViewModel.Factory 和 Android 默认 CreationExtras。仓库代理拒绝调用，Submit 被拦截，不产生远端 Issue。
- 设备发现 Scaffold safeDrawing padding 与额外 imePadding 重复，键盘出现后可视区域过小。应用 padding 后 consumeWindowInsets，再叠加 imePadding，避免重复计算。
- API 35 create-keyboard-fixed.png 确认正文框与创建按钮完整位于键盘上方；create-typed.xml 确认标题和正文分别正确输入。
- Home 后 am kill，原进程 21292 消失；重新带回原任务后新进程 21423，create-restored.xml 中 Restore title 和 Body draft survives process death 均恢复。
- 此证据覆盖 Activity SavedStateRegistry + 实际 ViewModel 工厂；不等价于已验证真实登录导航栈的恢复，也不验证远端创建。
- iteration-draft-insets.log：assembleDebug 成功，APK 已安装。此轮未重跑单元测试/Lint，最近记录为 330 项通过、Lint 0 错误。

### 2026-09-13 Webhook 信息阅读与管理入口

- 明确当前能力为只读列表，编辑与投递管理仍使用 GitHub 网页；修正 PAGE_AUDIT 中原先暗示存在原生表单的描述。
- 每项显示 Webhook ID 以区分 API 常见同名 web 项，订阅事件与最后响应不再截断；响应可选择复制，HTTP >=400 使用错误色。
- 每项增加对应 settings/hooks/{id} 的 GitHub 管理入口，使用集中 URL 构建器。
- API 35 深色 1.5 倍字体 webhooks-large.png 确认两个同名项、长事件和长 503 响应完整显示。hook-108-target.xml 确认回调指向 https://github.com/sample/android-client/settings/hooks/108；未打开真实权限页面。
- iteration-webhooks.log assembleDebug 成功，APK 已安装。字号恢复 1.0。浅色、权限失败、真实页面目标可达性仍需验证。

### 2026-09-13 Webhook URL 回归

- 新增 individualWebhookUrlIncludesId 回归，确保每个 Webhook 的 GitHub 管理链接带上唯一 ID。
- testDebugUnitTest 成功，331 项测试、0 失败（iteration-webhook-tests.log）。

### 2026-09-13 Webhook 与文件浏览审计补充

- 重新核对 RepositoryFilesScreen：二进制/过大文件均提供明确 GitHub 回退入口，文本文件保留等宽字体、选择复制和双向滚动，Markdown 使用仓库 ref/path 解析链接；分支/标签选择器独立于目录列表并显示加载/错误状态。
- 当前未改动文件浏览代码，因为现有分支已经覆盖这些状态；后续需要设备长文件和真实分支切换验收，而不是重复添加样式。

### 2026-09-13 仓库文件长名称

- RepositoryContentRow 文件名由单行省略改为最多两行，长文件在 Nothing 紧凑列表中可辨识；文件大小仍显示在下方。
- iteration-files-longname.log：testDebugUnitTest 与 assembleDebug 成功，331 项测试、0 失败。

### 2026-09-13 Explore 翻页入口复核

- ExploreFlipMode 已由 ExploreScreen 的仓库搜索工具栏直接接入，flipMode 使用 rememberSaveable，切换搜索 scope 时仅仓库结果显示翻页入口；代码路径不是孤立占位。
- 翻页模式包含首屏骨架、空/错误/重试、接近末页自动加载、页码动效和仓库点击导航。本轮未重复改动，仍需设备长结果和横屏验收。

### 2026-09-13 标签分配布局复核

- 标签编辑底部表单使用 LazyColumn 限高并保留分页状态，Checkbox 与标签内容按行排列；长标签通过水平滚动保留完整名称，描述最多两行，保存期间整行禁用。
- 该布局适合标签本身不可拆分的 Nothing 胶囊样式；本轮未改动代码，设备多选和混合状态仍需实际交互验收。

### 2026-09-13 分支/标签选择器长名称

- 当前 ref 按钮和底部选择列表均支持最多两行，按钮限制最大宽度，避免长分支名挤压箭头或错误提示。
- iteration-ref-picker.log：testDebugUnitTest 与 assembleDebug 成功；最近全量测试仍为 331 项、0 失败。

### 2026-09-13 仓库详情紧凑统计复核

- RepositoryDetail 的星标/派生/议题等内联统计保持单行，是刻意的紧凑数字指标；长文本字段（仓库名、描述、主题、文件名）使用独立换行或滚动容器，未发现新的截断缺陷。
- git diff --check 无空白错误；仓库文件长名称改动已包含在最近成功的 assembleDebug 和 331 项测试记录中。

### 2026-09-13 验收环境一致性

- 模拟器恢复为 font_scale=1.0、accelerometer_rotation=1、user_rotation=0。
- 当前 arm64 Debug APK 为 Etoile-Android-arm64-v8a-0.1.0-26091301-30.APK（约 6.2MB），对应最近文件列表、Actions、创建 Issue 和 Webhook 改动。
- 工作区保留既有迭代改动，未执行 reset/clean/commit；本轮未产生临时环境改动。

### 2026-09-13 二进制/超大文件识别

- GithubMessageState 新增可选 description；RepositoryFilesScreen 在二进制和超大文件状态显示完整仓库路径，用户能确认当前文件后再打开 GitHub。
- 首次构建再次遇到既有 classes.dex 文件锁，重试成功（iteration-binary-context-retry.log）；testDebugUnitTest 与 assembleDebug 成功，测试保持 331 项、0 失败。

### 2026-09-13 Inbox 取消订阅确认与大字体验收

- 确认框仅保存通知 ID，并从当前列表重新解析；通知消失或要求登录时清除目标，避免继续使用旧对象。rememberSaveable 保留旋转状态，triage 期间禁用确认。
- debug inbox 样例补上 systemBarsPadding。API 35、1.5 倍字体 inbox-large.png 验证菜单可见；inbox-menu.xml 确认 Done/Unsubscribe；inbox-confirm-rotation.xml 确认旋转后仍指向通知 (1)。未执行远端取消订阅。
- 发现仓库名在 Nothing 大字号下等宽粗体过于突出，待调整通知信息层级；当前长标题仍两行省略，不据此宣称完整长标题可读。
- assembleDebug 重试成功（iteration-inbox-confirm-retry.log），首轮为既有 dex 文件锁。新 APK 已安装。字号恢复 1.0、自动旋转 1、user_rotation 0。

### 2026-09-13 Inbox 信息层级调整

- 仓库名从 labelLarge/primary 调整为 bodySmall/onSurfaceVariant，并增加 6dp 顶部间距，让通知标题保持主视觉，同时保留长仓库标识。
- API 35、1.5 倍字号重新启动 inbox 样例确认标题、仓库、类型、时间和三点菜单层级清晰（inbox-hierarchy.png）；此前 action-error 仍可在行内显示。
- iteration-inbox-hierarchy.log assembleDebug 成功，APK 已安装；字号恢复 1.0。真实 Inbox 数据和横屏仍需后续验收。

### 2026-09-13 Actions 开关可访问性

- 工作流开关增加本地化语义描述，包含工作流名称，屏幕阅读器能区分不同工作流的开关。
- 补齐 semantics/contentDescription 导入；第二次构建遭遇既有 dex 文件锁，最终重试 assembleDebug 成功（iteration-actions-a11y-final.log）。

### 2026-09-13 Webhook 刷新重试状态修复

- RepositoryWebhooksViewModel 新增 failedPage，刷新失败后 Retry 重新请求第一页并保留旧列表直到新结果成功；分页失败则重试相同页并保留已加载项目。
- 刷新进行中 canLoadMore 明确为 false，避免并发分页取消刷新。
- 新增三项失败刷新/分页/并发回归，Webhook 专项测试 4 项通过（iteration-webhook-retry-final2.log）。

### 2026-09-13 Webhook 修复后全量回归

- Webhook 刷新/分页重试状态修复后执行全量 testDebugUnitTest：334 项测试、0 失败（iteration-full-regression.log）。
- 本轮未重新运行设备样例；模拟器此前已恢复 font_scale=1.0、自动旋转和 user_rotation=0。

### 2026-09-13 Inbox 长标题可读性

- 通知标题最多显示三行，保留更多上下文；仓库、类型与时间继续使用辅助层级，列表间距不变。
- testDebugUnitTest 成功，334 项测试、0 失败（iteration-inbox-title.log）。

### 2026-09-13 Explore 翻页卡片长名称

- ExploreFlipCard 仓库 fullName 增加最多三行和省略，避免极端名称无限撑高内容区，同时比原先无上限布局保留更多辨识信息。
- testDebugUnitTest 成功，334 项测试、0 失败（iteration-explore-title.log）。

### 2026-09-13 公共用户简介长度

- PublicUserProfileScreen 简介限制最多六行并在超长时省略，防止极端个人简介撑满统计和仓库入口；正常简介完整显示。
- testDebugUnitTest 成功，334 项测试、0 失败（iteration-profile-bio-retry.log）。

### 2026-09-13 全量质量回归

- 最近 Profile 简介、Inbox 标题/确认、Webhook 重试、仓库文件/ref 和 Actions 改动合并后执行 testDebugUnitTest + lintDebug。
- iteration-quality-regression.log：334 项测试、0 失败，Lint 0 错误（警告仍为既有 106 项左右）。

### 2026-09-13 当前账号简介长度

- ProfileScreen 当前账号简介限制最多六行并在超长时省略，避免极端个人简介推离统计区；与 PublicUserProfileScreen 行为一致。
- testDebugUnitTest 成功，334 项测试、0 失败（iteration-profile-bio-main.log）。

### 2026-09-13 最新 Debug 构建

- git diff --check 无格式错误（仅报告现有文件换行符提示）。
- iteration-final-assemble.log：assembleDebug 成功，最新 arm64 APK 已生成；覆盖最近 Profile 简介、Inbox 标题/确认、Webhook 重试、Actions、仓库文件/ref 等改动。

### 2026-09-13 最终测试复核

- iteration-final-tests.log：最近全部代码改动完成后，testDebugUnitTest 成功，334 项测试、0 失败。

### 2026-09-13 仓库文件行可访问性

- RepositoryContentRow 增加完整路径、文件/目录类型和文件大小的 contentDescription，屏幕阅读器可区分同名文件和目录。
- testDebugUnitTest 成功，334 项测试、0 失败（iteration-files-a11y.log）。
