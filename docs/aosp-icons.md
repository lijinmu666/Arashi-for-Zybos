# AOSP 电池、Wi-Fi 与蓝牙图标

## 使用

入口：Hook 功能设置 → 美化 → 原版状态栏图标。

一个开关统一控制电池、WLAN 和蓝牙已连接图标，新安装默认关闭，只需系统界面 `com.android.systemui` 作用域。安装或更新模块后、切换开关后重启 SystemUI，或重启设备。图标功能不修改真实电量、网络连接状态或系统图标显示策略。

从三个独立开关的版本升级时，只要原有任意一个开关开启，合并开关就显示为开启并统一启用三个图标；原来全部关闭则保持关闭。用户操作合并开关后，以新开关的值为准，旧设置不会使已关闭的开关重新开启。界面、本地配置读取及 LSPosed 远程配置均使用同一迁移规则。

## 电池图标

- Android 12（6.3.0 样本）：恢复已有但隐藏的 `mBatteryIconView`，继续使用原生 `ThemedBatteryDrawable`。
- Android 14（7.10 样本）：把保留的 `AccessorizedBatteryDrawable` 接到新建的 ImageView。
- 隐藏作业帮横向电池及其附加充电视图，复用系统已有电量、充电、省电、颜色及百分比处理。关闭后恢复对应视图。
- Android 14 用户已确认图标发生预期变化；Android 12 仍待单独确认电池图标效果。

## Wi-Fi 图标

内部版本 39 仅在旧 `StatusBarWifiView` 路径替换 Wi-Fi 6 等变体，普通图标与新显示路径未完整覆盖，Android 14 用户反馈开启后没有变化。

内部版本 40 在 SystemUI 的 `Resources.getDrawable` 与 `getDrawableForDensity` 加载路径匹配 Wi-Fi 图标表中的精确资源 ID，直接使用 AOSP 原始矢量路径绘制五档信号和无网络提示，覆盖普通 Wi-Fi、Wi-Fi 6 及新旧显示路径，避免继续依赖设备资源的图形外观。未匹配到的资源保持原样，其他使用相同 Wi-Fi 资源的 SystemUI 位置也可能显示同一原版样式。

每个 Drawable 独立保存颜色状态，保留调用方的 tint、透明度及颜色过滤处理。不改变信号强度、连接状态、无障碍描述或隐藏图标的策略。

| 功能 | Android 12 | Android 14 |
| --- | --- | --- |
| AOSP Wi-Fi，内部版本 40 | 用户已反馈可用 | 用户已反馈可用 |
| AOSP 蓝牙已连接 | 样本核对、主机检查通过，待真机验证 | 样本核对、主机检查通过，待真机验证 |
| AOSP 电池 | 样本适配完成，待单独实测确认 | 用户已确认生效 |

上述反馈仅代表已测试设备及固件，不能推断所有 Android 12 / 14 固件兼容。此前 Android 12 Wi-Fi 不可用的反馈已由用户更正，不再列为未解决问题。

## 蓝牙已连接图标

蓝牙已连接图标由“原版状态栏图标”统一控制。Android 12 与 14 的 `PhoneStatusBarPolicy.updateBluetooth` 都选用 `ic_bluetooth_connected_zyb`；14 的调用已内联成资源整数，不依赖生成的 R 字段查找。沿 `Resources.getDrawable` / `getDrawableForDensity` 按完整的包名、类型和资源名匹配，直接绘制 AOSP 原始矢量：蓝牙符号左右各有一个点，原生尺寸为 17dp，路径视口为 24×24。

只替换厂商的连接图标资源，不修改蓝牙状态、状态栏图标可见策略、无障碍描述，也不替换使用其他资源的快捷设置磁贴。SystemUI 内如果有其他位置引用同一资源，也会使用这份图形。每次加载产生独立 Drawable，支持屏幕密度、显式请求密度、tint、透明度与颜色过滤器；资源不匹配或解析异常时继续原加载流程。

`python3 tests/check_bluetooth_icon.py` 运行真实 Hook 和绘制代码，核对两版匹配、开关关闭、无关资源、其他包名、异常回退、密度、绘制边界、独立 tint／透明度及与 AOSP 矢量路径一致性。检查不代表真机渲染与连接切换已验证。本次纳入内部版本 45 的 GitHub Actions APK 构建。

## 来源与排查

Wi-Fi 路径取自 AOSP `android-14.0.0_r1` 的 `ic_wifi_signal_*.xml` 与 SettingsLib 的 `ic_no_internet_wifi_signal_*.xml`；版权、Apache-2.0 许可及源路径保留在 `AospWifiDrawable.java` 中。

蓝牙路径取自 AOSP `android-12.0.0_r1` / `android-14.0.0_r1` 的 `packages/SystemUI/res/drawable/ic_bluetooth_connected.xml`，两版内容一致；来源与 Apache-2.0 许可保留在 `AospBluetoothDrawable.java` 中。

相关实现：`AospBatteryHooks.java`、`AospWifiHooks.java`、`AospWifiDrawable.java`、`AospBluetoothHooks.java`、`AospBluetoothDrawable.java`。若更换固件后失效，提供系统和 SystemUI 版本，以及重启 SystemUI 后的 `[Arashi-Battery]` / `[Arashi-Wifi]` / `[Arashi-Bluetooth]` 日志，以核对 Hook 安装和资源匹配。
