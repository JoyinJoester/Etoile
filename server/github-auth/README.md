# Etoile GitHub App 授权服务

Node.js 22+，无第三方运行依赖。用于原生客户端的授权码交换和刷新，密钥仅由服务端环境变量提供。当前没有部署到公网。

运行测试：`npm test`。配置 `GITHUB_APP_CLIENT_ID`、`GITHUB_APP_CLIENT_SECRET` 后执行 `npm start`。默认监听 `127.0.0.1:8787`，可通过 `HOST` / `PORT` 设置。不要将密钥写入版本库、镜像或 APK。

部署时在前面配置 HTTPS 反向代理，限制请求体 4 KB，禁止记录请求体和授权响应，关闭响应缓存。保持 GitHub App 的令牌过期选项开启。默认按照直连 IP 每分钟限制 30 次，不信任客户端传来的转发 IP；多实例或代理部署需在入口额外配置按真实来源限流。

- `POST /v1/exchange`：JSON 包含 `code` 和 `code_verifier`。
- `POST /v1/refresh`：JSON 包含 `refresh_token`。
- `GET /health`：服务健康状态。

客户端在浏览器授权时生成 state 和 PKCE S256 challenge，在 `etoile://oauth` 回调中先验证 state，再将 code 和 verifier 通过 HTTPS 发到服务。服务固定 GitHub 域名、Client ID 和回调地址，不接受客户端覆盖。令牌仅返回给请求客户端，不在服务端存储。

成功响应包含 `access_token`、`refresh_token`、`expires_in`、`refresh_token_expires_in`、`token_type`、`scope`。客户端必须加密保存并原子更新两个令牌。授权被拒绝返回 401；限流 429；GitHub 或交换失败 502。错误信息不包含 GitHub 的原始响应。

此服务不代替安装授权：仓库访问范围仍由 GitHub App 安装、用户自身权限和组织策略决定。Android 端服务地址配置和接入尚待完成。
