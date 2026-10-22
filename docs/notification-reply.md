# 通知快捷回复修复

- 入口：行为 → 修复通知快捷回复
- 默认关闭，仅需要系统界面 `com.android.systemui` 作用域，不依赖系统框架
- 初次启用模块或更新 Hook 代码后，重启 SystemUI 或重启设备；同一版已安装 Hook 时，功能开关实时读取

## 问题与处理

Android 14 的设备日志确认：`RemoteInputView.focus()` 成功后，下一次通知布局通过 `NotificationContentView.selectLayout → updateViewVisibilities → NotificationHeaderViewWrapper.setVisible` 隐藏回复框所在面板，随后触发失焦关闭

当前在 `NotificationContentView.calculateVisibleType()` 中保留正在输入的展开或悬浮面板，避免高度重算将其切换成无法承载回复框的折叠类型。正常展开面板与悬浮面板之间仍由系统转移焦点

仅当功能开启、通知内容可见、回复框处于输入状态且不在发送中时处理；用户手动展开或收起时保留原计算结果。未修改回复消息内容或发送目标，也不跳过应用自身的发送处理

早期方案仅处理横屏远程输入触发的快捷设置禁用位，设备验证不足以修复问题；第二次尝试恢复输入框焦点，同样未解决隐藏面板的原因。后者已从当前实现移除，保留有针对性的布局处理及早期 QS 兼容路径

## 兼容与验证

| 系统 | 对应实现 | 验证状态 |
| --- | --- | --- |
| Android 14 / SystemUI 7.10 样本 | 布局选择修复与 R8 内联的 QS 回调兼容 | 用户已确认可以正常输入并回复消息 |
| Android 12 / 已提供的两份样本 | 相同布局方法与回复状态接口，旧版 `adjustDisableFlags` 路径 | 静态核对及主机侧检查通过，未获得设备实测确认 |

`python3 tests/check_notification_reply.py` 检查实际 Hook 回调，包括默认关闭、原禁用位保留、异常恢复、活动回复面板、发送中、隐藏页面及用户展开手势等分支。模拟检查不能代替设备验证

启用功能时，`[Arashi-Reply]` 日志保留焦点、可见性及关闭调用栈，不记录回复文本。若仍复现，请同时提供系统版本、SystemUI 版本与复现日志
