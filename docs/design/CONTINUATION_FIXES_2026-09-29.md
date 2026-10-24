# 中断工作收尾：2026-09-29

本轮在当前会话直接接手 `D:\Etoile` 的未提交修改，完成热力图、Markdown 图片、仓库管理误触和横向过滚动四项修复。没有向旧会话转发工作。

## 修改

- 贡献日历按 GraphQL 的 `weeks[].contributionDays[]` 解析；覆盖正常响应、部分周、空日历、缺少数据、GraphQL 错误和 HTTP 失败。
- Issue、PR、评论、Discussion、Release、仓库文件和 README 恢复图片渲染。补充 Coil 网络加载及 SVG 模块；根据仓库、分支与文档路径解析相对图片，将 GitHub blob 图片地址转换为原始文件地址。HTML `<img>` 使用 jsoup 解析，预处理跳过代码节点。
- 仓库管理移入右上角入口，在紧凑及宽屏布局共用同一个管理窗口。可见性使用“公开／私人”菜单；可见性、归档和默认分支要求输入准确的仓库全名，ViewModel 同样校验。功能开关增加确认，失败保留输入及错误说明。已归档仓库只允许取消归档；默认分支复用 Git ref 名称校验。
- 使用 `LocalOverscrollFactory provides null` 关闭边缘拉伸，移除吞掉滚动与惯性回调的自定义效果。

## 验证

最终代码执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --console=plain
```

- 单元测试：736 项通过，0 失败，0 错误，0 跳过。
- Lint：0 错误，104 警告。
- 构建：成功，含 ARM64 和 ARMv7 安装包，版本文件名后缀 `26092901-02`。
- 最终构建日志：`.codex-tmp/continue-delivery.log`。
- 模拟器 `emulator-5554`：安装最终 ARM64 APK，检查右上角入口、公开／私人菜单、空输入及错误仓库名禁用保存、取消后保持原状态、确认后更新状态、归档及取消归档、Wiki 确认及取消、403 失败保留输入。
- 图片示例：相对路径 PNG、SVG 徽章、HTML 图片成功显示；热力图横向滑动后可见日期变化。检查了 Material 常规文字及 Nothing 窄屏 1.5 倍文字的窗口和按钮可达性。
- 所有仓库写入交互使用内存示例数据，没有对真实 GitHub 仓库执行管理操作。测试结束时恢复模拟器原有的 840×2100 尺寸及 1.5 倍文字设置。

## 截图

- [公开／私人菜单](continuation-fixes/visibility-menu.png)
- [错误仓库名无法保存](continuation-fixes/name-validation.png)
- [PNG、SVG 和 HTML 图片](continuation-fixes/markdown-images.png)
- [窄屏大字体确认窗口](continuation-fixes/large-text-confirmation.png)

## 验证范围与后续

真实账号权限、GitHub 对可见性变更的组织策略，以及需要额外鉴权的私有图片尚未实测。独立图片请求不会把 GitHub 令牌发送给第三方图片站点。仓库与个人 README 当前按仓库根目录解析相对路径，文件浏览器则使用实际文档路径；位于 `.github/` 或其他目录的默认 README 仍需补充源路径元数据。

额外发现：现有 Markdown 渲染器会隐藏行内代码中的 HTML 标签；本轮预处理器的“不改写代码”测试通过，但这不代表渲染器本身已修复该独立问题。

完整客户端路线图仍有后续开发项目；本记录只描述上述四项的本轮交付。
