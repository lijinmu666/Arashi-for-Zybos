# 原版应用详情与通知设置

开关位于 Hook 功能设置 → 行为，应用详情和通知共用一个开关，默认关闭，沿用旧版应用详情开关的已有状态，作用域为设置 `com.android.settings`。作用域首次启用后重启设置；之后切换开关，退出当前应用详情页并重新打开。已打开的页面沿用创建时的界面，避免操作过程中切换按钮。

当前按完整 AOSP 应用信息页恢复偏好树和控制器，不再只替换按钮。包括原版信息头部、打开应用、卸载／停用／启用、强行停止，以及通知、权限、存储和缓存、移动数据与 WLAN、电池、默认打开、闲置应用、特殊权限、安装来源与版本等项目。系统不支持、不适用或受策略限制的项目仍由原生控制器隐藏或禁用；Android 14 的语言、所有服务等专属项目按固件支持显示。通知设置仍由同一个开关控制。

整页恢复目前仅完成源码与主机检查，尚未在设备验证。用户已确认 44 的页面跳转不再闪退，但明确指出只恢复按钮不符合需求；本次重新实现整页恢复，不能将 44 的反馈当成本次界面已验证。

## 样本核对

| 样本 | 控制器 | 头部偏好 | 按钮状态接口 |
| --- | --- | --- | --- |
| Android 12 设置 4.0 | `AppButtonsPreferenceController`，宿主已改为 `ZybSettings` | `com.android.settings.widget.AppStoreDetailHeaderPreference` | `setInUnstallButton`、`setForceStopButton` |
| Android 14 设置 4.0.1 | 页面实际使用 `ZybAppButtonsPreferenceController`；另有未被页面采用的原版控制器 | `com.android.settings.zyb.widget.ZybAppStoreDetailHeaderPreference` | `setUninstallButtonStatus`、`setForceStopButtonStatus` |

两版均保留 `com.android.settingslib.widget.ActionButtonsPreference` 和 `LayoutPreference`。Android 14 优先通过 `R` 字段取资源；Android 12 部分生成字段已删除，但资源表保留名称，使用 `getIdentifier` 补齐。Android 12 的 `SubSettings` 仍继承厂商宿主；Android 14 原版控制器需要 `SettingsActivity`，厂商宿主并不继承它。仅修改跳转目标不能完成恢复。

实现保留厂商 Activity 及其类型匹配的操作控制器，恢复同一 Fragment 中的完整页面：

- 两版均选择保留的 `app_info_settings.xml`；Android 14 仍有完整 AOSP 偏好树，Android 12 的该 XML 只剩少数项目，在绑定控制器前补回缺失项目，采用原生 Preference、分类和开关。
- 移除 Android 12 残留的厂商标题控制器，避免未初始化的父页面导致绑定异常；补回被厂商从 `createPreferenceControllers` 删除的电池、内存、默认应用和即时应用控制器，并恢复 XML 控制器的包名、父 Fragment、生命周期与分类子控制器初始化。原版数据用量、安装来源、高级权限等项目使用真实控制器刷新状态及打开对应设置。
- 厂商类型的旧头部作为不可见的数据对象保留，避免 `ClassCastException`；可见头部直接复用 `settings_entity_header`，操作栏使用 SettingsLib。打开应用按目标 UID 所属用户启动，没有启动入口的应用不显示打开按钮。卸载、停用、强行停止仍使用原监听器和原系统策略。
- 原 XML 仍存在的项目不会重复添加；页面销毁时清除状态，当前页面的布局选择保持到退出。页面绑定只作用于本次应用信息页的指定资源，其他偏好页不受影响。

