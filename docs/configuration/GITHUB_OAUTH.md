# GitHub OAuth Device Flow 配置

Etoile 的首选登录方式是 GitHub OAuth Device Flow。该流程只需要 OAuth App 的公开 Client ID，不需要也不得在客户端中放置 Client Secret。

## GitHub OAuth App

1. 在 GitHub Developer Settings 创建 OAuth App。
2. 在 OAuth App 设置中启用 Device Flow。
3. 将 Client ID 通过以下任一方式提供给 Gradle：

```powershell
$env:ETOILE_GITHUB_OAUTH_CLIENT_ID='你的 Client ID'
```

或写入本机、不提交版本库的 `local.properties`：

```properties
githubOAuthClientId=你的 Client ID
```

未配置 Client ID 时，应用不会发送 Device Flow 请求，登录弹层只显示手动访问令牌备用入口。

## 安全边界

- Client ID 会进入 `BuildConfig`，它不是秘密；Client Secret 永远不能进入 Android 客户端。
- Device code 仅在 ViewModel 内存中短期保存，不写入日志、缓存或偏好设置。
- OAuth access token 必须先通过 GitHub `/user` 验证，成功后才写入加密令牌存储。
- 轮询遵守 GitHub 返回的 `interval`；`slow_down` 会在后续请求中额外增加 5 秒。
- 默认请求 `repo notifications read:user`，用于私有仓库、通知和用户资料功能。
