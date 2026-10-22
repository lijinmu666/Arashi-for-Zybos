# 解除通知图标数量限制

入口：Hook 功能设置 → 美化 → 解除通知图标数量限制。默认关闭，需要系统界面 `com.android.systemui` 作用域；安装更新、切换开关后重启 SystemUI，或重启设备。

开启后，普通状态栏可以显示超过原固定数量的通知图标，以实际可用宽度为准。空间不足时继续使用系统原有的溢出圆点／隐藏策略；通知过滤、静默通知分界、刘海区域、时钟及系统图标占用的空间继续由系统处理。锁屏、息屏显示及通知架不应用此 Hook。

## 两份样本

| 样本 | 普通状态栏数量来源 | 处理 |
| --- | --- | --- |
| Android 12 | `NotificationIconContainer.calculateIconTranslations()` 中的 static 分支硬编码为 4 | 仅在普通状态栏计算期间使用按子项数量排布的分支，完成后恢复 static 标记 |
| Android 14 | `mMaxStaticIcons`，样本的 `max_notif_static_icons` 资源为 4 | 在 `onMeasure` 和 `calculateIconXTranslations()` 中临时将数量放宽至当前子项数量，完成后恢复原字段 |

因此不能将所有系统的限制统称为“五个”；溢出提示和不同界面也会影响肉眼看到的图标数量。以上数字仅代表提供的样本，其他固件或运行时资源覆盖可能不同。

## 范围与恢复

仅匹配 SystemUI 内 ID 为 `notificationIcons` 或 `left_aligned_notification_icon_container` 的普通状态栏通知容器，同时检查 static、锁屏、息屏与通知架状态。继续调用固件原来的排布算法，不替换图标、不更改通知内容或状态栏系统图标。

Android 14 的测量也使用数量限制，仅修改最终排布会使容器宽度仍受旧上限约束，因此两条路径一起处理。数量使用实际子项数，不写入 `Integer.MAX_VALUE`，避免测量算术中的无意义大值。临时字段在正常返回和异常后均恢复；关闭开关后原布局在下一次刷新时恢复，重启 SystemUI 可确保完成全部重新测量。

日志前缀：`[Arashi-NotificationIcons]`。缺少预期目标时保留固件布局并记录日志。

## 验证

`python3 tests/check_notification_icon_limit.py` 在符合两份样本结构的容器上执行真实 Hook 回调，检查超过四个图标、数量为零、宽度不足、原有过滤、两个状态栏 ID、其他容器和包名、锁屏／息屏／通知架保护、Android 14 测量路径，以及关闭和异常时字段恢复。

主机测试不覆盖真机布局动画，v1.2.0 已构建 APK，设备反馈见下。

用户于 2026-10-22 反馈当前设备实测可用。该反馈不代表所有 Android 12／14 固件均已验证。
