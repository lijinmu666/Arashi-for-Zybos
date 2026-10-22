# 传统电源菜单

入口：Hook 功能设置 → 美化 → 始终使用传统电源菜单。默认关闭，需要同时勾选系统框架与系统界面 `com.android.systemui` 作用域。安装更新或修改作用域后重启设备，之后切换开关在下次打开电源菜单时生效；关闭后重新打开菜单恢复系统原来的选择。

开启后，长按电源键使用系统框架的 `LegacyGlobalActions` 对话框；下拉面板的电源按钮也转交系统框架打开此菜单。菜单项目由固件配置与系统状态决定，关机、重启、锁屏／锁定等操作继续使用系统原有实现。本功能不自行执行电源或锁屏指令。

## 样本核对

已查看所提供的两份 SystemUI APK，确认以下调用：

| 样本 | 下拉电源入口 | 显示方法 |
| --- | --- | --- |
| Android 12 | `QSFooterViewController$2.onClick` | `GlobalActionsDialogLite.showOrHideDialog(boolean, boolean)` |
| Android 14 | `FooterActionsViewModel$power$1.invoke` | `GlobalActionsDialogLite.showOrHideDialog(boolean, boolean, Expandable)` |

两份样本的框架回调均通过 `GlobalActionsImpl.showGlobalActions` 调用上述方法；无障碍电源操作已经使用 `IWindowManager.showGlobalActions`，可直接沿用框架侧处理。

传统菜单本身不在 SystemUI APK 中。框架入口另外参照 [AOSP Android 12](https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/services/core/java/com/android/server/policy/GlobalActions.java) 与 [AOSP Android 14](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/services/core/java/com/android/server/policy/GlobalActions.java) 的实现核对；尚未取得设备实际的厂商框架进行静态核对。

## Hook 行为

- 系统框架：在一次 `GlobalActions.showDialog(boolean, boolean)` 调用期间，临时将 `mGlobalActionsAvailable` 置为 false，使原方法选择 Legacy 分支；不更改传入的锁屏／初始化状态，也不跳过原方法的策略禁用检查。清除旧的显示超时任务，调用完成或抛异常后恢复原来的可用状态。
- 系统界面：将下拉电源按钮的直接显示请求通过 `IWindowManager.showGlobalActions` 交还系统框架。框架回调带有本进程的调用标记，框架 Hook 缺失时可以显示原菜单，避免跨进程反复重定向。
- 按样本对实际调用者做反优化，覆盖 Android 12 点击监听和 Android 14 的 ViewModel／桥接方法。日志前缀为 `[Arashi-LegacyPower]`。

## 验证范围

`python3 tests/check_legacy_power_menu.py` 使用保留原始版权头的 AOSP 12／14 `GlobalActions` 源码作为框架测试夹具，执行实际 Hook 回调，覆盖原菜单回退、传统分支、策略检查、参数及关闭回调、超时任务清除、异常后的可用状态恢复、两个下拉入口签名、重复打开、Binder 失败与避免重定向循环。夹具许可副本位于 `tests/fixtures/LICENSE.apache-2.0.txt`。

主机测试通过；本次仅更新源码，未构建 APK。厂商框架是否保留对应类、方法和字段，以及菜单实际样式和操作，仍需设备验证。
