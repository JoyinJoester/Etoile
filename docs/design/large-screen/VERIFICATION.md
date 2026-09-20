# 大屏适配与接管验收

2026-09-17 完成。当前交付为调试构建 **11**，保留 Nothing 默认风格，同时支持 M3E 与 Miuix。

## 页面布局

- 主导航按实际窗口宽度切换底部导航、导航轨和带文字的侧栏，短横屏可滚动到最后一个目的地。
- Issue、PR、提交、发布、仓库等集合使用自适应网格。搜索、筛选及分页跨整行；字号放大时减少列数。
- Issue、PR、提交、发布、公开个人页及应用详情按空间切换单栏和双栏，元信息与下载入口放在侧栏。
- 设置、登录和编辑表单限制为 640dp；Markdown 阅读为 840dp，代码阅读可扩展到 1440dp。
- Issue 评论草稿和 PR 当前页签在窗口变化后保留，发布附件入口在两种布局中均可到达。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 单元测试 | 424 项通过，0 失败、0 错误、0 跳过 |
| Android Lint | 0 错误，104 条警告 |
| Release Kotlin 与清单 | 通过，未包含调试样例包 |
| 宽屏页面 | 47 项通过 |
| 窗口、字号与主题组合 | 54 项通过 |
| 交互 | 8 项通过 |
| 两种 ABI 安装包 | DEX 的 SHA-256 相同 |

共 109 项原生检查。宽屏和响应式结果来自构建 09/10；构建 11 修正了调试商店样例的状态保存，
接管后安装 11 并通过来源草稿跨窗口保留、键盘下添加按钮可达及短横屏导航检查。
每项检查的构建版本均保留在 [完整记录](native-validation.json) 中，没有把历史检查标成对 11 的重跑。

原断点来自调试样例使用普通 `remember` 保存商店输入；生产输入由 ViewModel 持有。
样例现使用可保存的草稿状态模拟生产状态寿命，窗口变化后不再丢失测试输入。

模拟器为 Android API 35，420dpi，覆盖 320dp 窄屏、480dp 分屏、800dp 竖屏、1280dp 宽屏、
1600dp 超宽屏和 150% 字号。固定本地数据与拦截的外部操作用于布局和交互验证，未执行真实 GitHub 写入或应用下载安装。
未构建签名 Release APK。构建仍有既有弃用和依赖警告。

## 安装与预览

- [Etoile-Android-armeabi-v7a-0.1.0-26091701-11.APK](../../../app/build/outputs/apk/debug/Etoile-Android-armeabi-v7a-0.1.0-26091701-11.APK)
- [Etoile-Android-arm64-v8a-0.1.0-26091701-11.APK](../../../app/build/outputs/apk/debug/Etoile-Android-arm64-v8a-0.1.0-26091701-11.APK)

[全部设计入口](../m3e-canvas/index.html) · [Issue 模板说明](../ISSUE_TEMPLATES.md)

- [pages-1](pages-1.jpg)
- [pages-2](pages-2.jpg)
- [pages-3](pages-3.jpg)
- [pages-4](pages-4.jpg)
- [responsive-1](responsive-1.jpg)
- [responsive-2](responsive-2.jpg)
- [responsive-3](responsive-3.jpg)
- [themes-1](themes-1.jpg)
- [themes-2](themes-2.jpg)
