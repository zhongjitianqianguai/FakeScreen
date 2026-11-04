# Android / LSPosed 现代化改造

当前版本：**1.8.3 / versionCode 22**；后文历史验收对应 **1.8.2 / versionCode 20**。

## 1.8.3 审查修复与正式制品

本节是原 1.8.2 之后的工作区修改。后文的 SDK 下限、真机结果及 APK 哈希仅代表历史 1.8.2，不代表本次候选；当前支持范围为 Android 12 / API 31 起，modern 与 legacy 均保留。

- 按 `powerPress` 参数语义区分单击、多击与非交互唤醒，并保留系统亮屏过渡判断。
- 配置读取、显示切换和配置关闭恢复在同一锁内完成；同一面板的系统显示操作使模块放弃旧的假息屏状态，模块自己的显示调用和其他面板不误重置。
- 原 Shell 组件 `MainActivity` 和 `power` 参数保持兼容，由 `android.permission.DUMP` 保护；公开设置页迁至 `SettingsActivity`，不解析外部命令。旧的手工固定快捷方式可能需要重新添加。
- legacy 快照记录私有/框架来源及导入标记；未激活时的修改在激活后优先迁入，已有框架值不会被旧框架快照覆盖，modern 桥接导入保持兼容。
- 对原 1.8.2 无来源标记的快照，比较真实配置文件和快照修改时间；无法排序且值冲突时保留两份，在设置页展示开关值供选择，不消费或覆盖未解决快照。
- legacy 使用限定到 `android` 包的有序广播查询系统进程实际加载版本。应用自身的 Hook 只用于识别配置环境，不再充当系统激活凭据。
- 回归覆盖正常唤醒、多击、配置并发、外部显示操作、迁移往返、首次用户操作与入口权限。首轮修复当时未安装设备或发布；后续设备结果见下文，未验证部分仍保留待验标记。

二次兼容修复：

- Android 13 非当前用户拒绝磁贴刷新时，配置保存仍成功；其他编程错误不被吞掉。
- legacy 系统查询按 `UserHandle.ALL` 注册；Android 12 使用五参数入口，Android 13 起使用带明确导出标志的六参数入口。
- 分别安装普通防休眠与无操作超时防休眠 Hook，关闭选项时继续返回系统原结果。
- 显示控制先解析逻辑默认屏的物理 ID；一个关屏/恢复周期固定同一 token，恢复失败时保留该 token 重试，系统接管对应面板时释放模块的恢复责任。
- 设备行为测试曾按用户要求暂缓，后续已恢复并安装 modern 测试版。

1.8.3 / code 21 已在 Android 17 折叠设备上验证外屏、内屏、双屏固定 token 恢复、关闭配置恢复，以及 ROM 折叠策略完成后的正常唤醒/再次切换。独立无操作超时在该版本仍失败；code 22 在电源服务就绪后去优化有限调用者，完成以下真实对照：

- 15 秒无操作超时：开启选项后超过 23 秒保持 Awake；关闭后进入 Asleep，原因为 inattentive。排除了充电常亮、接近传感器和亮度增强保持唤醒的因素。
- 8 秒普通超时：开启后超过 13 秒保持 Awake，关闭后按 timeout 休眠。防休眠开启时，显式电源键仍能正常休眠并唤醒。
- 双屏点亮后关闭内屏，原生默认屏切到外屏；电源键和关闭设置均恢复原内屏。姿态恢复时保留 device_folded 休眠，随后正常唤醒。
- 新图标已接入，测试版带橙色 T 标记，并显示“FakeScreen 测试版”，已在管理器中确认。
- code 22 的 57 项定向回归、四种 APK 构建及双 flavor Lint 通过；Lint 各 0 错误、27 警告。此前 C1-C4 完整回归 102 项的结果仍保留，未重复无关全量。
- 最终 APK 与设备内安装包 SHA-256 一致，测试后的超时参数、姿态覆盖和应用开关已恢复。legacy、多用户、Android 12、14、15 的本次真机验收，以及新迁移对话框和第三方调用拒绝的真机流程仍未验证；不据此声明所有构建和设备全部通过。

2026-11-04 使用同一 code 22 modern debug APK 补充完成 Android 16 单屏和另一台 Android 17 设备验收：物理关屏/恢复、关闭设置恢复、黑屏及调暗后 ADB 触摸、普通与 attentive 超时开关对照、假息屏开关开启时的正常唤醒均通过。Android 17 的未使用副屏保持原 Off 状态。两台旧正式包及各自测试前设置均保留；未因此修改业务代码或重新构建。

当前 modern debug SHA-256：`c4c584c42cd9b99a4d67af310a5887c5e11e48c1012636b00f6831797e47a5dd`。发布制品采用独立正式签名，区别于原作者 1.7.x；旧签名包需先备份设置再迁移。

2026-11-04 补充完成 Android 13 / API 33、Vector 2.2 / API 102 的 modern 验收：关屏/恢复、关闭设置恢复、物理黑屏及调暗后 ADB 触摸、普通与 attentive 超时开关对照、正常唤醒全部通过。测试时排除充电常亮等独立保持唤醒因素。

正式 APK 由已经验证的同源 release 构建签名，原 ZIP 业务条目逐字节不变，仅增加签名条目。正式包与测试包使用不同包名；正式 modern 已在 Android 13、16、17 完成实际接管验证。legacy 正式包签名、Manifest、入口隔离和本地回归通过，未单独完成本次真机验收。

| 正式制品 | SHA-256 |
| --- | --- |
| FakeScreen-1.8.3-modern.apk | `0aa7322b456217f959d55369753ea8983c7b123e5ad92cc688f86a39f2b05d8f` |
| FakeScreen-1.8.3-legacy.apk | `b56c2ce5ef4a0548b588f19a9fe09ed60ea24d7f9e5cf3828959f5928a3217b2` |

## 历史 1.8.2：要求与结果

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
