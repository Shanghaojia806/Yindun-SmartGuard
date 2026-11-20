# P05 · Android 真实屏幕采集可行性验证报告（MediaProjection PoC）

- 版本：v0.1（P05 技术验证）
- 日期：2026-11-17
- 范围：仅验证「用户主动授权后，能否合规采集屏幕内容并正确处理启停与撤销」
- 结论：**Android 模拟器（Android 16 / API 36）上技术可行，全部 8 项测试通过；真实手机测试待验证。**

---

## 1. Android 版本

| 项目 | 值 |
| --- | --- |
| 模拟器系统版本 | Android 16（`ro.build.version.release=16`） |
| API Level | 36（`ro.build.version.sdk=36`） |
| 应用 compileSdk / targetSdk | 35 / 35 |
| minSdk | 26 |
| ABI | x86_64 |

> 说明：本轮在 API 36 模拟器上运行 targetSdk 35 的应用，已覆盖 Android 14+ 的 MediaProjection 前台服务强约束（先 `startForeground` 再 `getMediaProjection`、单次授权单会话）。真实手机的 Android 版本分布（Android 10–15）仍需逐一验证，见第 12 节。

## 2. 测试设备

| 设备 | 类型 | 结果效力 |
| --- | --- | --- |
| `medium_phone` AVD（`sdk_gphone64_x86_64`，1080×2400） | Android 模拟器 | 仅代表模拟器结果 |
| 真实 Android 手机 | — | **未提供，全部真机项标记为「待验证」** |

模拟器启动命令：

```text
%LOCALAPPDATA%\Temp\android-sdk\emulator\emulator.exe -avd medium_phone -no-snapshot-load -no-boot-anim
```

> 模拟器结果不能替代真机验收。V1.3 要求的真机「启动 / 停止 / 撤销 / 锁屏」四类记录目前均缺真机数据。

## 3. SDK 与 targetSdk

- JDK 21（`JAVA_HOME=C:\Program Files\Java\latest\jdk-21`）
- Gradle Wrapper 8.9，Android Gradle Plugin 8.7.3
- `compileSdk = 35`、`targetSdk = 35`、`minSdk = 26`
- 产物：`app/build/outputs/apk/debug/app-debug.apk`，11,174,995 字节（约 10.66 MB），versionName `0.1.0`

## 4. 权限配置

`AndroidManifest.xml` 新增（未申请无障碍、存储、网络业务权限）：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

| 权限 | 用途 | 备注 |
| --- | --- | --- |
| `FOREGROUND_SERVICE` | 前台服务基础权限 | 必需 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | mediaProjection 类型前台服务 | Android 14+ 必需 |
| `POST_NOTIFICATIONS` | 采集常驻通知与通知栏停止入口 | Android 13+ 运行时权限；拒绝后采集仍可进行，但通知栏入口可能不可见 |

MediaProjection 本身**不需要**静态权限，授权完全由系统弹窗产生。

## 5. MediaProjection 启动流程

页面路径：E03 守护首页 → E05 运行状态 → 「真实屏幕采集验证（P05 PoC）」→ 页面 P05。

```text
点击「开始真实采集（系统授权）」
  → 若 Android 13+ 且未授予 POST_NOTIFICATIONS：先请求通知权限（允许/拒绝都继续）
  → MediaProjectionManager.createScreenCaptureIntent() 弹出系统授权窗口
  → 用户在系统窗口选择「共享整个屏幕」并确认
  → startForegroundService(ScreenCaptureService, resultCode + data)
  → 服务内先 startForeground(notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  → getMediaProjection(resultCode, data)
  → registerCallback(MediaProjection.Callback)
  → createVirtualDisplay(720×N, ImageReader.surface)
  → ImageReader 收到帧：记录宽高 + 时间戳后立即 image.close()
  → 页面展示「运行中」、帧数、分辨率、首帧/最新帧时间、帧间隔
```

