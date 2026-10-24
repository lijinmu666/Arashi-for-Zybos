# 导航与下拉面板限制拦截

- 开关：行为 → 阻止禁用系统导航与下拉面板
- 默认关闭，切换后重启系统
- 作用域：系统框架、系统界面、设备对应桌面（Launcher 12 或 Launcher 14）

## 拦截路径

1. Android 12/14 `StatusBarManagerService.disableLocked(displayId, userId, what, token, pkg, which)`：仅移除 EXPAND、HOME、BACK、RECENT、SEARCH，以及 DISABLE2_QUICK_SETTINGS、DISABLE2_NOTIFICATION_SHADE，保留其他标志、原有权限检查、状态保存和分发
2. SystemUI `CommandQueue.disable`：同步过滤以上禁用位，处理来自系统的导航状态
3. 仅在 SystemUI 进程读取 `Settings.Global.control_panelbar` 时返回 0，不修改实际数据库
4. 仅在导航、通知面板与快捷设置的调用栈中放行初始化完成查询，不更改真实 `device_provisioned`、`user_setup_complete`，不全局修改锁屏的初始化判断
5. `PhoneWindowManager`：仅在导航按键路径中放行初始化完成查询，保留其锁屏检查
6. 已提供的 Launcher 12（7.13.2）与 Launcher 14（7.10.2）：分别恢复 `com.android.launcher3.g5.s/t`、`g6.s/t`，在初始化、ErrorActivity、最近任务及桌面触摸处理时防止这些本地门控继续拦截

缺失的可选目标会单独记录，不阻止其他目标安装。桌面混淆类名是样本版本特有的，后续版本需要重新验证

## 验证

`python3 tests/check_navigation_access.py` 执行真实 Hook 回调，覆盖两套桌面、开关关闭时放行、未知标志组、无关状态位、参数/原数组保留、设置键筛选、初始化判断的调用范围

仍需在设备上验证激活页面与 ErrorActivity 的按键、手势及下拉操作。该功能不改变云端状态，不完成激活，也不移除 ErrorActivity 的自动重开逻辑，因此主页可能仍回到原有激活/错误页面。它不用于保持导航条常驻，普通应用的沉浸式显示行为仍由系统处理
