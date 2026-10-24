# 提交与版本索引

本索引按版本梳理开发记录。Git 原始提交保持不变，便于查看差异和回退。

## v0.9.3

- 新增 root 投屏开启、关闭快捷指令。
- 修复 Android 12 过早读取 PMS 静态字段引发的循环初始化、系统进程崩溃和 LSPosed 安全模式。
- 云端编译通过的代码提交：[ebeb21a3](https://github.com/lijinmu666/Arashi/commit/ebeb21a3b4b5e94de0ca4c28e640e37d70ff9e92)。
- 随后的 [7bf5c384](https://github.com/lijinmu666/Arashi/commit/7bf5c38402dae45d10b3c34b7217493277a7c348) 调整了投屏与安装页面文案，不能把此前 APK 视为包含这些文案的重新构建。

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
| [7bf5c384](https://github.com/lijinmu666/Arashi/commit/7bf5c38402dae45d10b3c34b7217493277a7c348) | 更新 MiuixPages.kt |
| [ebeb21a3](https://github.com/lijinmu666/Arashi/commit/ebeb21a3b4b5e94de0ca4c28e640e37d70ff9e92) | 启动 v0.9.3 云编译，产物使用提供的密钥本地签名 |
| [a0b48c43](https://github.com/lijinmu666/Arashi/commit/a0b48c43de2cce4855ad9f56ec3ba8aa80fdf931) | 修复安卓12 PMS 循环初始化导致系统进程崩溃，保持 v0.9.3 暂不编译 [skip ci] |
| [0492b4c2](https://github.com/lijinmu666/Arashi/commit/0492b4c2ca1159b6e9804e8912ca7865118e527a) | 更新至 v0.9.3，快捷指令新增投屏开关，暂不编译 [skip ci] |
| [523d5e23](https://github.com/lijinmu666/Arashi/commit/523d5e2360a88de64081c6bd8083130d5b51df93) | 修正 Android SDK 37 的实际包名为 android-37.0 |
| [e4969a2b](https://github.com/lijinmu666/Arashi/commit/e4969a2b01a38e65e6c41dade1de97d57a805118) | 保留依赖所需 SDK 37，更新云端 SDK 工具与包索引 |
| [1309fffb](https://github.com/lijinmu666/Arashi/commit/1309fffb630527e61f8fe4e80765a1a8c14e6db9) | 修复云编译 SDK 不可用问题，改用 Android SDK 36 |
| [d3796341](https://github.com/lijinmu666/Arashi/commit/d3796341aed5c2138148f11566789947905ab228) | 修复云编译 SDK 初始化，使用运行器预装工具 |
| [fc0243e8](https://github.com/lijinmu666/Arashi/commit/fc0243e83e3edc22bace98afee4e1fcd868c14dc) | 更新至 v0.9.2 并配置 GitHub Actions 云编译 |
| [4b8d4367](https://github.com/lijinmu666/Arashi/commit/4b8d4367a85f0803a8a6c67eac1be6822f6fb2e7) | 首次初始化时根据安卓12设置兼容模式默认值，保留用户后续选择 |
| [65c23714](https://github.com/lijinmu666/Arashi/commit/65c23714e104d19d328c52de7e9451b55faa0d9c) | 调整系统兼容性提示，确认后允许继续使用 Arashi |
| [ac925c38](https://github.com/lijinmu666/Arashi/commit/ac925c383cd7bf88824ca85f97ffd7fc377bd5c7) | 整理仓库并准备 v0.9.1 Release |
| [e217e08f](https://github.com/lijinmu666/Arashi/commit/e217e08f914015c9ee46bb6bf325532237886e24) | 同步 Arashi v0.9.1 当前源码 |
| [f661206e](https://github.com/lijinmu666/Arashi/commit/f661206ed26ba3833c66db11d1fbc83eea534db3) | 临时提供 0.2.3 与 Miuix 试验版已签名 APK |
| [380ce6c0](https://github.com/lijinmu666/Arashi/commit/380ce6c053ebaea848ced1ff2aa57840457b2c66) | 更新 Arashi 0.2.3：清理未使用代码并修复构建配置 |
| [9d200c48](https://github.com/lijinmu666/Arashi/commit/9d200c48b59536614fd17109df5e1982c0519169) | 更新 Arashi 0.2.2：完善安装器、设置与项目说明 |
| [cb2d9e39](https://github.com/lijinmu666/Arashi/commit/cb2d9e3957324068fde42c8292356160a6831121) | 发布 Arashi 0.2.1：基础功能与 LSPosed 模块 |
| [566452ae](https://github.com/lijinmu666/Arashi/commit/566452ae4f7cc19c853c8750982d3f033bb628e6) | Initial commit |