关键实现：

- 授权令牌只在系统窗口返回 `RESULT_OK` 后使用，应用无法自行构造；
- 授权被取消（`RESULT_CANCELED`）→ 回到「未开始」，提示「已取消系统授权，未开始采集」，**不视为系统故障**；
- 每次开始都必须重新走系统授权，不存在「一次授权永久后台采集」；
- VirtualDisplay 宽度限制为 720px（PoC 控内存），高度按屏幕比例缩放，均为偶数像素。

## 6. 前台服务配置

```xml
<service
    android:name=".guard.capture.ScreenCaptureService"
    android:exported="false"
    android:foregroundServiceType="mediaProjection"
    android:stopWithTask="true" />
```

| 项目 | 实现 |
| --- | --- |
| 服务类型 | `mediaProjection`（`types=0x00000020`，已用 `dumpsys` 核实） |
| 通知渠道 | `silver_shield_screen_capture`，IMPORTANCE_LOW，常驻、不可滑动清除 |
| 通知文案 | 「银盾智鉴 · 屏幕采集验证中」+「正在本地验证采集帧，不上传、不保存、不录像」 |
| 停止入口 | 通知栏「停止采集」按钮；应用内「停止真实采集」按钮；系统共享状态栏芯片 |
| 返回栈 | `exported=false`：外部应用/ADB 无法启动采集服务 |
| 任务移除 | `stopWithTask=true` + `onTaskRemoved` 兜底停止 |
| 应用退后台 | 采集继续（前台服务），系统状态栏显示共享图标与计时 |

## 7. 实际采集结果

模拟器实测（会话 1）：

| 指标 | 实测值 |
| --- | --- |
| 采集状态 | 运行中（真实 MediaProjection 会话） |
| 采集分辨率 | 720 × 1600 |
| 首帧时间 | 02:20:54（授权确认后约 1 秒） |
| 24 秒累计帧数 | 201 帧 |
| 最近两帧间隔 | 93 毫秒（画面变化驱动，非固定帧率） |
| 前台服务 | `isForeground=true`，`types=0x20`，通知含 1 个停止动作 |
| 后台切换 | 切到桌面 4 秒后返回，帧数继续增长，状态保持运行中 |

> VirtualDisplay 只在画面内容变化时产生新帧，静止页面帧数不会持续增长，页面已提示「切换页面或滑动屏幕」。

截图：[运行中](../previews/p05_03_running.png)、[通知栏](../previews/p05_04_notification.png)、[系统授权窗口](../previews/p05_02_system_dialog.png)

## 8. 停止与撤销结果

| 场景 | 操作 | 结果 |
| --- | --- | --- |
| 用户主动停止 | 点击页面「停止真实采集」 | 服务结束、通知移除、状态「已停止」，日志 `采集已停止：已停止屏幕采集。` |
| 通知栏停止 | 下拉通知点「停止采集」 | 与主动停止一致，服务记录消失，状态「已停止」 |
| 系统撤销 | 点击状态栏共享芯片 →「Stop sharing」 | `MediaProjection.Callback.onStop` 触发，状态「已停止」+「系统已结束屏幕共享，本次采集已停止。」，**不再显示运行中** |
| 任务移除 | 划掉任务（`stopWithTask`） | 服务停止，后台不残留采集 |
| 异常退出 | `am force-stop` 结束进程 | 服务与投影会话全部消失，通知被系统清除 |

资源释放顺序（`ScreenCaptureEngine.release()`，幂等）：

```text
停止取帧（listener 置空）
  → VirtualDisplay.release()
  → ImageReader.close()
  → unregisterCallback
  → MediaProjection.stop()
  → HandlerThread.quitSafely()
  → stopForeground(REMOVE) + stopSelf()
```

## 9. 锁屏测试结果

