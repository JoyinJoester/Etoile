# GitLab 项目浏览

从登录页或账号管理进入「GitLab 账号」，选择并验证账号，再点击「我的 GitLab 项目」。支持 GitLab.com 和 HTTPS 自建实例，包括安装子路径。GitHub 与 GitLab 账号可同时保存，导航和 API 互不混用。

## 功能

- 我的项目：读取 `GET /api/v4/projects?membership=true`，按最近活动排序，每页 30 项；输入搜索词后点击搜索或键盘搜索，匹配由 GitLab 服务端完成。仅展示参与的项目，不是全站公开项目搜索。
- 项目详情：名称、命名空间路径、说明、可见性、归档状态、星标与派生数。
- 仓库目录：默认分支进入，支持嵌套目录、分页、返回上级和刷新。空项目无需请求不存在的默认分支。
- 版本输入：可输入分支、标签或完整提交 SHA。先通过 Commits API 解析为不可变 SHA，后续目录和文件始终读取该 SHA；刷新时重新解析所选版本。
- 文件预览：Files API 的 Base64 内容在本机解码，仅展示 UTF-8 文本，最大 512 KiB；显示最多 5,000 行、每行 20,000 字符，截断时明确提示。支持逐行选择复制和横向滚动。二进制、不合法 UTF-8 和过大文件显示不支持，不请求外部下载链接。

项目详情现已提供 Issue、合并请求和流水线入口，范围见 [GITLAB_WORKFLOWS.md](GITLAB_WORKFLOWS.md)。项目浏览本身只读；代码修改、项目管理、README 富文本、子模块跳转和分支/标签列表选择器尚未接入，通过版本输入框指定 ref。

## 授权与隔离

推荐 PAT 使用 `read_api`，OAuth 应用允许 `read_user` 和 `read_api`。旧的 `read_user` 登录只验证身份，不能自动提升权限；API 返回 401/403 时提供重新登录和权限提示，原有账号不会被错误覆盖。配置见 [GITLAB_AUTH.md](GITLAB_AUTH.md)。

请求从 data 层取得当前活动账号的凭据，必要时串行刷新 OAuth 令牌。令牌不会进入页面状态、导航参数或 SavedState。实例与账号键不匹配时不发送请求；独立客户端移除 GitHub 拦截器、Cookie、HTTP 缓存和 authenticator，不跟随重定向。项目 ID 使用数字，文件路径和 ref 各自作为单个路径段编码；分页只接受相邻的整数页码，绝不请求 Link 指向的 URL。

切换/移除 GitLab 账号后，浏览与协作 ViewModel 都清空数据、取消旧请求，并拒绝迟到响应。详情路由始终携带原账号键，不会将旧项目 ID 送给新实例。只读结果保存在当前 ViewModel 内存中，没有磁盘缓存或离线回退。GitLab 页面不显示 GitHub 的速率限制/缓存提示。

## 验证

`GitLabProjectsRepositoryImplTest` 使用 MockWebServer 验证实例子路径、PAT/Bearer、搜索/分页、路径编码、固定 SHA、重定向拦截、权限错误、文本预览限制，以及过期 OAuth 刷新与移除账号后拒绝请求。`GitLabProjectsViewModelsTest` 验证搜索重置、去重、分页失败保留与重试、切换版本、目录返回、空项目、文件失败和账号切换迟到响应。既有 GitLab 登录、回调和浏览器策略测试一并回归。

自动化验证不使用真实 GitLab 凭据，不修改远端资源。正式浏览器授权仍需实际 Application ID；真实 GitLab.com / 自建实例权限和设备交互尚需联调。未生成 APK。

2026-10-21 项目浏览阶段验证：35 项 GitLab 测试及 11 项共享 UI/分页/领域边界回归，共 46 项通过，0 失败、0 错误、0 跳过。`:app:lintDebug` 通过，0 Error/Fatal、132 Warning（既有代码与现有依赖的版本提示；新增项目浏览源码没有 Warning）。日志：`.codex-tmp/gitlab-projects-final.log`。后续协作功能已完成全量 867 项单元测试及 Lint 验证，最新验收见 [GITLAB_WORKFLOWS.md](GITLAB_WORKFLOWS.md)。未运行 APK 打包任务。
