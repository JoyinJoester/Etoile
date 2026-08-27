# Etoile 发布签名

Release 签名材料只保存在构建机器或 CI 的安全存储中，仓库只保留配置说明。

## 本地配置

在仓库根目录创建被 Git 忽略的 `keystore.properties`：

```properties
storeFile=keystore/etoile-release.jks
storePassword=...
keyAlias=etoile-release
keyPassword=...
```

`storeFile` 使用绝对路径，或使用相对于仓库根目录的路径。四个字段必须同时存在；字段缺失时 Release 保持未签名状态，Gradle 不会使用 Android debug 证书。

也可以使用环境变量：

```text
ETOILE_RELEASE_STORE_FILE
ETOILE_RELEASE_STORE_PASSWORD
ETOILE_RELEASE_KEY_ALIAS
ETOILE_RELEASE_KEY_PASSWORD
```

## 验证签名契约

只检查配置、不生成安装包：

```text
gradlew.bat :app:verifyReleaseSigningConfiguration
gradlew.bat :app:verifyReleaseSigningConfiguration -PrequireReleaseSigning=true
```

CI 发布任务应使用第二条命令，让缺少签名材料的任务立即失败。签名证书变更会使旧证书安装包无法原地升级，发布前应保留旧证书或安排一次迁移版本。