偏好树与初始化顺序参考 [AOSP Android 12](https://github.com/aosp-mirror/platform_packages_apps_settings/blob/android-12.0.0_r1/res/xml/app_info_settings.xml) 和 [AOSP Android 14](https://github.com/aosp-mirror/platform_packages_apps_settings/blob/android-14.0.0_r1/src/com/android/settings/applications/appinfo/AppInfoDashboardFragment.java)，并按两份设置样本实际保留的类、构造函数与资源名适配。

普通应用显示“卸载”；系统应用按当前启用状态显示“停用”或“启用”。标题不代表按钮一定可用，实际以原控制器给出的状态为准。此功能与“解除应用详情页限制”分开，后者只处理部分包名的页面打开限制。

整页方案准备阶段缺少必要类、构造函数、XML 或应用信息时沿用固件页面；按钮桥接缺少监听器、资源或应用信息时保留原头部。已启用页面在状态同步失败后移除新增偏好并恢复旧头部。日志标签为 `[Arashi-AppDetails]`。

## 验证范围

`python3 tests/check_app_details.py` 运行真实 Hook 源码，使用两版不同的样本接口模型检查默认关闭、开启与重复进入、原生回调、卸载／停用／启用文字、禁用按钮、状态更新、无关页面和失败回退。

`python3 tests/check_full_app_info.py` 使用两份样本的公开构造函数及资源契约运行真实整页 Hook，覆盖控制器初始化、分类关系、完整树补齐、目标用户 UID、重复进入、跨页面隔离、缺少应用信息回退和销毁清理。`tests/fixtures/app_info_12.json` 与 `app_info_14.json` 仅保存签名和资源编号，不包含样本 APK 代码。

本次整页恢复纳入内部版本 45 的 GitHub Actions APK 构建；主机模型不能证明设备上的显示和操作结果。其他固件需另行核对。

## 设置侧栏入口与闪退反馈（内部版本 43／44）

用户确认内部版本 42 在活动启动器打开的原生应用列表中可生效，但从设置主页侧栏“应用与存储”进入时仍显示原页面。旧实现只在 `onCreatePreferences` 后尝试一次，并没有调整厂商专用启动器。

43 补充了 `onResume`、`refreshUi` 后的重试，并把启动器的应用详情目的地强制切到 `InstalledAppDetailsTop`，通知目的地强制切到 `Settings$AppNotificationSettingsActivity`。用户随后确认点击应用详情立即闪退。主机的类存在与参数保留模型没有验证真实 Activity 的启动、主题与宿主生命周期，因此此前通过不能证明该跳转兼容。

44 删除了强制替换 Activity 的 Hook，完整保留原 Intent、组件、flags、侧栏索引、参数及原启动器的用户和结果处理。页面内的恢复同时在两版控制器 `initButtonPreference` 完成后触发；有视图时使用 `View.post` 延后执行，避免在原刷新调用栈内改动偏好。重复请求合并，视图已更换、开关已关闭或投递失败时不修改页面；初始偏好创建且尚无视图时保留原本的安装路径。

用户随后确认 44 页面跳转不再闪退；尚无崩溃堆栈，不能据此确定 43 的具体根因。本次整页恢复继续保留该宿主方案，新增页面内容与操作仍需设备验证。

## 原版应用通知设置（内部版本 43）

应用详情和通知设置合并为“行为”中的“原版应用详情与通知设置”，默认关闭，作用域为设置。该功能处理应用详情中的“通知”以及应用通知列表进入的应用通知页，不替换“声音”等系统其他页面。启用作用域后重启设置，更改开关后退出当前通知页再打开。

- Android 14：固件仍保留 `app_notification_settings.xml`，实际却选择 `zyb_app_notification_settings.xml`。启用后恢复前者，并把 `ZybBlockPreferenceController` 换回保留的 `BlockPreferenceController`，移除厂商声音总开关控制器，使用原版通知渠道与相应控件。厂商类型的头部偏好仅作为隐藏的数据对象保留，可见部分使用原版信息头部。
- Android 12：原 `app_notification_settings.xml` 已直接修改，仍保留渠道列表和大部分通知控制器。隐藏厂商可见头部、`NotifySwitchPreference` 及厂商勿扰控件，增加保留的 `LayoutPreference` 信息头部和 `SettingsMainSwitchPreference` 主开关。通过适配监听器调用原 `BlockPreferenceController.onValueClick`，不重写通知后端。原 XML 中的通知渠道、角标等原版控件沿用。
- 44 起不再改变通知目的地的 Activity，沿用原宿主、请求参数、目标用户和结果处理。
- Android 12 的 `SettingsMainSwitchPreference` 没有 14 的 `setSwitchBarEnabled`、`setDisabledByAdmin` 方法，使用偏好本身的可用状态，并在控件绑定后向实际 SwitchBar 同步管理员限制。不能用 14 的接口模型替代 12 验证。

主机检查 `tests/check_settings_routes.py` 覆盖同一开关同时控制详情与通知、三种启动器未被 Hook、原组件及完整参数保留、14 的 XML／控制器配对、12 的回调与策略限制、资源缺失及状态同步失败回退；`tests/check_app_details.py` 增加两版控制器就绪后的延后安装、重复任务、旧视图、关闭开关及投递失败的恢复检查。真机界面、通知开关、渠道设置和用户切换仍待验证；内部版本 44 已获用户反馈跳转正常；本次整页恢复仍需另行装机验证。
