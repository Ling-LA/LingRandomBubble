# 公开依据与致谢

检索日期：2026-09-27。下列源文件用于核对接口形状与行为线索，**不等于目标 APK 的实机验证**。本工程使用反射，不将 QQ、QFun 或 QAuxiliary 二进制打包进去。

## QFun

- 项目：https://github.com/oneQAQone/QFun
- 复读分支、发送参数：`app/src/main/java/me/yxp/qfun/hook/chat/RepeatMsg.kt`，本次读取文件 SHA `f1c1bd5c135c05c47a01c81271a363c341039019`。
- `IKernelMsgService.CppProxy` 的 sendMsg/forwardMsg 签名：`qqinterface/src/main/java/com/tencent/qqnt/kernel/nativeinterface/IKernelMsgService.java`，SHA `d672d89d0f881cc35400d61def73328b69d02be6`。
- 气泡与 VAS 属性结构：同目录的 `MsgAttributeInfo.java`、`VASMsgElement.java`、`VASMsgBubble.java`。
- `TextElement.java` 的正文与 atType：SHA `55309897f6df2bbe58ea8b1fbec24470feb36263`。
- 原消息读取接口：`qqinterface/src/main/java/com/tencent/mobileqq/aio/msg/AIOMsgItem.java`，SHA `aa97f0f57fb3febafb460ff0f73f57fe463760ba`。
- 输入控件线索：`qqinterface/src/main/java/com/tencent/mobileqq/aio/input/edit/AIOEditText.java`。
- Xposed Maven 源配置：`settings.gradle.kts`，SHA `dc56ea1942ee16440e79b6a96d6e8a7b7b1c8258`。

QQ SDK / 内部类名归各自权利人所有。`tests/fixtures` 是独立编写的最小模拟模型，只用于测试，不是反编译得到的 QQ 实现。

## QAuxiliary

https://github.com/cinit/QAuxiliary/blob/main/app/src/main/java/me/hd/hook/RandomBubble.kt

本次读取 SHA `b2b71eeb5981c963fa43aa0fec2106f97607bce0`。其随机子气泡实现基于 `SVIPHandler.getSubBubbleId`。本工程有意不采用全局 getter 替换方案，以缩小与复读的交互面；也不内置其气泡 ID 列表。

## 注入与工具链

- NPatch：https://github.com/7723mod/NPatch
- Legacy Xposed Hook API：https://api.xposed.info/reference/de/robv/android/xposed/XC_MethodHook.html
- 官方 Xposed Maven 库：https://api.xposed.info/
- Android Gradle Plugin 8.7.x / Gradle 8.9 / SDK 35 / JDK 17 兼容说明：https://developer.android.com/build/releases/agp-8-7-0-release-notes
- Android SDK 官方下载与工具说明：https://developer.android.com/studio
- Gradle 官方校验值：https://gradle.org/release-checksums/

使用固定 Gradle 8.9 二进制发行包 SHA-256：
`d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`

AGP 8.7.3、Gradle 8.9 并非在此声称为最新版本；选择它们是为了固定、可说明的构建组合。
