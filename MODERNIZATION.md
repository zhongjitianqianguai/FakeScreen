# Android / LSPosed 现代化改造

当前版本：**1.8.2 / versionCode 20**。

## 要求与结果

| 要求 | 结果 |
| --- | --- |
| 支持更新的 Android | compile / target SDK 37；Android 16、17 上完成相关真机验收 |
| 支持 libxposed API 102 | modern flavor 使用 API / Service 102.0.0，已在系统进程验证 |
| 保留旧框架支持 | legacy flavor 保留 Xposed API 82 入口，最低 Android 5.0 |
| 保留现有功能 | 电源键假息屏、独立禁用休眠、快捷设置磁贴、Shell 控制及旧配置迁移均保留 |
| 修复 ADB 触摸副作用 | 防休眠开启时，超时调暗和物理假息屏期间均能通过 ADB 点击更改并持久化设置 |
| 构建与回归 | 四种 APK 构建、30 项测试、双 flavor Lint 和打包隔离检查通过 |

## 构建与框架适配

- 使用 AGP 9.4.1、Gradle 9.8.1、JDK 17、Android SDK 37。
- modern 最低 API 26，使用 `META-INF/xposed` 模块入口、`system` 作用域和 `onSystemServerStarting` 生命周期。
- legacy 最低 API 21，保留旧 Xposed 入口。两种 flavor 不混用框架 API，APK 不打包框架提供的 API 类。
- 应用设置保留本地持久化；modern 通过框架远程偏好设置同步，并处理连接断开及首次导入。
- `singleTop` 确保设置页已在前台时，原有 Shell 命令仍通过 `onNewIntent` 写入设置。
- 激活状态识别框架返回的 `system`、`system_server`、`android` 系统进程目标，并核对加载版本。
- 显示控制按 Android 版本选择隐藏接口；控制失败时电源键继续执行系统原有行为。配置关闭会恢复由模块关闭的显示。

## 旧配置迁移与安装

旧版配置可能位于框架重定向目录，modern API 无法直接读取。迁移需要先覆盖安装同签名的新版 legacy 桥接构建，打开一次保存私有快照，再覆盖安装 modern。已有远程键在本地缺少对应键时保留；首次操作不会被旧快照覆盖。

release 保持原包名 `me.neversleep.plusplus`，默认产物未签名，覆盖旧版需要原签名证书。debug 使用独立包名 `me.neversleep.plusplus.debug`，可与原版并存。新旧模块并存验收时，仅启用其中一个，避免重复 Hook。完整步骤与构建命令见 [README](README.md)。

## HyperOS 触摸问题与修复

开启防休眠后，屏幕超时仍会进入 DIM 状态，但系统保持 Awake。部分 HyperOS 实现会在此时设置 `mTouchInteractiveUnavailable=true`，并调用输入服务的 `setDimState(true)`；ADB 触摸因此不再触发设置页操作。正常休眠唤醒或键盘活动能解除该状态，单独的物理屏关闭与恢复不触发这一问题。

运行态对照与 ROM 类的静态分析确认，相关调用为：

```text
PowerManagerServiceImpl.updatePowerGroupPolicy(group0, policy2)
  -> PowerManagerServiceImpl.setTouchInteractive(true)
  -> MiuiInputManagerInternal.setDimState(true)
```

1.8.2 在 modern、legacy 两个入口中精确匹配可选的 `setTouchInteractive(boolean)` 方法。仅在防休眠开启时，将其触摸 DIM 参数改为 false，然后继续执行原 setter；关闭防休眠时完整传递原参数。

该修复保留屏幕调暗、原有屏幕策略、传感器逻辑和物理假息屏。不包含该可选方法的 ROM 继续使用基础功能。未修改系统 JAR，未删除或禁用现有功能。

## 验证

- modern / legacy 各 15 项单元测试通过，共 30 项；覆盖显示成功与失败恢复、关闭配置恢复显示、方法重载发现、防休眠开关独立性和触摸 DIM 参数行为。
- 四种构建通过：modern / legacy 的 debug 和 unsigned release。
- 两种 flavor 的 Lint 均为 0 错误。仍有兼容依赖、旧资源和系统隐藏接口警告；隐藏显示接口的检查仅在系统进程实际执行的方法上定向说明并抑制。
- 打包检查确认 modern 使用 API 102 元数据、legacy 使用旧入口，无混合 API 调用或框架 API 类打包。
- 同一 modern debug APK 在 Android 17 / API 37（LSPosed 2.2.0 / API 102）和 Android 16 / API 36（LSPosed 2.2.1 / API 102）完成以下触摸验收：

| 场景 | Android 17 | Android 16 |
| --- | --- | --- |
| 防休眠开启，15 秒超时后正常调暗 | 超过 47 秒仍 Awake，触摸不可交互标志 false | 超过 83 秒仍 Awake，触摸不可交互标志 false |
| 调暗后的 ADB 点击 | 复选框变化且设置持久化 | 复选框变化且设置持久化 |
| 同时开启假息屏与防休眠 | 主屏 Off 超过 129 秒后仍能点击 | 主屏 Off 超过 141 秒后仍能点击 |
| 物理屏关闭期间的 ADB 点击 | 设置持久化，主屏继续 Off | 设置持久化，主屏继续 Off |
| 关闭假息屏 | 主屏恢复 On | 主屏恢复 On |
| 关闭两项功能后的对照 | 原 DIM 触摸策略恢复，随后正常 Asleep | 原 DIM 触摸策略恢复，随后正常 Dozing |

迁移、首次 Shell 操作、前台 Shell 热启动、快捷设置磁贴持久化和电源键显示 Off→On 已通过实际操作验证。测试后恢复各设备原有配置；原版应用与数据保留。

真机触摸验收使用的 modern debug SHA-256：

```text
BCA964FE61AAD3CC4397E67982CDC457A59E9D231035B0AFF6AF83C2B0B9A29C
```

legacy 的构建与回归已通过，未单独完成系统 Hook 的真机验收。最低 Android 安装版本不代表所有 ROM 隐藏接口已验证。设备日志、截图、配置快照、回滚 APK 和本地验收记录不纳入源码仓库；APK 未通过此源码提交发布。

## 技术依据

- [libxposed API 102 官方说明](https://github.com/libxposed/api)
- [模块生命周期与元数据](https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html)
- [libxposed Service](https://github.com/libxposed/service)
- [AGP 9.4 兼容表](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- AOSP [PhoneWindowManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/policy/PhoneWindowManager.java)、[PowerManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/power/PowerManagerService.java)、[DisplayControl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/DisplayControl.java)
