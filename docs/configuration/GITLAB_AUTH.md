# GitLab 登录

入口：GitHub 登录页的「使用 GitLab 登录」，或账号管理页的「GitLab 账号」。支持同时保存 GitHub 和 GitLab 账号，以及多个 GitLab 实例的账号。实例地址必须使用 HTTPS，可以包含自建实例的安装子路径。

## 个人访问令牌

在实例中创建 Personal Access Token，浏览项目请授予 `read_api` 权限；创建或修改 Issue、合并请求和执行 CI 操作需要 `api`，同时受项目角色和实例策略限制。只有 `read_user` 的旧令牌仍可验证账号，但不能浏览私有项目和仓库。填写实例基础地址和令牌后登录。客户端使用该实例的 `GET /api/v4/user` 验证身份，成功后才保存凭据。失败时不会覆盖已保存的账号。

令牌经 Android Keystore 支撑的加密偏好保存，与 GitHub 凭据使用独立存储。账号键包含实例基础地址和用户 ID，避免不同实例的相同 ID 相互覆盖。移除账号会二次确认，仅移除本机凭据；需要撤销令牌时，请前往对应 GitLab 实例的访问令牌设置。

## 浏览器 OAuth

在 GitLab 用户设置的 Applications 中注册应用：

- Redirect URI：`etoile://gitlab-oauth`
- 关闭 **Confidential**，使用公开客户端。
- Scope：读取使用 `read_user` 和 `read_api`。需要协作写入时，在 GitLab 注册应用时还需允许 `api`。

在应用登录页填写该实例注册的公开 Application ID。无需填写 Secret，客户端也不读取或保存 Secret。每个自建实例需要在该实例上注册应用。

默认关闭「启用协作写入」，浏览器只申请 `read_user read_api`；用户明确开启后才申请 `read_user read_api api`。`api` 是 GitLab 的广泛 API 权限，可能修改项目或运行 CI，并非只授予某个按钮。授权进行中不能更改此开关。旧只读账号需要重新发起授权，刷新令牌不能提升 scope；PAT 账号需使用具有 `api` 的令牌重新登录。

分发者可以在不提交的 `local.properties` 配置 GitLab.com 应用的默认公开 ID：

```properties
gitLabOAuthClientId=你的公开ApplicationID
```

也支持 Gradle 同名属性和环境变量 `ETOILE_GITLAB_OAUTH_CLIENT_ID`。没有配置默认 ID 时，用户仍可在登录页填写 ID，或使用个人访问令牌登录。

OAuth 使用系统浏览器、随机 state 和 PKCE S256。state、verifier 和实例地址加密保存在本机，支持浏览器跳转期间的进程重建。回调有效期十分钟，拒绝重复参数、错误 state、错误目标及重放；有效回调只消费一次。授权码交换和后续刷新只向绑定的实例发送，不跟随重定向。GitLab OAuth 令牌过期时，「选择并验证」和项目 API 读取都会串行刷新令牌，先保存新的 refresh token，再继续请求。刷新不会扩大 scope，原先只有 `read_user` 的账号需要在 GitLab 应用设置中允许 `read_api`，然后重新走浏览器授权；也可用具有 `read_api` 的 PAT 重新登录。

用户取消登录会清除待处理授权。自定义 URI scheme 的归属无法由 Android 验证，PKCE 用于防止仅截获授权码的应用兑换令牌。

官方依据：[GitLab OAuth 2.0 API](https://docs.gitlab.com/api/oauth2/)、[当前用户 API](https://docs.gitlab.com/api/users/#retrieve-the-current-user)。

## 当前范围

已接入登录、身份验证、多实例账号管理、OAuth 刷新与加密保存，以及原生项目浏览、Issue、合并请求、流水线和作业。选择账号后点击「我的 GitLab 项目」进入项目详情，再选择协作入口；需要切换账号时先点击「选择并验证」。范围及边界见 [GITLAB_PROJECTS.md](GITLAB_PROJECTS.md) 和 [GITLAB_WORKFLOWS.md](GITLAB_WORKFLOWS.md)。GitHub 与 GitLab 使用独立的账号和 API，GitLab 账号不会传入 GitHub 请求。通知和高级管理能力尚未适配。

业务写请求和 OAuth 授权码交换、刷新使用一次性请求体，并关闭自动连接重试及重定向。断连、响应解析失败或服务端提示立即重试时，不自动重发；先检查远端结果，再由用户决定是否再次提交。

自动化测试使用本机 MockWebServer，不使用真实令牌或修改远端资源。正式浏览器授权仍需配置实际 Application ID，并由用户在对应实例完成登录验收。
