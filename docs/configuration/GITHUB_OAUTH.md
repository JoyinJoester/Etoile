# GitHub OAuth 配置

Etoile 支持两种登录方式：

1. 配置 Client ID 与 Client Secret 时，使用 OAuth Web Flow。可选系统浏览器（Custom Tabs）或应用内 WebView；用户完成授权后通过 `etoile://oauth` 自动返回应用，无需设备代码。
2. 仅配置 Client ID 时，回退到 GitHub OAuth Device Flow，也提供上述两种登录入口。外置登录先显示可点击复制的代码页面，再由用户打开浏览器；仍需在 GitHub 输入设备代码。

## 浏览器 OAuth（个人构建）

1. 在 GitHub Developer Settings 创建 OAuth App。
2. 将 Authorization callback URL 设置为：

```text
etoile://oauth
```

3. 在本机且不会提交的 `local.properties` 中配置：

```properties
githubOAuthClientId=你的 Client ID
githubOAuthClientSecret=你的 Client Secret
```

也可以使用环境变量：

```powershell
$env:ETOILE_GITHUB_OAUTH_CLIENT_ID='你的 Client ID'
$env:ETOILE_GITHUB_OAUTH_CLIENT_SECRET='你的 Client Secret'
```

Web Flow 使用随机 `state` 与 PKCE S256。短期校验数据存储在 Android 加密偏好中，回调必须严格匹配 `etoile://oauth`，并在十分钟内完成。WebView 不提供 JavaScript Bridge，禁止文件访问、明文与混合内容；只有 GitHub HTTPS 页面会留在容器内。

### 风险说明

Android APK 无法安全保存 Client Secret。即使它来自环境变量或 `local.properties`，最终仍会进入 APK，可能被反编译提取。自定义 URI Scheme 也无法像 HTTPS App Links 一样验证应用归属。选择内嵌 WebView 时，登录页面由应用直接承载；选择系统浏览器可使用其支持的 Passkey 和密码管理器。

因此该模式只适合个人构建、自用安装或明确接受风险的分发。正式公开发行应使用 HTTPS 回调和服务端 Token 交换。

## Device Flow 兜底

只提供 Client ID、不提供 Client Secret 时，Etoile 使用 Device Flow。OAuth App 必须在设置中启用 Device Flow。

未显式配置 Client ID 时，当前构建使用 GitHub CLI 的公开 Client ID 作为 Device Flow 兼容兜底；它不会启用 Web Flow。

## 通用安全边界

- OAuth access token 必须先通过 GitHub `/user` 验证，成功后才写入加密令牌存储。
- Token、Client Secret、OAuth code、state 与 PKCE verifier 不写入日志。
- 默认请求 `repo notifications read:user`，用于私有仓库、通知和用户资料功能。

## 轻量浏览器登录与代码通知

- Custom Tabs 使用已安装浏览器的内核和登录会话；仅依赖 AndroidX Browser，不在 APK 中打包 Chromium。无 Custom Tabs 提供者时尝试普通浏览器；打开失败时显示可恢复的错误并保留应用内入口。
- Passkey 是否可用取决于浏览器、系统凭据提供者和 GitHub 账户是否已设置通行密钥。外置浏览器不会把 Device Flow 自动变成无需代码的 Web Flow。
- 两个入口可切换同一次等待中的授权或设备代码；重新打开浏览器不会重建 OAuth state。外置授权完成后，现有 Activity 回调链路校验 state、有效期和 PKCE，再验证账户并保存令牌。
- Device Flow 的代码页面可点击复制。仅在已有通知权限、应用及频道通知均开启时发布代码通知，不主动申请权限。点击通知通过临时 Activity 复制代码；锁屏公开版本不显示代码。完成、取消或过期会清除通知。
- 无设备代码的 Web Flow 不显示代码页或代码通知。GitHub 的网页 Token 交换仍要求 Client Secret；没有自己的 OAuth App 配置时，不能借用公开 Client ID 跳过这一步。

官方依据：[GitHub OAuth 授权与 Token 参数](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)。当前实现保留个人构建配置方式；尚未部署服务端 Token 交换。

## 本轮验收（2026-09-17，调试构建 15）

430 项单元测试、7 项原生交互检查通过；Lint 0 错误、113 条警告。另验证了真实 Chrome Custom Tabs 启动，未登录真实 GitHub 账户、未提交设备代码，也未实际使用个人 Passkey。

原生检查覆盖：无通知权限时仅显示代码页、不申请权限；有权限时点击通知复制并清除；登录完成后清理；网页 OAuth 不显示代码或通知；重开不生成新授权；浏览器打开失败后可切换内置；320dp / 150% 字号下代码可达。

arm64 调试 APK 为 6,818,396 字节，相比构建 14 增加 22,355 字节。Native 库仍只有原有图形路径与代码阅读器库，没有打包 Chromium。Release Kotlin 和清单通过检查，不包含调试样例。

[完整验收记录](BROWSER_LOGIN_VERIFICATION.json) · [arm64 调试 APK](../../app/build/outputs/apk/debug/Etoile-Android-arm64-v8a-0.1.0-26091701-15.APK)


### OAuth App 注册选项与关注权限

- Redirect URI：`etoile://oauth`，关闭 wildcard matching。
- 建议开启 Enable Device Flow，保留设备验证码授权作为备用。
- 当前实现尚未保存或使用 refresh token，注册时关闭 Expire user access tokens。
- 两种 OAuth 授权流程均请求 `user:follow`。旧凭据不会随 APK 更新获得权限，需要退出并重新授权；个人令牌需自行具备关注写权限。
- 组织资料页使用“在 GitHub 管理关注”入口，应用不对组织调用个人关注接口，也不展示个人贡献日历。


## GitHub App 授权服务模式

在 `local.properties` 配置公开 Client ID 和已经部署的 HTTPS 服务地址：

```properties
githubAppClientId=Iv23litmCSITWQoOAjio
githubAuthServiceUrl=https://你的授权服务域名/
```

服务代码与部署说明见 `server/github-auth/README.md`。地址必须为 HTTPS、以 `/` 结束，不可含账号密码、查询参数或片段。不填写地址时保持旧 OAuth 网页登录；填写后浏览器登录使用 GitHub App，设备代码入口仍保留旧 OAuth 兼容登录。App Secret 不被这两个配置项读取，也不打包到客户端。

GitHub App 回调单独保存待验证 state/PKCE 信息，避免旧登录回调跨配置复用；刷新按加密凭据里的授权来源选择服务。服务端必须配置与客户端一致的 Client ID，并保持令牌过期选项开启。目前未提供公网服务地址，不能声称真实授权已验证。
