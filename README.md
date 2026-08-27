<h1 align="center">Etoile</h1>

<div align="center">

**中文** | [English](README_EN.md)

<img src="image/etoile_launcher.png" alt="Etoile App Icon" width="220" />

<p><strong>专注 GitHub 的独立 Android 客户端</strong></p>
<p>收件箱 · 仓库与代码浏览 · Issue · Pull Request · Actions · Release · Explore</p>

<p>
	友情链接：
	<a href="https://linux.do" title="Linux.do">
		<img src="https://www.google.com/s2/favicons?domain=linux.do&sz=64" alt="Linux.do" width="22" />
		Linux.do
	</a>
	·
	<a href="https://github.com/Monica-Pass/Monica-for-Android" title="Monica Pass">
		Monica Pass
	</a>
</p>

[![Release](https://img.shields.io/github/v/release/JoyinJoester/Etoile?style=flat-square)](https://github.com/JoyinJoester/Etoile/releases)
[![Downloads](https://img.shields.io/github/downloads/JoyinJoester/Etoile/total?style=flat-square)](https://github.com/JoyinJoester/Etoile/releases)
[![Last Commit](https://img.shields.io/github/last-commit/JoyinJoester/Etoile?style=flat-square)](https://github.com/JoyinJoester/Etoile/commits/main)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square)](LICENSE)
[![QQ群](https://img.shields.io/badge/QQ群-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

[![爱发电](https://img.shields.io/badge/爱发电-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)

</div>

<br>

Etoile 是一款独立维护的 GitHub 第三方 Android 客户端，用 Jetpack Compose 与 Material 3 Expressive 构建，
把 GitHub 的日常协作收在一部手机里：分拣通知、读代码、处理 Issue 与 Pull Request、盯 Actions 运行。

> **当前状态：公开测试版。** 接口与布局仍会调整，不是正式版，也不是 GitHub 官方客户端。
>
> 本应用与 GitHub, Inc. / Microsoft 没有隶属、授权或赞助关系。GitHub 及相关商标归其各自权利人所有。

---

## 用户先看

### Etoile 适合谁
- 需要在手机上及时处理 Issue / PR 评审与通知，而不是只有网页版可用。
- 希望原生阅读仓库代码、Actions 日志和 Release，而不是被推进 WebView。
- 有多账号切换需要，或不希望为轻量使用安装完整 GitHub Mobile。

### 你能得到什么
- **收件箱**：通知线程分页、逐条 Done / 取消订阅、未读状态与失败就地提示。
- **仓库与代码**：目录浏览、Branch/Tag 切换、README 渲染、文件原文、Commits、Releases；
  独立的分支列表、协作者权限与 Webhooks 只读页面。
- **Issue**：详情与管理弹层统一收口，支持标题正文编辑、Labels、Assignees、Milestone、
  关闭/重开、会话锁定与 Reaction。
- **Pull Request**：Conversation 与 Diff、行级 Review 评论、请求审阅者、Labels/Assignees/Milestone，
  以及绑定 Head SHA 的合并确认（MERGE / SQUASH / REBASE）。
- **Actions**：Workflow 与 Run 列表、日志、重跑/取消、启停与手动 Dispatch。
- **Explore**：Repositories / Users / Code / Issues / Pull Requests 五类搜索共享防抖、分页与错误状态。
- **个人资料**：公开资料、Followers / Following、仓库与星标（本地分类）。
- **深链**：可识别的 `github.com` Issue、PR、Actions Run/Job 链接进入原生页面，未知路径仍交给浏览器。

### 快速安装

1. 从 [Releases](https://github.com/JoyinJoester/Etoile/releases) 下载与设备架构匹配的 APK。
2. 在 Android 8.0+ 设备安装。
3. 使用 GitHub 设备码流程（Device Flow）登录，凭据保存在本机加密存储中。

### 已知限制
- 仍为公开测试版，接口与布局可能随时调整。
- 依赖 GitHub REST API，受速率限制影响；限流与缓存回退会在界面上明确提示，不会静默展示旧数据。
- 分支保护规则编辑、协作者增删、Webhook 的发送/删除等高风险或有外部副作用的操作，
  仍引导至 GitHub 官方设置页完成，客户端不复制网页权限模型。
- 最终合并、权限变更等结果**以 GitHub 服务端返回为准**。

---

## 数据与安全边界

- 应用 ID：`app.etoile`，数据由 Android 应用沙箱隔离。
- 访问令牌经 GitHub 设备码流程取得，保存在设备端加密存储；不上传到第三方服务器。
- 缓存遵循 ETag / 304 校验，退出登录或切换账号时清理，401/403/4xx 不会展示其他账号的旧数据。
- Webhook 的 URL、secret 等敏感配置不进入客户端模型与界面。
- 本仓库不含遥测或广告 SDK。

### 技术实现
- UI：Jetpack Compose + Material 3 / Material 3 Expressive，手机与平板自适应布局。
- 分层：`feature` → `domain` ← `data`，`domain` 保持纯 Kotlin、可跨平台共享。
- 网络：OkHttp 直连 GitHub REST API，统一分页、认证请求与结构化只读 GET 缓存。
- 状态：Kotlin Coroutines + Flow，`UiState` 不可变并经 `StateFlow` 暴露。
- 安全存储：Android Keystore 支撑的本地加密令牌存储。

---

## 项目由来

Etoile 的代码基座来自 [Monica Android](https://github.com/Monica-Pass/Monica-for-Android)——
一款本地优先的密码管理器——经界面与架构改造而来，沿用了其 Material 3 设计语言、导航与安全组件。
仓库现已收敛到 GitHub 客户端本身：Steam 相关功能层、Monica 密码库模块（Bitwarden、KeePass、
自动填充、附件、Passkey）以及 MDBX 存储引擎均已移除。源码提取基线见 [`SOURCE.md`](./SOURCE.md)。

---

## 赞助支持

如果 Etoile 对你有帮助，欢迎支持持续开发。

<div align="center">
<img src="image/support_author.jpg" alt="Support Etoile" width="320"/>
<br/>
<sub>微信 / 支付宝扫码支持</sub>
<br/><br/>
</div>

也可通过 [爱发电](https://afdian.com/a/JoyinJoester) 或 [Ko-fi](https://ko-fi.com/joyinjoester) 支持。

---

## 开发与构建

### 环境要求
- Android Studio 最新稳定版。
- JDK 17+。
- `compileSdk 35`，`targetSdk 34`，`minSdk 26`（Android 8.0+）。
- 构建基线：AGP `8.7.3`，Kotlin `2.0.21`，Compose BOM `2026.03.00`（以 `gradle/libs.versions.toml` 为准）。

### 常用命令

只跑 JVM 测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

构建安装包：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

注意：`debug` 构建同样开启 `minifyEnabled` 且 `debuggable false`，需要用 release 风格方式调试。

Release 签名通过 `keystore.properties` 或 `ETOILE_RELEASE_*` 环境变量外部提供，请勿提交签名文件。
缺少签名配置时产出显式未签名的 release 包，不会退回调试证书。

### 项目分层（代码现状）
- `takagi/ru/monica/github`：GitHub 客户端全部业务，按 `feature` / `domain` / `data` / `component` / `design` / `navigation` 分层。
- `takagi/ru/monica/data` / `utils` / `ui`：应用级设置、偏好存储、主题与基类 Activity 等共享支撑。
- 包名仍为 `takagi.ru.monica`（沿承自 Monica），`applicationId` 为 `app.etoile`。

### 仓库导航
- [`README_EN.md`](./README_EN.md) — English overview
- [`docs/architecture/GITHUB_MODULES.md`](./docs/architecture/GITHUB_MODULES.md) — 分层、分页与 UI 维护约定
- [`docs/architecture/GITHUB_UI_LAYOUT.md`](./docs/architecture/GITHUB_UI_LAYOUT.md) — 响应式布局约定
- [`docs/configuration/GITHUB_OAUTH.md`](./docs/configuration/GITHUB_OAUTH.md) — OAuth Client ID 配置
- [`docs/release-signing.md`](./docs/release-signing.md) — 外部签名契约
- [`SOURCE.md`](./SOURCE.md) — 从 Monica Android 的提取基线
- [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) — 第三方组件声明

---

## 反馈与支持

- Issue：[Etoile Issues](https://github.com/JoyinJoester/Etoile/issues)
- QQ 群：`1087865010`
- 赞助：[爱发电](https://afdian.com/a/JoyinJoester) · [Ko-fi](https://ko-fi.com/joyinjoester)

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=JoyinJoester/Etoile&type=Date)](https://star-history.com/#JoyinJoester/Etoile&type=Date)

---

## 许可证

Copyright (c) 2025–2026 JoyinJoester

Etoile 基于 [GNU General Public License v3.0](LICENSE) 开源发布。

其他第三方组件的版权与许可证见 [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md)。

GitHub 及相关商标归 GitHub, Inc. / Microsoft 及其权利人所有。本项目为非官方第三方客户端。
