# 提交与版本索引

本索引按版本梳理开发记录。Git 原始提交保持不变，便于查看差异和回退。

## 未发布源码

- 开启静态作用域，包含 launcher14 和 launcher3；筛选按钮使用 Miuix 原生图标；选择器按进入时的勾选状态排序，点击不再立即移动条目。未编译 APK。
- 用户反馈通知图标数量限制解除、传统电源菜单和自定义主题色实测可用，具体其他固件仍需分别验证。

## v1.2.0（当前）

内部版本代码 47，本次 Actions 仅构建 Release APK。

- 卸载验证和 TCP 拦截通知使用普通响铃通知渠道，显式配置系统默认通知声音；升级切换到固定的新渠道，避免旧静音渠道沿用。后续仍遵循用户的通知渠道、音量与勿扰设置。

- 所有应用选择器新增 Miuix 原生下拉刷新，后台重新读取应用列表及图标，保留搜索、筛选和现有有效勾选，刷新后清理已卸载应用；失败可下拉重试。仅更新源码，尚

- 应用选择器筛选入口移至右上角，菜单仅保留“显示系统应用”，默认只显示用户应用；卸载黑白名单与后台保留白名单仍按已勾选优先排序，已隐藏项目的勾选保留。

- 新增“解除通知图标数量限制”，适配 Android 12 的布局内固定数量与 Android 14 的测量／布局字段，主机验证通过，详见 [通知图标](notification-icons.md)。

- 电池、WLAN、蓝牙已连接图标合并为“原版状态栏图标”一个开关，补齐旧设置迁移与新设置优先级检查，

- 新增“始终使用传统电源菜单”，统一长按电源键及下拉电源入口，默认关闭。两份 SystemUI 样本核对与 AOSP 12／14 主机测试通过，详见 [传统电源菜单](legacy-power-menu.md)。

- 新增 SystemUI 自定义主题色，配置入口位于“美化”；按 Android 12／14 样本覆盖快捷设置与厂商锁屏密码界面，含 RGB 校验、预览、黑白前景色及关闭回退。使用方法与验证范围见 [SystemUI 主题色](systemui-theme.md)。

## v1.1.0

内部版本代码 46。加载转圈恢复 Miuix 默认颜色，后台保留选择器补齐系统／用户应用标记；由 GitHub Actions 构建，产物文件名与实际 APK 版本统一为 1.1.0。

## v1.0.0

内部版本 45 及更早的开发记录如下，以提交标识具体变更，不改写原始历史

