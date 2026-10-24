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

## 图标图层修复

自适应图标的前景与背景使用一致的原始图层缩放，再应用连续圆角遮罩，避免只放大一层导致图案偏小或边缘异常。保持应用原始图案，不替换为其他应用的图标

`python3 tests/check_ios_icon.py` 覆盖图层缩放、尺寸恢复与相关图标路径。当前图标开关默认关闭，启用或更新代码后强行停止桌面或重启设备再验证，不能仅根据应用内开关变化认定已刷新桌面缓存

## Android 12 壁纸服务兼容补充

用户反馈原壁纸支持在 Android 14 可用，Android 12 不可用。对提供的参考模块 `base.apk` 进行 DEX 核对后，确认其仅在系统框架中独立处理以下两个 boolean 方法，参数均为 String；调用前直接返回 true：

- `com.android.server.wallpaper.ZybWallpaperInjector.allowedSetWallpaper(String)`
- `com.android.server.wallpaper.WallpaperManagerService.isSetWallpaperAllowed(String)`

此前 Arashi 只处理第一个入口，本次补充第二个，并使用精确的 String 参数及 boolean 返回类型校验。两处 Hook 独立注册，单个目标缺失不会阻止另一处安装；均由现有“壁纸支持”开关控制，关闭时执行原方法。参考模块本身并未包含桌面背景、动态壁纸渲染或录屏保存的修复逻辑。

桌面透出系统壁纸的逻辑继续保留，不修改 Android 14 的快照与容器处理。需要系统框架和对应桌面作用域；更新本次系统服务 Hook 后应重启设备，仅重启桌面不足以加载 system_server 中的新 Hook。

本次仅按参考模块核对并补充源码，尚未编译或设备验证。它是针对缺失服务入口的兼容修正，不能据此认定 Android 12 静态和动态壁纸问题已全部解决。若仍失败，请提供实际固件、桌面版本及 `[Arashi-Restrict]` / `[Arashi-Wallpaper]` 日志。
