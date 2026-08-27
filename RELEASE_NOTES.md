# 发布说明

Etoile 的逐版本说明与安装包托管在 [GitHub Releases](https://github.com/JoyinJoester/Etoile/releases)，
应用内「检查更新」读取的也是该地址。本文件只记录仓库层面的重大变更，不重复 Release 内容。

## 仓库重置：从 Steam 客户端到 GitHub 客户端

Etoile 最初是从 [Monica Android](https://github.com/Monica-Pass/Monica-for-Android) 中拆出的 Steam 客户端形态。
当前仓库已收敛为 GitHub 第三方客户端，并移除了与之无关的全部历史实现：

- **Steam 功能层**：账号与令牌（`maFile`）、库与商店、好友与聊天、通知、语音、账号统计小组件，
  以及网络解析优化（自建 DNS / DoH / 静态 Hosts）。
- **Monica 密码库模块**：本地密码库、Bitwarden、KeePass、自动填充、附件、Passkey、
  WebDAV / OneDrive 同步、密码生成器、笔记、卡片钱包与回收站。
- **存储引擎**：`mdbx-engine` 模块及其预编译原生库。
- **配套内容**：Room 数据库与实体、上述模块的设置项与多语言文案、其单元测试与仪器化测试、
  相关资源文件、CI 工作流与架构文档。

因此 **1.0.306 及更早版本的说明已不适用于当前代码**，那部分历史记录不再保留在仓库中。
如需查阅 Steam 形态的实现与说明，可回看移除前的提交：

```bash
git show 5a4bbdd:RELEASE_NOTES.md
git show 5a4bbdd --stat
```

## 当前版本线

当前公开测试版的特性范围、已知限制与安全边界见 [README.md](README.md)，
分层与维护约定见 [`docs/architecture/`](docs/architecture/)。
