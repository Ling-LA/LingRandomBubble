# 0.1.56 验证报告

日期：2026-10-25。versionName 0.1.56；versionCode 57。

## 变更

根据 [LSPosed 审核回复](https://github.com/Xposed-Modules-Repo/submission/issues/2165#issuecomment-6245976794)，将实际 Android applicationId 从 `io.github.ling.randombubble` 迁移为 `io.github.ling_la.randombubble`。配置 Provider authority 改为 `io.github.ling_la.randombubble.config`，由构建的 applicationId 派生；测试 APK 身份和 targetPackage 随之更新。

Java namespace、Activity/Provider 类及 Xposed 入口类保留旧代码命名空间，降低迁移涉及的类加载范围；它们不决定 APK 安装包名。QQ 私有配置、账号气泡库、商城模板的文件名和格式没有改动。独立应用因安装身份变化无法按同包名覆盖或直接读取旧私有目录，README 提供导出导入和重新核对配置的步骤。

内嵌更新工具只允许已经集成新身份且不含旧模块的 QQ。旧身份、双身份或缺失新模块的源 APK 会在写入输出和读取签名材料前被拒绝；首次迁移必须由 LSPatch 重新集成，不能仅将新版 APK 塞入旧路径。

## 已执行检查

- JDK21 / Gradle8.9 / Android SDK35：assembleDebug、assembleDebugAndroidTest、lintDebug 成功。Lint 为0 errors / 29 warnings。
- [117组核心检查](core-tests-0.1.56.txt)通过。
- [16项实际APK与工具检查](package-identity-0.1.56.txt)通过：APK身份/版本、Provider authority、Xposed元数据、权限、Activity入口、Xposed入口DEX类、编译后Provider URI、测试目标/runner DEX类、两个APK签名，以及旧身份/双身份/缺失新身份的更新工具拒绝行为。
- 新增4项Android运行时检查，核对安装身份、Provider注册与设置Activity解析；测试APK已编译，本轮未在设备运行。
- ADB 当前无连接设备；未安装本版到用户手机，未修改用户QQ或独立应用的数据。

验证脚本：`tools/verify-package-identity.py`；需要 JAVA_HOME、ANDROID_HOME、构建的两个APK及本地 `.tools/patch-deps` 开发依赖。脚本检查真实合并Manifest与DEX定义，另用临时合成ZIP检查失败保护，不发送QQ消息。

## 交付与边界

模块：`LingRandomBubble-0.1.56-debug.apk`；测试包：`LingRandomBubble-0.1.56-debug-androidTest.apk`。收录使用的标签格式为 `57-0.1.56`，不覆盖旧版release。

本轮没有新包名的Android运行、真机迁移、LSPatch/LSPosed加载、QQ消息发送及接收端验证。0.1.55的287项Android真机检查与发送记录是历史证据，不作为0.1.56实测结论。QQ配置沿用原存储的设计已核对，跨包名实际迁移保留仍待实机验证。源码与现有配置格式未改，并不保证未经实测的加载环境完全兼容。
