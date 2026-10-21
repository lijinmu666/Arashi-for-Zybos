# 原版样式应用详情页

开关位于 Hook 功能设置 → 行为，默认关闭，作用域为设置 `com.android.settings`。作用域首次启用后重启设置；之后切换开关，退出当前应用详情页并重新打开。已打开的页面沿用创建时的界面，避免操作过程中切换按钮。

当前恢复应用信息头部和 AOSP SettingsLib 操作栏，显示卸载／停用／启用、强行停止。没有新增打开应用按钮。存储、通知、权限、默认设置等现有项目继续使用固件实现；这不是完整恢复所有 AOSP 页面内容，也不会补回已删除的控制器。

## 样本核对

| 样本 | 控制器 | 头部偏好 | 按钮状态接口 |
| --- | --- | --- | --- |
| Android 12 设置 4.0 | `AppButtonsPreferenceController`，宿主已改为 `ZybSettings` | `com.android.settings.widget.AppStoreDetailHeaderPreference` | `setInUnstallButton`、`setForceStopButton` |
| Android 14 设置 4.0.1 | 页面实际使用 `ZybAppButtonsPreferenceController`；另有未被页面采用的原版控制器 | `com.android.settings.zyb.widget.ZybAppStoreDetailHeaderPreference` | `setUninstallButtonStatus`、`setForceStopButtonStatus` |

两版均保留 `com.android.settingslib.widget.ActionButtonsPreference` 和 `LayoutPreference`。Android 14 优先通过 `R` 字段取资源；Android 12 部分生成字段已删除，但资源表保留名称，使用 `getIdentifier` 补齐。Android 12 的 `SubSettings` 仍继承厂商宿主；Android 14 原版控制器需要 `SettingsActivity`，厂商宿主并不继承它。仅修改跳转目标不能完成恢复。

实现保留厂商控制器及其类型要求，在原头部后加入真实的 SettingsLib 操作栏和重新构建的信息头部，隐藏旧头部偏好。原控制器仍负责应用信息、用户 ID、确认对话框和操作结果；操作栏直接使用原强行停止与卸载／停用监听器。按钮可用状态跟随两版各自的状态 setter 更新，并反优化已确认的调用方法，避免 setter 内联后漏掉同步，不额外放开系统或管理策略限制。

普通应用显示“卸载”；系统应用按当前启用状态显示“停用”或“启用”。标题不代表按钮一定可用，实际以原控制器给出的状态为准。此功能与“解除应用详情页限制”分开，后者只处理部分包名的页面打开限制。

缺少目标类、按钮监听器、资源、应用信息或添加偏好失败时保留旧页面。已启用页面在状态同步失败后移除新增偏好并恢复旧头部。日志标签为 `[Arashi-AppDetails]`。

## 验证范围

`python3 tests/check_app_details.py` 运行真实 Hook 源码，使用两版不同的样本接口模型检查默认关闭、开启与重复进入、原生回调、卸载／停用／启用文字、禁用按钮、状态更新、无关页面和失败回退。

这次只提交源码，没有编译 APK。尚未真机验证视图、卸载／停用确认及强行停止操作；主机模型不能证明设备上的操作结果。其他固件需另行核对。
