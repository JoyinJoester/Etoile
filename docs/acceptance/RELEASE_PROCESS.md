# GitHub Releases 发布准备

主渠道为 GitHub Releases。稳定版必须先逐项完成 [客户端验收](CLIENT_ACCEPTANCE.md)；CI 通过不等于正式发行验收通过。

## 可重复的源码检查

准备 JDK 21、Node.js 22、Rust 1.86.0、cargo-ndk 4.1.2、Android SDK 37、NDK 28.2.13676358，以及 aarch64-linux-android / armv7-linux-androideabi Rust 目标。SDK 路径使用 ANDROID_HOME 或本地 local.properties；Gradle Wrapper 固定版本。仓库 workflow 执行相同脚本，保留两个构建变体的测试与 Lint 报告。

```powershell
./scripts/verify-client.ps1
```

脚本只编译源码、执行主机测试和静态检查，不生成或安装 APK。当前 CI 配置的远端执行结果需在实际推送后确认。

脚本还会导出实际解析的 Release Maven 依赖清单。Python 3.11+ 可运行 `python scripts/check-dependency-advisories.py`，将公开依赖坐标与 Cargo.lock 里的 registry 包版本提交给 OSV 查询已知公告；不会读取账号配置。报告保存于 app/build/reports/acceptance。结果不涵盖构建工具、操作系统、未收录漏洞和实际可达性分析；网络失败时不能记为通过。CI 也运行此检查。

## 第三方许可证更新

构建会检查 `docs/legal/dependencies.json` 是否覆盖实际解析的 Release Maven 模块，以及 Cargo.lock 是否变化，并校验归档声明文本。更新依赖后，先生成上述依赖清单，再运行：

```powershell
python scripts/generate-dependency-notices.py --gradle-cache D:/GradleRepository
```

将缓存目录替换为当前 GRADLE_USER_HOME（默认 ~/.gradle 时可省略参数）；Cargo 源码默认取 CARGO_HOME/registry/src。生成器读取公共构建缓存、缺失时从 Google Maven / Maven Central 获取公开 POM，收集继承的许可声明、已发布 JAR/AAR 的 license/notice 和 Cargo 包自带的声明。遇到未审查许可证或缺失信息会停止，需要核对上游，而非假定兼容。不要使用私人账号或签名配置作为输入。

审查并保留 `docs/legal/dependencies.json`、`dependencies.md`、`dependency-texts/` 和 `licenses/`；应用会把清单和全文组合进离线声明。Maven 图含平台元数据，Cargo 清单含构建及目标专属包，是保守超集，不等同于每个包都进入 Android 二进制。JNI macro crate 未附带的许可声明来自其 `.cargo_vcs_info.json` 指向的固定上游提交；cesu8 自带的版权声明与统一 Apache 2.0 文本一并保留。此自动化核对不能代替对新许可证条款和发行方式的人工兼容性判断。

公开发行前，在发布环境额外运行：

```powershell
./scripts/verify-client.ps1 -PublicRelease
```

该开关检查自有 GitHub OAuth Client ID，并要求外部正式签名配置。Release 固定不嵌入 OAuth Secret；普通源码检查和所有 Release 的 preReleaseBuild 也执行该安全约束，Debug 可单独使用本机 Secret。不打印凭据内容，不签名或发布。Device Flow 需在自有 OAuth App 启用；可选 GitHub App 浏览器登录还需部署并实测授权服务。GitLab.com 可配置默认公开 Application ID，自建实例允许用户填写对应 ID 或 PAT。

每次正式升级用递增的 `-PappVersionCode=整数`，并保留同一应用 ID 和签名证书。参数应高于所有已公开版本；没有配置时仍为开发基线 1。版本名称、发布标签和代码来源需一起记录，不能靠输出文件名代替 Android versionCode。

## 发布记录

正式打包授权后补充：源码 commit、依赖与工具链、版本号、签名证书指纹（仅公钥）、两个 ARM ABI 的安装/启动记录、旧版原地升级记录、APK SHA-256、变更说明、已知限制、隐私和 GPL 源码链接。提供与实际二进制对应的完整源码及构建说明。禁止把私钥、令牌、用户私有日志或本地配置放入附件。

发布前验证 Release R8、JNI、授权回调和安装权限交接。需要回退时，保留上一稳定版资源；降级安装可能需要卸载并丢失本机数据，先说明兼容性，优先以更高 versionCode 修复发布。

## 以后上架的条件

上架前按当时商店政策重新核验 targetSdk、隐私政策公网地址、数据安全表和账户相关声明。商店版本须单独评估 APK 下载/请求安装权限功能及发行政策，不能直接声称当前 GitHub Releases 构建满足 Google Play 要求。公开资料、离线隐私入口、外部签名和递增版本号已独立组织，便于后续适配。
