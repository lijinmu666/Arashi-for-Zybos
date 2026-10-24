# 桌面壁纸与 iOS 圆角兼容

两种桌面共享现有开关，按运行包名选择对应的 Hook 目标，不根据 Android SDK 版本猜测混淆类名

| 项目 | Launcher3 / Android 12 | Launcher14 / Android 14 |
| --- | --- | --- |
| 核对 APK | 桌面 7.13.2 | 桌面 7.10.2 |
| 包名 | `com.android.launcher3` | `com.zuoyebang.iot.pad.launcher14` |
| 壁纸快照协程 | `launcher.a1.invokeSuspend` | `launcher.c1.invokeSuspend` |
| 普通图标绘制 | `d8.q.a(Canvas, Rect)` | `g8.v.a(Canvas, Rect)` |
| 主题图标绘制 | 无对应的主题覆盖类 | `g8.p0.a(Canvas, Rect)` |
| 文件夹背景 | `folder.c0.c(Canvas)` | `folder.b0.c(Canvas)` |
| 文件夹预览绑定 | `folder.e0.h(folder.d0, k8.j)` | `folder.d0.h(folder.c0, n8.l)` |
| 最近任务绑定 | `TaskView.getTask()` | `TaskView.getTaskIdAttributeContainers()` |

`launcher.*` 表示 `com.zuoyebang.iot.pad.launcher.*`，`folder.*` 表示 `com.android.launcher3.folder.*`

两种桌面复用相同的连续圆角路径、应用原始图案与半透明文件夹底色，保留图标与角标的原有布局。Dock 的 `dock.j.a`、预置应用的 `bgKousuanPk` / `cardView` 已核对两版的 DEX 原始字段

Launcher3 的最近任务 IconView 继承 CardView，原逻辑会放大缓存图标再裁剪。启用时移除旧 CardView 圆角并清除不透明底色，将应用原图绘制到视图可见区域，关闭时恢复原圆角与底色。Launcher14 保持其分屏任务的独立图标绑定逻辑

验证：已核对 Launcher3 APK 的目标方法签名和字段类型，本地模拟检查覆盖两版文件夹绑定、任务复用、Launcher14 分屏图标及 Launcher3 CardView 圆角恢复。设备实际视觉效果仍需实测