| 提交 | 内容与验证边界 |
| --- | --- |
| 内部版本 45 | Miuix 统一加载动画、完整 AOSP 应用信息页恢复及 AOSP 蓝牙已连接图标；由 GitHub Actions 构建，新增界面与 Hook 真机效果待验证 |
| 内部版本 44 | 针对 43 的应用详情闪退反馈撤回强制换 Activity，保留原宿主；增加控制器初始化后、视图安全时机的恢复与回归检查。缺少设备堆栈，待设备验证 |
| 内部版本 43 | 应用详情与通知设置合并为一个开关，沿用原详情开关状态；包含侧栏入口和延后初始化处理、两版通知恢复及主机检查，待设备验证 |
| 内部版本 42 | 原版样式应用详情操作栏，Android 12／14 样本适配及主机检查；详见[说明](app-details.md)，待真机验证 |
| 内部版本 41 | 主页激活状态改为大号对勾背景，包含此前入口排序、关于页仓库链接、指令箭头、选择器排序和失效应用自动取消选择，以及 Android 12 壁纸、录屏兼容修正；设备效果分别核验 |
| [1c391da](https://github.com/lijinmu666/Arashi-for-Zybos/commit/1c391da5b5b62af95e0385285d688dd70d72e703)、[b388de1](https://github.com/lijinmu666/Arashi-for-Zybos/commit/b388de1bc8dafb3addea2fee554dce4de58e584a) | 后续页面调整，保留现有代码 |
| [9d99393](https://github.com/lijinmu666/Arashi-for-Zybos/commit/9d9939308fcc4f93c317e76f0a72e1eb9d26e479) | 内部版本 40：改用 AOSP 原始 Wi-Fi 路径并覆盖新旧显示路径；Actions 成功，Android 12 / 14 用户反馈可用 |
| [75165f8](https://github.com/lijinmu666/Arashi-for-Zybos/commit/75165f8a4b10db78aefb5bad4e78bd98e28cd45d) | 内部版本 39：首次加入 Wi-Fi 样式开关；旧路径覆盖不足，后续 40 修复 |
| [6d7c12f](https://github.com/lijinmu666/Arashi-for-Zybos/commit/6d7c12f43033308149ed8bf70bd230714b3d772a) | 修复 TCP 添加服务器按钮间距并去掉嵌套说明卡片 |
| [4627568](https://github.com/lijinmu666/Arashi-for-Zybos/commit/4627568be4aa7082ff55ff86addb9f0edaeb2f9c) | 内部版本 38：多条 TCP 端点编辑及 AOSP 电池图标；Actions 成功，Android 14 电池图标用户确认生效 |
| [57b7e47](https://github.com/lijinmu666/Arashi-for-Zybos/commit/57b7e47d431718118110a51044bc88e534c03564) | 整理 v1.0.0 功能、兼容性及已知问题文档 |
| [809705e](https://github.com/lijinmu666/Arashi-for-Zybos/commit/809705e) | 包名与命名空间改为 com.lijinmu.arashi |
| [2a978dd](https://github.com/lijinmu666/Arashi-for-Zybos/commit/2a978dd) | 首次加入通知快捷回复的 QS 兼容路径，设备测试未解决问题 |
| [e7c013a](https://github.com/lijinmu666/Arashi-for-Zybos/commit/e7c013a) | 添加焦点诊断与恢复尝试，未解决根因，恢复尝试已被后续实现移除 |
| [83a26a6](https://github.com/lijinmu666/Arashi-for-Zybos/commit/83a26a6) | 保留活动回复面板，Android 14 用户已确认正常回复，Android 12 待实测 |
| [56c8530](https://github.com/lijinmu666/Arashi-for-Zybos/commit/56c8530) | TCP 与卸载记录改为直接清空、取消旧读取并重新加载，构建通过，实机效果待确认 |
| [bfe0e95](https://github.com/lijinmu666/Arashi-for-Zybos/commit/bfe0e95) | 调整功能名称、位置与说明，随后 Actions 已成功构建 |

同期功能还包括 TCP login.fail 拦截记录与独立通知、息屏拒绝卸载弹窗、Zybos 环境控制、导航可用性及两套桌面的图标适配，当前使用与兼容状态以 README 和专题文档为准

## v0.9.5 与 v0.9.4

补齐后台白名单显示动画、说明卡片布局、夜间模式、QQ群链接及未签名 release 构建入口，版本条目见[更新记录](../CHANGELOG.md)

## v0.9.3

- 新增 root 投屏开启、关闭快捷指令。
- 修复 Android 12 过早读取 PMS 静态字段引发的循环初始化、系统进程崩溃和 LSPosed 安全模式。
- 云端编译通过的代码提交：[ebeb21a3](https://github.com/lijinmu666/Arashi-for-Zybos/commit/ebeb21a3b4b5e94de0ca4c28e640e37d70ff9e92)。
- 随后的 [7bf5c384](https://github.com/lijinmu666/Arashi-for-Zybos/commit/7bf5c38402dae45d10b3c34b7217493277a7c348) 调整了投屏与安装页面文案，不能把此前 APK 视为包含这些文案的重新构建。

## v0.9.2

- 系统兼容性提示允许继续进入主界面。
- Android 12/12L 首次初始化默认开启兼容模式，保留后续手动选择。
- 新增 GitHub Actions，完成 SDK 工具与 Android 37.0 包名适配。
- 云编译环境修复均属于本版本的构建准备，没有单独提升应用版本。

## v0.9.1

同步当前源码与 Miuix 界面，整理源码目录、作用域检测、卸载验证、安装器及构建说明。

## v0.2.x

- v0.2.3：清理未使用代码、修复构建配置。
- v0.2.2：完善安装器、设置与项目说明；另有 Miuix 试验版本。
- v0.2.1：基础功能与 LSPosed 模块。
- 旧测试 APK 曾临时提交至仓库根目录，后续仓库整理已从主分支文件中移除，仍保留在原始历史中。

## 原始提交

以下列表按时间从新到旧排列，保留原始提交标题。后续提交使用中文，尽量将同一功能与必要修复组织成可独立审查的变更。

| 提交 | 内容 |
| --- | --- |
| [7bf5c384](https://github.com/lijinmu666/Arashi-for-Zybos/commit/7bf5c38402dae45d10b3c34b7217493277a7c348) | 更新 MiuixPages.kt |
| [ebeb21a3](https://github.com/lijinmu666/Arashi-for-Zybos/commit/ebeb21a3b4b5e94de0ca4c28e640e37d70ff9e92) | 启动 v0.9.3 云编译，产物使用提供的密钥本地签名 |
| [a0b48c43](https://github.com/lijinmu666/Arashi-for-Zybos/commit/a0b48c43de2cce4855ad9f56ec3ba8aa80fdf931) | 修复安卓12 PMS 循环初始化导致系统进程崩溃，保持 v0.9.3 暂不编译 [skip ci] |
| [0492b4c2](https://github.com/lijinmu666/Arashi-for-Zybos/commit/0492b4c2ca1159b6e9804e8912ca7865118e527a) | 更新至 v0.9.3，快捷指令新增投屏开关，暂不编译 [skip ci] |
| [523d5e23](https://github.com/lijinmu666/Arashi-for-Zybos/commit/523d5e2360a88de64081c6bd8083130d5b51df93) | 修正 Android SDK 37 的实际包名为 android-37.0 |
| [e4969a2b](https://github.com/lijinmu666/Arashi-for-Zybos/commit/e4969a2b01a38e65e6c41dade1de97d57a805118) | 保留依赖所需 SDK 37，更新云端 SDK 工具与包索引 |
| [1309fffb](https://github.com/lijinmu666/Arashi-for-Zybos/commit/1309fffb630527e61f8fe4e80765a1a8c14e6db9) | 修复云编译 SDK 不可用问题，改用 Android SDK 36 |
| [d3796341](https://github.com/lijinmu666/Arashi-for-Zybos/commit/d3796341aed5c2138148f11566789947905ab228) | 修复云编译 SDK 初始化，使用运行器预装工具 |
| [fc0243e8](https://github.com/lijinmu666/Arashi-for-Zybos/commit/fc0243e83e3edc22bace98afee4e1fcd868c14dc) | 更新至 v0.9.2 并配置 GitHub Actions 云编译 |
| [4b8d4367](https://github.com/lijinmu666/Arashi-for-Zybos/commit/4b8d4367a85f0803a8a6c67eac1be6822f6fb2e7) | 首次初始化时根据安卓12设置兼容模式默认值，保留用户后续选择 |
| [65c23714](https://github.com/lijinmu666/Arashi-for-Zybos/commit/65c23714e104d19d328c52de7e9451b55faa0d9c) | 调整系统兼容性提示，确认后允许继续使用 Arashi |
| [ac925c38](https://github.com/lijinmu666/Arashi-for-Zybos/commit/ac925c383cd7bf88824ca85f97ffd7fc377bd5c7) | 整理仓库并准备 v0.9.1 Release |
| [e217e08f](https://github.com/lijinmu666/Arashi-for-Zybos/commit/e217e08f914015c9ee46bb6bf325532237886e24) | 同步 Arashi v0.9.1 当前源码 |
| [f661206e](https://github.com/lijinmu666/Arashi-for-Zybos/commit/f661206ed26ba3833c66db11d1fbc83eea534db3) | 临时提供 0.2.3 与 Miuix 试验版已签名 APK |
| [380ce6c0](https://github.com/lijinmu666/Arashi-for-Zybos/commit/380ce6c053ebaea848ced1ff2aa57840457b2c66) | 更新 Arashi 0.2.3：清理未使用代码并修复构建配置 |
| [9d200c48](https://github.com/lijinmu666/Arashi-for-Zybos/commit/9d200c48b59536614fd17109df5e1982c0519169) | 更新 Arashi 0.2.2：完善安装器、设置与项目说明 |
| [cb2d9e39](https://github.com/lijinmu666/Arashi-for-Zybos/commit/cb2d9e3957324068fde42c8292356160a6831121) | 发布 Arashi 0.2.1：基础功能与 LSPosed 模块 |
| [566452ae](https://github.com/lijinmu666/Arashi-for-Zybos/commit/566452ae4f7cc19c853c8750982d3f033bb628e6) | Initial commit |
