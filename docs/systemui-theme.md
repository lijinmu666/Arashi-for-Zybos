# SystemUI 自定义主题色

入口：Hook 功能设置 → 美化 → 自定义 SystemUI 主题色。默认关闭，需要在 LSPosed 勾选系统界面 `com.android.systemui` 作用域。

开启后点击“SystemUI 颜色”，选择预设或输入 `#RRGGBB`，预览并保存。六位十六进制色值可以带或不带 `#`，不接受透明度。颜色独立于 Arashi 应用自身的主题；修改颜色或开关后重启 SystemUI，或重启设备。关闭后重启可恢复固件原色。

## 着色范围

| 位置 | 自定义效果 |
| --- | --- |
| 下拉快捷设置 | 已开启快捷项的背景；文字、图标及副标题自动选取黑色或白色以保证对比度 |
| 下拉亮度条 | 普通亮度条的进度填充 |
| 作业帮锁屏密码页 | 数字键盘按下背景、按下时的数字前景、已输入密码的圆点 |

按键、密码校验和解锁流程继续使用系统原来的处理。此功能仅负责强调色，不改锁屏壁纸与身份验证。输入法由其自身应用绘制的键盘不在本 Hook 的着色范围内。

## 样本适配与实现

- Android 12 与 14 的快捷项均使用 `qs_selected_background`；`QSTileViewImpl` 的取色方法分别接收状态整数、状态整数加策略禁用标记。仅在开启状态替换前景，策略禁用及未开启状态沿用原来的颜色。
- `QSIconViewImpl.setIcon(ImageView, State, boolean)` 中按当前状态限制图标取色，避免把未开启快捷项、通知或其他图标一并改色。
- 两个样本的 `KeyguardPasswordViewZyb` 都使用 `zyb_keyguard_password_keyboard` 与 `zyb_keyguard_password_dot_bg` 选择器，选中分支内的蓝色为硬编码渐变。复制 Drawable 后仅修改目标状态的填充，保留圆角、尺寸和其他状态。
- 同时处理 XML 背景通过 `Resources.loadDrawable` 加载的路径；仅 Hook 公共 `getDrawable` 无法覆盖所有 XML 加载。按完整资源包名及名称匹配，不修改系统范围的调色板或共享 Drawable 缓存。
- 颜色整数通过本地偏好、LSPosed 远程偏好和 ContentProvider 两条配置通道传递；绘制热路径只读内存快照。

## 验证

`python3 tests/check_systemui_theme.py` 执行真实 Hook 回调，检查六位色值输入、4096 种 RGB 采样的黑白前景对比度、Android 12／14 方法签名、未开启及策略禁用状态、资源包归属、XML Drawable 加载、选择器原始对象不被污染、异常后恢复及关闭回退。

已静态核对所提供的 Android 12／14 SystemUI 样本。主机测试不包含真机绘制、动画和锁屏输入操作，本次源码更新尚未构建 APK。

## 与莫奈取色的关系

手动主题色只覆盖本文列出的 SystemUI 控件，不负责壁纸取色或系统动态调色板。另有“美化 → 修复莫奈取色”开关，用于恢复保留的原生取色流程。两项同时开启时，上述控件优先采用手动颜色，系统仍可生成壁纸配色。详见 [莫奈取色](monet.md)。
