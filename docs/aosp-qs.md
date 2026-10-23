# 原版下拉面板

位于“美化 → 原版下拉面板”，默认关闭，仅需系统界面 `com.android.systemui` 作用域。开启或关闭后重启 SystemUI，以重新创建布局。

## 恢复范围

使用固件中保留的原生 QSFragment、QSPanel、QuickQSPanel、磁贴 Host、分页和编辑器，不创建独立悬浮面板，也不改写已保存的磁贴列表。

| 部分 | 处理 |
| --- | --- |
| 展开与收起 | 恢复输入高度的最小／最大值约束，二次下拉可展开完整面板 |
| 完整磁贴 | Android 14 移除强制隐藏，沿用原生展开、禁用和动画状态 |
| 磁贴布局 | 横向图标与文字、双列、80dp 高度、20dp 图标、28dp 圆角和 14sp 文字；简略区最多四项 |
| 磁贴测量 | 修正两版厂商按宽度测量正方形磁贴的逻辑，独立测量高度 |
| Android 12 状态头 | 接回原生日期／隐私行和时钟／状态图标行，移除厂商包裹布局对二者的纵向堆叠；隐藏头像和重复时钟 |
| Android 14 状态头 | 接回保留的 MotionLayout 状态头及运营商监听，隐藏厂商头像行 |
| 亮度 | 使用原有 SeekBar 与亮度控制器，恢复 AOSP 圆角进度和随进度移动的太阳图标布局，将亮度条放回完整磁贴上方；隐藏额外自动亮度按钮和简略区重复亮度条 |
| 厂商模式卡片 | 隐藏学习／工作模式卡片，保留控制器生命周期 |
| 编辑、分页、媒体 | 沿用保留的原生控制器和回调，不改动点击动作与磁贴存储 |

面板颜色读取框架动态调色板。“自定义 SystemUI 主题色”开启时，选中磁贴与亮度强调色优先使用手动颜色；需要恢复壁纸自动取色时，可同时开启“修复莫奈取色”。本开关本身不强制启用 Monet 生成器，也不改变密码键盘配色。

## 样本依据

Google Drive 中的 Android 12／14 SystemUI 样本均保留完整 QS 组件，但修改了展开入口：

- 12：`NotificationPanelViewController.setQsExpansion(float)` 忽略传入高度，固定采用最小高度。
- 14：`QuickSettingsController.setExpansionHeight(float)` 同样固定最小高度，`QSFragment.updateQsState()` 最后将完整 QSPanel 设为 GONE，`ShadeHeaderController.updateVisibility()` 将原生状态头隐藏。
- 两版 TileLayout 均用单元格宽度测量高度，且磁贴构造器改为竖向布局；14 的布局切换还会将亮度条移到磁贴和页脚下方。

恢复展开时保留原有的息屏、越界滚动、收起动画与展开状态分支，继续调用原生布局更新、无障碍和监听器。12 保留原有误触检测／锁屏验证回调；不改写系统禁用标志或解锁状态。

布局、展开及亮度进度行为参考 AOSP Android 12／14：

- [Android 12 NotificationPanelViewController](https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/packages/SystemUI/src/com/android/systemui/statusbar/phone/NotificationPanelViewController.java)
- [Android 14 QuickSettingsController](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/packages/SystemUI/src/com/android/systemui/shade/QuickSettingsController.java)
- [Android 14 QSTileViewImpl](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/packages/SystemUI/src/com/android/systemui/qs/tileimpl/QSTileViewImpl.kt)
- [Android 14 BrightnessProgressDrawable](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/packages/SystemUI/src/com/android/systemui/util/BrightnessProgressDrawable.kt)

## 验证与边界

`python tests/check_systemui_qs.py` 执行实际 Hook 回调，覆盖两版展开和回调、锁屏误触验证、系统禁用与关闭回退、资源归属、状态头及运营商监听、布局重挂载、亮度顺序、磁贴测量／Drawable 隔离、亮度进度边界及复制类型。

`python tests/check_systemui_theme.py` 覆盖原版面板与手动主题色、Monet 的颜色优先级及配置变化刷新，并继续检查原有手动／Monet 功能的关闭回退。

以上为样本分析与主机模拟检查，未编译 APK，未在真实 Android 12／14 设备验证展开手势、编辑器、分屏、旋转、隐私提示、媒体及动画。功能恢复面板结构与交互路径，继续使用固件提供的字体、图标、磁贴服务与部分资源，不能据此宣称所有细节与 Pixel 固件逐像素一致。

缺失预期目标时跳过相应 Hook；恢复失败时记录 `[Arashi-AospQs]` 并保留原方法回退。其他固件可能内联或删除不同入口，需要根据日志与样本继续适配。
