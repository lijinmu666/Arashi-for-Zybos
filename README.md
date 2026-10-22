# Arashi

Arashi 是面向作业帮学习机 AIBangOS 的 LSPosed 模块和系统工具，界面使用 Miuix。目前版本为 `v1.2.0`，内部版本代码为 `47`。

系统内部相关组件使用 Zybos 命名。安装、卸载等特权工具依赖设备提供的 `ZybosSysApiManager`；不同固件的接口和限制可能存在差异。

应用包名为 `com.lijinmu.arashi`，模块入口为 `com.lijinmu.arashi.xposed.HookEntry`。旧包名 `com.arashi` 与当前包名属于不同应用，不能原地覆盖，设置不会自动迁移

## 版本记录

- [更新记录](CHANGELOG.md)
- [提交与版本索引](docs/HISTORY.md)
- [作用域、兼容性与已知问题](docs/compatibility.md)
- [原版样式应用详情页](docs/app-details.md)
- [通知快捷回复修复](docs/notification-reply.md)
- [桌面壁纸与 iOS 圆角](docs/launcher-compatibility.md)
- [AOSP 电池、Wi-Fi 与蓝牙图标](docs/aosp-icons.md)
- [SystemUI 自定义主题色](docs/systemui-theme.md)
- [传统电源菜单](docs/legacy-power-menu.md)
- [解除通知图标数量限制](docs/notification-icons.md)
- [导航与下拉面板限制拦截](docs/navigation-access.md)
- [记录、通知与环境工具](docs/records-and-tools.md)

## 下载与使用