- 操作：采集运行中按电源键锁屏，保持 5 秒后亮屏解锁。
- 结果：前台服务仍为 `isForeground=true`（`types=0x20`），会话未中断；解锁后继续接收帧，状态保持运行中。
- 说明：本页不渲染采集画面，锁屏期间实际画面内容（是否黑屏、系统是否暂停帧）未做像素级验证；模拟器无锁屏密码，**真机锁屏（含安全锁）行为待验证**。

## 10. 音频及受保护内容限制

| 项目 | 本轮结论 |
| --- | --- |
| 音频采集 | **未实现**，本轮不采集任何音频 |
| AudioPlaybackCapture 限制 | 仅能捕获允许 `allowAudioPlaybackCapture` 的应用音频；短视频类应用通常不允许，且需要 `RECORD_AUDIO` 权限与 `AudioPlaybackCaptureConfiguration`；不能假设所有第三方应用都允许 |
| `FLAG_SECURE` 内容 | 系统对受保护窗口（支付、密码、部分视频）屏蔽采集，画面通常为黑屏；本轮**不尝试绕过**，页面明确提示 |
| 数据留存 | 帧仅在内存读取宽高/时间戳，读取后立即 `close()`；不落盘、不上传、不发给任何 AI 接口 |

## 11. 已知兼容性问题

1. **Kotlin 编译守护进程与中文路径**：增量构建偶发 `Kotlin daemon ... AccessDeniedException`（工程路径含中文）。处理方式：`-Pkotlin.compiler.execution.strategy=in-process`，构建恢复正常。属构建环境问题，不影响 APK 运行。
2. **Android 14+ 单会话令牌**：一次系统授权只能创建一个 VirtualDisplay；重新开始必须重新弹授权。当前实现已按此流程，不做令牌复用。
3. **Android 14/15 系统授权窗口默认「共享单个应用」**：自动化/用户需手动切换为「共享整个屏幕」，否则只能采集单个应用。本轮测试均为整屏共享。
4. **通知权限被拒绝**：前台服务仍运行，但通知栏可能不显示停止入口；页面已提示改用应用内停止。
5. **VirtualDisplay 帧率由内容变化驱动**：静止画面帧数不增长，不能以帧率判断存活；本轮以「服务状态 + 首帧后最新帧时间」判断。
6. **模拟器无安全锁屏/无真实第三方 App**：锁屏、FLAG_SECURE、第三方短视频应用场景需真机验证。
7. **PoC 分辨率固定 720 宽**：不随旋转/折叠屏动态调整，横竖屏切换后仍按启动时尺寸采集。

## 12. 是否具备进一步集成条件

**具备（限于模拟器验证范围内的技术可行性）。**

理由：

- 用户主动授权 → 授权窗口 → 前台服务 → 真实取帧 → 完整释放的闭环已跑通；
- 系统撤销（`onStop`）能正确打断采集并让 UI 离开运行中；
- 通知栏停止、任务移除、异常退出均无残留服务；
- 与 P04 模拟守护完全隔离：`FakeGuardService`、`GuardState`、`GuardRepository`、`GuardViewModel` 未修改，E03/E04/E05/E13/E14 导航不变；P05 页面常驻「真实采集 · PoC」标识，模拟页面常驻「模拟守护」，不存在互相冒充。

进入下一阶段前必须补齐：

1. 真实 Android 手机完成启动 / 停止 / 系统撤销 / 锁屏四项验收；
2. 明确真实采集状态是否、以及如何写入统一 `GuardStatus`（当前刻意不写入，避免真假状态混合）；
3. 明确息屏/后台长时间运行的功耗与系统回收策略；
4. 补齐旋转、折叠屏、分屏下的 VirtualDisplay 尺寸策略；
5. 音频与 FLAG_SECURE 仅记录限制，不纳入 V1.3 承诺。

---

## 附录 A：八项测试记录（模拟器）

