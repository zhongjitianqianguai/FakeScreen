# FakeScreen

电源键只关闭或恢复物理屏幕，保留系统运行状态。另提供独立的“禁用休眠”、快捷设置磁贴和 Shell 控制。

版本 **1.8.3 / versionCode 22**，最低支持 **Android 12 / API 31**。正式 APK 与校验文件见 [Releases](https://github.com/zhongjitianqianguai/FakeScreen/releases/tag/v1.8.3)。

## 选择构建

| 构建 | 框架要求 | 推荐作用域 |
| --- | --- | --- |
| modern | 支持 libxposed API 102 的 LSPosed / Vector | 系统框架（`system`） |
| legacy | legacy Xposed API / LSPosed New XSharedPreferences | 系统框架（`android`），模块自身由旧框架处理 |

两种构建都以 Android 17 / API 37 编译并设定 target SDK。两个正式构建共用包名，选择符合框架的一种安装。

多屏设备在每次开始假息屏时按逻辑默认屏选择物理面板，并在恢复前固定该面板的 token。活动屏变化不会把恢复命令发到另一块屏；关闭假息屏设置会恢复模块关闭的显示。隐藏接口不可用时，电源键继续执行系统原行为。

“禁用休眠”阻止普通与独立无操作超时，保留电源键、折叠等显式休眠策略。电源服务就绪后只对有限的自动休眠调用者去优化；legacy 使用 LSPosed 的扩展接口。HyperOS 调暗时保留防休眠期间的触摸交互。

## 安装与迁移

本仓库发布的 1.8.3 使用**独立正式签名**，与原作者的 1.7.x 签名不同，不能直接覆盖安装。签名证书 SHA-256：

```text
ffad8e58af30a0340834a78340cf6ee44d3dd49b9e5026a25c910adb91297050
```

从原签名版本迁移时，先记录两项开关与作用域，备份旧 APK 和应用配置，再卸载旧签名包、安装本仓库正式包并恢复设置。在框架中启用正式版、选择系统框架并重启；打开设置页，确认系统已加载当前版本。更改设置实时同步，无需每次重启。

正式包名为 `me.neversleep.plusplus`。测试版使用独立包名 `me.neversleep.plusplus.debug`，名称为“FakeScreen 测试版”，图标带橙色 T。正式版与测试版可以并存，验收时仅启用其中一个，避免重复 Hook；切换前记录测试版的两个开关。

若自行使用**同一原证书**构建并覆盖旧 legacy 版本，仍保留桥接迁移能力：先安装新版 legacy 并打开一次，将框架配置保存为私有快照，再同签名覆盖 modern。modern 首次读取时导入快照；已有远程键、本地首次操作和旧配置来源都会保留。无法确定新旧且值冲突时，设置页列出两份开关值供选择；选择前不覆盖快照，磁贴和 Shell 提示先完成选择。

框架未连接时，设置先保存在本地并提示等待同步。legacy 的激活提示查询系统进程实际加载版本，不再仅凭应用自身被 Hook 判定成功。

## Shell 控制

```shell
am start -n me.neversleep.plusplus/me.neversleep.plusplus.MainActivity --ez power true
am start -n me.neversleep.plusplus/me.neversleep.plusplus.MainActivity --ez power false
```

测试版只需将组件左侧包名替换为 `me.neversleep.plusplus.debug`，右侧 Activity 类名不变。设置页提供完整命令，长按可复制。

命令入口由 Android `DUMP` 权限保护，ADB Shell / root 可以调用。普通应用打开设置应使用启动 Intent 或 `me.neversleep.plusplus.SettingsActivity`；设置页不接受外部 `power` 指令。旧版手动固定的 Activity 快捷方式可能需要重新添加。

## 构建与验证

需要 JDK 17、Android SDK 37。项目使用 AGP 9.4.1 / Gradle 9.8.1、libxposed API / Service 102.0.0。

```powershell
.\gradlew.bat :app:assembleModernDebug :app:assembleLegacyDebug
.\gradlew.bat :app:assembleModernRelease :app:assembleLegacyRelease
.\gradlew.bat :app:testModernDebugUnitTest :app:testLegacyDebugUnitTest
.\gradlew.bat :app:lintModernDebug :app:lintLegacyDebug
```

输出位于 `app/build/outputs/apk/<modern|legacy>/<debug|release>/`。本地 Gradle 的 release 输出默认未签名；发布页 APK 已使用上述正式证书签名，私钥不纳入仓库。

本次 modern 候选在 Android 13、16、17 上完成显示控制、黑屏/调暗后 ADB 触摸、普通与独立无操作超时开关对照，以及正常唤醒；Android 17 折叠设备还验证了双屏原面板恢复。legacy、多用户和其余 Android 版本/ROM 的本次真机验收仍有限，最低安装版本不代表每款 ROM 的隐藏接口都已实测。构建、回归与历史结果详见 [改造记录](MODERNIZATION.md)。
