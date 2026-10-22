# 录屏音源菜单与 Android 12 兼容

## 功能与作用域

现有“修复录屏声音选项”开关恢复 SystemUI 音源菜单，支持系统声音、麦克风、麦克风与系统声音混录。录音总开关关闭时应传递 NONE，不擅自开启麦克风。只需系统界面作用域，更新 Hook 后重启 SystemUI 或设备。

## 样本核对

核对 Google Drive 的“系统界面_12.apk”（27,278,017 字节，本地样本 MD5：`6d3aae2eed6d3d23797ca44648c688c4`）：

- `ScreenRecordDialog` 为 Activity，`mOptions` 是继承 Spinner 的 `RecordSpinnerZyb`；菜单的三种模式仍然存在。
- `requestScreenCapture()` 原始代码只按录音总开关选择 MIC_AND_INTERNAL 或 NONE，不读取所选模式。
- 启动链为 `RecordingService.getStartIntent(Context,int,int,boolean)` → `PendingIntent.getForegroundService` → 倒计时 → `RecordingService.onStartCommand`。
- 服务读取 `extra_useAudio` 后交给 `ScreenMediaRecorder`，样本中仍有单独 MIC、INTERNAL 与混录的准备、录音、合并与保存分支。

不能仅因这些分支存在就推断当前固件的音频驱动、编码器与 MediaStore 保存已正常。

## 本次修正

此前只在 getStartIntent 返回后修改音源。该辅助方法若被 ART 内联，修改可能被绕过，导致菜单选择未进入服务；这是需要防护的调用路径，未有设备日志证明它是全部失败的唯一原因。

本次保留原处理，同时在创建前台服务 PendingIntent 前校正音源，并反优化 requestScreenCapture，使内部辅助调用可被 Hook。只在当前音源选择请求的线程内、且 Intent action 与目标组件均匹配录屏启动时修改 `extra_useAudio`，不更改 resultCode、显示触摸、请求码、标志和用户上下文。不影响其他前台服务，不跨请求缓存用户选择，异常退出也清理线程状态。

诊断日志 `[Arashi-ScreenRecord]` 显示最终 PendingIntent 音源、服务收到的音源，以及录屏 prepare/start/end/save 的结果。后端异常原样抛回原流程，保留系统清理和提示，不伪装为成功。

## 验证与边界

`python3 tests/check_screen_recording.py` 执行真实 Hook 回调，以 Activity 和 Dialog 两种页面结构检查三种音源、NONE、关闭 Hook、无关目标、跳过辅助方法的模拟内联路径、请求异常清理和后端异常原样传播。这些主机检查不验证真实 AudioRecord、音频混流、视频编码或文件保存。

Android 14 由用户反馈可用；Android 12 本次源码修正尚未编译和实机验证。若仍失败，需提供具体模式、无法开始/无声音/结束后无法保存等现象，以及一次完整录屏的上述日志。应用最低版本仍为 Android 12。