下载入口：
- [GitHub Releases](https://github.com/lijinmu666/Arashi-for-Zybos/releases)
- [GitHub Actions](https://github.com/lijinmu666/Arashi-for-Zybos/actions)
- [官方 QQ 群聊](https://qun.qq.com/universal-share/share?ac=1&authKey=i%2Bh3ZyTelsMfhjWvc8srNCHnS4BMGrsr4hj1fDcEXx0fC5ECgHyUjh45kS5qiQ3W&busi_data=eyJncm91cENvZGUiOiIxMTI1MTkwMzUwIiwidG9rZW4iOiIrRFg2KzdBTHhRRlZKODZjUUt2U2gySFRBaHFBeFNIYTdFTFc0emcydXJuTEZsN1dvS3MwT1J4ZFF2dnNJNUhOIiwidWluIjoiODQxNTYzNjAifQ==&data=PB0LIOhizgFR_GzQFzdDn6FEbw0rzkzMw5JbqCzMZp0t72lHOOiSfSqMcISpvsGOvjkcACA_44t9aKGSV3GaNQ&svctype=4&tempid=h5_group_info)

Actions 提供测试签名 APK 和未签名 release APK。未签名包需自行签名后安装，覆盖安装必须保持包名与签名一致

1. 安装 Arashi，在支持 libxposed API 102 的 LSPosed 中启用模块。
2. 启用模块并确认所需作用域，然后重启设备。模块使用静态作用域，桌面同时声明 `com.zuoyebang.iot.pad.launcher14` 和 `com.android.launcher3`，覆盖对应 Android 14／12 桌面。
3. 在“Hook 功能设置”中启用所需功能。系统框架按本次开机的 Hook 状态检测，应用作用域在设置页实时读取。

最低 Android 版本为 Android 12（API 31）。Hook 针对 AIBangOS 的相关实现，不保证其他系统或所有固件兼容。Android 12 与 14 的兼容路径及实测状态见[兼容性说明](docs/compatibility.md)

## 功能

主界面入口依次为：Hook 功能设置、快捷指令执行、卸载验证设置、系统特权工具、应用偏好设置、关于 Arashi。

### Hook 功能设置

- 安装：外部应用安装支持、Shell 安装支持、清除安装黑名单。
- 行为：开发者选项支持及设置侧边菜单注入、保留后台进程、USB 文件传输、壁纸设置、WiFi 网页登录、解除应用详情页限制、原版应用详情与通知设置、修复录屏声音选项、修复通知快捷回复、阻止重设默认浏览器、阻止禁用导航与下拉面板、始终使用传统电源菜单
- 美化：SystemUI 自定义主题色、原版状态栏图标（统一控制电池、WLAN 和蓝牙已连接）、解除通知图标数量限制、iOS 风格桌面图标圆角、文件夹与最近任务图标适配，以及状态栏时间后显示日期
- TCP 服务：屏蔽 ZpTcpService 发出的 `login.fail` 广播，保留拦截记录与通知，不改变服务器端设备状态

保留后台进程默认关闭，管控服务类 Hook 默认关闭，默认值不会覆盖已有设置。保留后台进程支持应用白名单，应用列表与卸载黑白名单一样在包名后标注“系统应用”或“用户应用”。开启开关后可在下方选择应用；未选择时默认保留全部，选择后仅保留选中的应用。微信、QQ、护眼、语音助手和时钟沿用固件原有豁免，不显示在选择器中。关闭开关会隐藏入口并保留选择。

应用选择器统一使用右上角的Miuix 原生 Filter 筛选图标，弹出菜单仅有“显示系统应用”一项，默认关闭、只显示用户应用。原先的分类筛选菜单已移除；每次进入时按已勾选优先排序，当前页面勾选或取消不会立即改变位置，重新进入后更新排序，隐藏系统应用不会取消已有勾选。所有应用选择器支持 Miuix 原生下拉刷新，重新读取应用列表及图标，保留搜索与筛选，自动清理已不存在应用的勾选；读取失败可再次下拉重试。
- 管控：阻止管控服务、应用禁用失效、页面屏蔽失效及时间限制相关功能。

未检测到对应作用域时，相关开关不可用并保留原设置。系统框架 Hook 的变更需要重启设备，应用作用域的变更需要重启目标应用。

AOSP 电池、Wi-Fi 与蓝牙图标开关默认关闭，只需系统界面作用域，启用后重启 SystemUI。Wi-Fi 图标在内部版本 40 改用 AOSP 原始路径，Android 12、14 均已获用户反馈可用；Android 14 电池图标已确认生效，Android 12 电池图标仍待单独确认。蓝牙已连接图标完成 Android 12／14 样本核对与主机检查，真机效果待验证。详见[图标说明](docs/aosp-icons.md)。

“原版应用详情与通知设置”位于“行为”，默认关闭，作用域为设置。按 Android 12／14 样本接回完整 AOSP 应用信息偏好树与控制器，包括原版头部、打开／卸载或停用／强行停止、通知、权限、存储、数据用量、电池、默认打开与高级权限；Android 12 补齐被删减的项目，具体可见性由系统支持情况和管理策略决定。保留设置侧栏“应用与存储”的原始宿主和启动参数。开关变更后重新打开详情页，本次整页恢复的真机效果待验证。详见[应用详情说明](docs/app-details.md)。

应用通知设置也由同一个开关控制，沿用原应用详情开关的值。Android 14 使用保留的原版应用通知布局及 Block 控制器；Android 12 恢复原版信息头部、主开关，沿用原通知操作与渠道列表。更改后重新打开应用通知页，尚待真机验证。

### 卸载验证

当应用或用户发起卸载时，询问是否允许本次卸载，并展示发起方名称与 UID、目标应用图标、名称和包名。

- 黑白名单匹配的是发起卸载的应用包名或 UID，而非被卸载的应用。
- 白名单直接允许，黑名单直接拒绝；同时命中时黑名单优先。
- 名单使用独立选择页面，支持应用图标、搜索，以及手动输入包名或 UID。默认只显示用户应用，右上角开启“显示系统应用”后同时显示系统应用与常用 UID。已勾选项排在未勾选项前，同组内按用户应用、系统应用、UID 及名称排序。手动添加系统应用或 UID 后会自动打开显示，便于查看和取消选择。
- 验证倒计时必须选择 5、10、15、30 秒之一，默认 10 秒，超时默认拒绝。
- 伪装卸载结果默认关闭。开启后仍拦截拒绝的卸载请求，但向请求方返回卸载成功，记录仍保留真实的拒绝结果。
- 设备息屏时，需要弹窗确认的卸载请求默认拒绝，已弹出的请求也会被拒绝；黑白名单规则不受影响
- 允许和拒绝的请求均可在卸载记录中查看，记录支持分页、刷新和清空
- 清空先取消旧读取任务，删除后重新读取数据库，显示删除数量或失败原因；当前修改已通过构建，实机清空效果待确认
- 未检测到本次开机的系统框架 Hook 时，卸载验证暂时关闭；重新检测到激活后恢复此前的开启状态。

### TCP 记录与通知

TCP 拦截记录保存可获取的错误码、来源、进程及调用信息，支持查看详情、复制、分页、下拉刷新与清空。通知选项统一在应用偏好设置中管理。只有成功拦截 `login.fail` 后才发送通知，点击进入记录页

“应用偏好设置 → 通知设置”分别控制 TCP、卸载黑名单拒绝、白名单放行、超时拒绝及息屏拒绝通知，通知开关默认开启，实际发送仍需系统通知权限。通知开关不影响拦截规则

### 系统特权工具与快捷指令

系统特权工具与 Arashi 安装器仅支持 `ro.build.version.os` 小于 `V7.10.0` 的版本，达到或超过该版本会禁用入口与安装器组件，并显示不支持提示。这项限制不同于外部安装、Shell 安装等 Hook

在受支持版本中，通过 Zybos 系统接口安装 APK、按包名卸载或使用应用选择器卸载，并显示执行结果。选择器支持图标、搜索及右上角“显示系统应用”菜单；卸载选择器仅在开启显示后列出已更新的系统应用，继续过滤未安装更新的系统应用。

快捷指令提供冻结应用、打开设置，以及开发者选项、ADB 等设置的开启和关闭操作。投屏开启、关闭指令通过 root 切换无线显示并请求 SystemUI 添加或移除投屏磁贴

冻结类指令使用 `pm disable`，包括 PadMs、report、系统升级服务与桌面 ErrorActivity；ErrorActivity 只禁用对应组件，不禁用整个桌面

Zybos 环境控制用于读取、格式化展示、写入或重置 `persist.sys.env_config`，需要 root。环境菜单为 `PRODUCTION`、`TEST`、`MOCK`；两个调试开关每次新进入页面均关闭，不从系统属性恢复开关状态。环境变更可能影响登录和激活，详见[工具说明](docs/records-and-tools.md)

### 应用安装器

支持从文件管理器等应用的“打开方式”选择 Arashi，接收 `VIEW`、`INSTALL_PACKAGE` 和 `SEND` 安装请求。解析 APK 后展示应用信息并确认安装。

确认后立即显示 Miuix 无限进度条，后台执行安装并核对安装结果。无法直接使用的文件来源会尝试暂存到共享存储，读取共享存储时需要所有文件访问权限。

### 应用偏好设置

夜间模式提供“默认、开启、关闭”，配色可选 Arashi 默认紫色或 Miuix 默认蓝色。首次安装首选紫色，另有通知设置、隐藏桌面入口和安装器启用状态等设置。未检测到激活时隐藏桌面图标选项不可用。

兼容设置包含“兼容安卓12清除安装黑名单”，用于切换不同固件的 Hook 路径；是否启用功能仍由对应功能开关控制。

签名检查在主界面全新启动时进行，从后台返回不重复检查。与原作者签名不匹配时显示提示，确认后仍可使用。用户反馈提示关闭后持续卡顿，尚未定位原因，不能视为已修复

## 项目结构

```text
app/src/main/java/com/lijinmu/arashi/
  about/       关于页面
  notifications/ 通知权限、偏好与卸载通知
  security/    签名检查
  settings/    设置入口
  tcp/         TCP 拦截记录、通知与页面入口
  tools/       特权工具、选择器、快捷指令和环境控制
  ui/          界面辅助组件
  uninstall/   卸载验证设置、名单和记录
  xposed/      模块入口、配置同步、作用域检测和 Hook
  zybos/       Zybos 系统服务调用
app/src/main/kotlin/com/lijinmu/arashi/ui/
               MiuixPages、TCP 记录与应用选择器等 Compose 页面
tests/         Hook 与兼容规则的主机侧检查
docs/          兼容性、功能说明与提交索引
```

## 许可证

Arashi 版权所有 © 2026 lijinmu666，采用 GNU General Public License v3.0 only（SPDX：`GPL-3.0-only`）授权，详见 [LICENSE](LICENSE)。

卸载验证与 TCP 拦截提醒使用普通响铃通知及系统默认通知声音，遵循系统音量、勿扰和用户的渠道设置。
