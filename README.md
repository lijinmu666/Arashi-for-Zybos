# Arashi

Arashi 是面向作业帮学习机 AibangOS 的 LSPosed 模块和系统工具。

AibangOS 系统内部使用 **Zybos** 作为相关系统组件名称。本项目目前主要围绕 `ZybosSysApiManager` 实现应用安装与卸载功能。

## 当前功能

### 通过 ZybosSysApiManager 安装和卸载应用

Arashi 连接 AibangOS 提供的 `ZybosSysApiManager`，调用其系统接口执行：

- 按应用包名发起卸载
- 按 APK 文件路径发起安装

安装和卸载请求由 `ZybosSysApiManager` 处理，不需要通过 `adb root` 等特殊系统权限来完成具体操作。

### 注册为系统安装器

Arashi 可以注册为系统 APK 安装器，接收系统发出的 APK 安装请求。

使用方式与系统软件包安装程序类似：

- 从文件管理器或其他应用打开 APK
- 在系统弹出的应用选择界面中选择 Arashi
- Arashi 读取 APK 信息
- 通过 `ZybosSysApiManager` 发起安装

目前支持系统安装器常见的 APK 打开方式，包括 `VIEW`、`INSTALL_PACKAGE` 和 `SEND` 请求。

### 安装器开关

可以在 Arashi 设置中启用或禁用 Arashi 安装器。

禁用后，Arashi 仍可通过 LSPosed 管理器的模块设置入口打开。

## Hook 功能

Arashi 通过 LSPosed 对 AibangOS / Zybos 相关系统组件进行 Hook。当前已接入以下功能开关：

- **外部应用安装支持**：改善软件包安装程序对外部来源应用安装请求的处理
- **Shell 安装支持**：放宽 ZybOS 对 Shell / `pm install` 安装请求的限制
- **清除内置黑名单**：清理系统安装组件中的部分硬编码应用黑名单
- **Wi-Fi 登录支持**：调整认证型 Wi-Fi 登录流程相关判断
- **隐藏 ADB 状态**：让普通应用读取开发者选项和 ADB 状态时得到隐藏后的结果

Hook 功能在 Arashi 的“Hook 功能设置”中单独控制。启用模块后，需要在 LSPosed 中选择对应作用域；部分作用于 `system_server` 的 Hook 需要重启系统后生效。


## 适用范围

- AibangOS 系统
- 系统内部提供 `ZybosSysApiManager` 的设备