| # | 测试 | 结果 | 证据 / 说明 |
| --- | --- | --- | --- |
| 1 | 正常授权 | ✅ 通过 | 系统窗口确认后 1 秒内收到首帧；720×1600，24 秒 201 帧，间隔 93ms（[截图](../previews/p05_03_running.png)） |
| 2 | 拒绝授权 | ✅ 通过 | 系统窗口点 Cancel → 页面「已取消系统授权，未开始采集」，`dumpsys` 无采集服务记录 |
| 3 | 主动停止 | ✅ 通过 | 页面停止 → 服务消失、通知移除、状态「已停止」+「已停止屏幕采集。」 |
| 4 | 系统撤销 | ✅ 通过 | 状态栏共享芯片 → Stop sharing → `onStop` 生效，状态「已停止」+「系统已结束屏幕共享…」（[撤销弹窗](../previews/p05_06_system_revoke.png)） |
| 5 | 应用切换 | ✅ 通过 | 切桌面 4 秒再返回：服务保持前台，帧数继续增长，状态仍为运行中 |
| 6 | 锁屏 | ⚠️ 模拟器通过 / 真机待验证 | 锁屏 5 秒服务存活，解锁后继续取帧；安全锁屏与画面内容未验证 |
| 7 | 重复启动 | ✅ 通过（防护有效） | 运行中页面只显示「停止」；状态机 `beginPermissionRequest` 重复调用返回 false（单测覆盖）；服务内 `engine != null` 直接忽略；ADB 外部启动被 `exported=false` 拒绝 |
| 8 | 异常退出 | ✅ 通过 | `am force-stop` 后服务与投影会话消失；重启 App 进入 P05 显示「未开始」，无残留运行状态 |

真机待验证清单：1、3、4、6 的 Android 10 / 12 / 14 / 15 真机复测，以及 FLAG_SECURE 第三方应用黑屏行为。

## 附录 B：构建与单元测试

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-21"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug `
  "-Pkotlin.compiler.execution.strategy=in-process" --console=plain
```

- 结果：`BUILD SUCCESSFUL`
- 单元测试：40 项，0 失败 / 0 错误 / 0 跳过（P04 27 项 + P05 新增 13 项）
- 新增测试文件：`app/src/test/java/com/silvershield/app/guard/capture/ScreenCaptureStateTest.kt`
- 覆盖：初始状态、授权同意/拒绝、重复启动防护、取帧统计与间隔、停止幂等、系统撤销、异常态、页面销毁清理、重新开始清空统计

## 附录 C：实现文件

| 文件 | 职责 |
| --- | --- |
| `guard/capture/ScreenCaptureState.kt` | 采集阶段、帧统计、会话快照（纯 Kotlin） |
| `guard/capture/ScreenCaptureCoordinator.kt` | 进程级状态持有者 `ScreenCaptureSession`，线程安全 |
| `guard/capture/ScreenCaptureEngine.kt` | MediaProjection + VirtualDisplay + ImageReader 取帧与释放 |
| `guard/capture/ScreenCaptureService.kt` | mediaProjection 类型前台服务、通知、onStop、任务移除 |
| `guard/capture/ScreenCaptureController.kt` | 真实采集控制接口与 MediaProjection 实现 |
| `guard/capture/ScreenCaptureViewModel.kt` | P05 页面状态与一次性提示 |
| `ui/elder/ScreenCapturePocScreen.kt` | P05 验证页（真实采集标识、指标、启停、限制说明） |
| `AndroidManifest.xml` | 三项权限 + 服务声明 |
| `navigation/AppRoutes.kt`、`ElderSubPage.kt`、`ElderAppScreen.kt` | 新增二级页路由，E05 增加入口 |
| `ElderRunStatusScreen.kt` | 仅新增 P05 入口与「模拟守护不采集屏幕」说明，原有页面逻辑未改 |

P04 的 `FakeGuardService`、`GuardState`、`GuardRepository`、`GuardViewModel` 文件未做任何修改。
