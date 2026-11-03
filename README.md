# FakeScreen

电源键只关闭 / 恢复物理屏幕显示，保留系统原有运行状态；另提供独立的“禁用休眠”选项、快捷设置磁贴和 Shell 控制。

当前版本 **1.8.2 / versionCode 20**。已在 Android 17 / API 37、LSPosed 2.2.0 / API 102 完成显示控制、防休眠、配置入口及超时后的 ADB 触摸验收，详见 [改造记录](MODERNIZATION.md)。

同一 APK 也已在 Android 16 / API 36、HyperOS 3、LSPosed 2.2.1 / API 102 通过超时调暗后和物理假息屏期间的 ADB 触摸测试。

1.8.2 修复 HyperOS 开启“禁用休眠”后，超时调暗导致ADB触摸被拦截的问题：保留正常调暗，在防休眠期间保持触摸可交互；物理假息屏期间的ADB点击也已验证。关闭选项后恢复系统原有策略。

## Android 与框架

| 构建 | 最低 Android | Xposed 框架 | 推荐作用域 |
| --- | --- | --- | --- |
| modern | Android 8.0 / API 26 | 支持 libxposed API 102 的 LSPosed | 系统框架（`system`） |
| legacy | Android 5.0 / API 21 | 原有 legacy Xposed API / LSPosed New XSharedPreferences | 系统框架（`android`），模块自身由旧框架处理 |

两种构建均以 Android 17 / API 37 编译和设定 target SDK。最低安装版本不代表每款 ROM 的隐藏显示接口已通过真机验证；设备实测见 [改造记录](MODERNIZATION.md)。

modern 使用 `META-INF/xposed` 模块入口和框架远程偏好设置，不包含旧 Xposed 调用。legacy 保留旧框架入口。假息屏和禁用休眠可以分别控制。显示接口不可用时，电源键继续执行 Android 原有行为；modern 中关闭假息屏设置会恢复由模块关闭的显示。

## 安装与旧设置迁移

1. 旧版 1.7 用户先用**同一签名证书**覆盖安装新版 legacy 桥接版，并打开一次。它会把框架重定向目录中的两项设置保存到应用私有 `files/x_conf-legacy.properties`。
2. 再用同一证书覆盖安装 modern 版。首次读取设置时会导入快照，之后同步到框架；已有远程配置在缺少本地键时也会被保留。
3. 在 LSPosed 启用模块，选择系统框架，重启后打开应用一次。更改设置实时同步，无需每次重启。框架未连接时先保存本地，并显示等待同步。

直接从旧版换成 modern 不能读取 LSPosed 的旧随机配置目录；请先完成桥接步骤。请勿通过卸载原应用来绕过签名不匹配，否则会丢失原应用数据。

release 包名保持 `me.neversleep.plusplus`。仓库不含原发布私钥，release APK 默认未签名。debug 包名为 `me.neversleep.plusplus.debug`，可与原版并存，用于验收；两种 debug flavor 可以相互覆盖并保留测试数据。

## 构建

需要 JDK 17、Android SDK 37。项目使用 AGP 9.4.1 / Gradle 9.8.1；依赖为实际已发布的 libxposed API / Service 102.0.0。

```powershell
.\gradlew.bat :app:assembleModernDebug :app:assembleLegacyDebug
.\gradlew.bat :app:assembleModernRelease :app:assembleLegacyRelease
.\gradlew.bat :app:testModernDebugUnitTest :app:testLegacyDebugUnitTest
.\gradlew.bat :app:lintModernDebug :app:lintLegacyDebug
```

输出位于 `app/build/outputs/apk/<modern|legacy>/<debug|release>/`。

## Shell 控制

```shell
am start -n me.neversleep.plusplus/me.neversleep.plusplus.MainActivity --ez power true
am start -n me.neversleep.plusplus/me.neversleep.plusplus.MainActivity --ez power false
```

debug 版将组件左侧包名替换为 `me.neversleep.plusplus.debug`，右侧 Activity 类名不变。设置页提供当前构建对应的完整命令，长按可复制。
