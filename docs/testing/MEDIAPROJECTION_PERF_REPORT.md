# P05 性能优化补丁：后台采集卡顿治理报告

- 版本：P05 性能优化补丁（不进入 P06）
- 采集实现：真实 `MediaProjection` + `VirtualDisplay` + `ImageReader`（未回退 FakeGuardService）
- 测试设备：Android 模拟器 `sdk_gphone64_x86_64`（API 35，1080×2400 屏幕，4 核）
- 采集分辨率：720×1600（按 `MAX_CAPTURE_WIDTH = 720` 等比缩放）
- 待真机复测：华为 Mate 60 Pro（HarmonyOS 7.0.0.111，卓易通兼容环境，720×1554）

> 说明：本文所有数字均来自本轮 adb 实测（`logcat` / `top` / `dumpsys meminfo` / `dumpsys activity services` / `dumpsys media_projection`）。
> 模拟器不具备真机 GPU 与调度行为，因此「整机流畅度」只能用可量化代理指标衡量，不能代表 Mate 60 Pro 的最终体验，真机数据仍需补测。

---

## 一、性能瓶颈分析（先定位，后修改）

优化前链路：`ImageReader.onImageAvailable` → 每帧回调 → `ScreenCaptureSession`（进程级 `MutableStateFlow`）→ `ViewModel.combine().stateIn()` → Compose `LazyColumn` 全量重组。

| # | 瓶颈 | 证据 / 影响 |
|---|------|------------|
| 1 | **没有处理层节流**：取到一帧就上报一帧 | 真机约 54 FPS（约 2 秒 108 帧），业务处理频率 = 原始帧频率，CPU 与状态流压力随系统刷新率线性增长 |
| 2 | **状态流每帧发射** | `ScreenCaptureStatus` 每秒被替换约 54 次，`MutableStateFlow` 比较 + 分配持续发生，后台也在跑 |
| 3 | **UI 每帧重组** | `combine(...).stateIn` 每帧产生新 `ScreenCaptureUiState`，整页 Compose 重组 ≈54 次/秒；页面不可见时仍持续产生状态，切后台后整机瞬卡 |
| 4 | **缓冲与释放策略未显式约束** | `maxImages` 未做上限说明、未区分「被丢弃帧」，无法证明缓冲区不堆积 |
| 5 | **缺少原始帧/处理帧分离统计** | 只有 1 个帧计数，无法量化「系统产生了多少帧 / 我们处理了多少帧」，优化效果不可验证 |

结论：卡顿主因不在「取帧」本身（取帧已在线程中即时 `close()`），而在**没有业务处理频率上限**，导致状态流与 Compose 以系统刷新率工作。因此优化重点是**处理层节流 + UI 降采样**，而不是降低采集分辨率或关闭采集。

---

## 二、修改文件清单

| 文件 | 改动 |
|------|------|
| `apps/android/app/src/main/java/com/silvershield/app/guard/capture/ScreenCaptureEngine.kt` | 重写：独立 `HandlerThread` 取帧；`SystemClock.elapsedRealtimeNanos()` 单调时钟节流（默认 5 FPS，可 10 FPS）；`maxImages=2` + `acquireLatestImage()`；被跳过帧在 `finally` 中 `close()`；原始/处理/丢弃分开计数，每 5 秒输出实测日志；释放顺序固化 |
| `.../guard/capture/ScreenCaptureState.kt` | 重写：新增 `DEFAULT_CAPTURE_FPS=5`、`TEST_CAPTURE_FPS=10`、`CapturePhase`、`CaptureFrameSnapshot`、`CaptureFrameStats`（raw/processed/dropped、平均原始 FPS、平均处理 FPS） |
| `.../guard/capture/ScreenCaptureService.kt` | 读取 `EXTRA_TARGET_FPS`（1..30 收敛）并透传给引擎；启动日志打印处理频率 |
| `.../guard/capture/ScreenCaptureCoordinator.kt` | `onFrame(CaptureFrameSnapshot)` 新签名；记录 `targetFps`；原始/处理统计写入同一状态 |
| `.../guard/capture/ScreenCaptureController.kt` | 接口增加 `targetFps` 参数并透传 |
| `.../guard/capture/ScreenCaptureViewModel.kt` | 新增 1 秒 UI 采样（`STATS_UI_INTERVAL_MS=1000`，阶段变化立即上屏）；`WhileSubscribed(5_000)`；新增 `setTargetFps`（采集中拒绝切换） |
| `.../ui/elder/ScreenCapturePocScreen.kt` | 新增「处理频率（性能测试）」5 FPS / 10 FPS 选择器；数据区改为原始帧、已处理帧、节流丢弃帧、处理频率上限 |
| `apps/android/app/src/test/java/com/silvershield/app/guard/capture/ScreenCaptureStateTest.kt` | 重写为 14 项用例（含 raw=54 → processed=6 → dropped=48 的节流统计、平均频率、停止后不再计数等） |

未修改：`FakeGuardService`、`GuardState`、`GuardRepository`、`GuardViewModel`（P04 隔离保持不变）、`AndroidManifest.xml`（权限与服务声明沿用上轮）。

---

## 三、优化前后采集处理频率

| 项目 | 优化前 | 优化后（5 FPS 默认） | 优化后（10 FPS 测试） |
|------|--------|---------------------|----------------------|
| 业务处理频率上限 | 无（= 原始帧率） | 5 FPS（硬上限） | 10 FPS（硬上限） |
| 真机原始帧率 | 约 54 FPS（约 2 秒 108 帧） | 未变（系统刷新率决定） | 未变 |
| 模拟器实测处理频率（持续滑动屏幕） | 未做节流，每帧都处理 | **4.1 ~ 4.4 FPS** | **6.7 ~ 7.6 FPS** |
| 模拟器实测处理频率（静态页面） | — | 1.0 ~ 2.0 FPS | 1.6 ~ 1.8 FPS |
| 模拟器实测原始帧率（持续滑动） | — | 8.8 ~ 14.2 FPS | 11.4 ~ 18.5 FPS |
| 节流丢弃累计 | 无此概念 | 161 秒累计 726 原始 / 317 处理 / **409 丢弃** | 87 秒累计 513 原始 / 258 处理 / **251 丢弃** |

原始 fps 明显低于真机 54 FPS，是因为模拟器的 `VirtualDisplay` 只在画面变化时产帧；真机系统动画与视频播放会持续产帧。**关键点是「处理 fps 被限制在目标值以下，且丢弃帧持续增长」**，说明节流按设计生效。

日志样例（5 FPS 模式）：

```
I SilverScreenCapture: 采集启动 720x1600 目标处理频率=5fps（原始帧由系统刷新率决定）
I SilverScreenCapture: 帧率实测：原始=11.5 fps 处理=4.4 fps 原始累计=190 处理累计=100 丢弃累计=90
I SilverScreenCapture: 帧率实测：原始=14.2 fps 处理=4.1 fps 原始累计=263 处理累计=121 丢弃累计=142
I SilverScreenCapture: 采集引擎已释放（原始帧累计=726）
```

---

## 四、CPU 与内存测试结果

测量方式：`adb shell top -b -n 2 -d 3 -p <pid>`（取进程 %CPU）、`adb shell dumpsys meminfo <pid>`（TOTAL PSS）。
「持续滑动」= 后台循环 `input swipe` 制造连续画面变化；「静态」= 页面静止。

### 4.1 CPU（模拟器 4 核，%CPU 为进程占用）

| 场景 | 未采集 | 5 FPS 采集中 | 10 FPS 采集中 |
|------|--------|--------------|----------------|
| 静态页面（前台/后台，无画面变化） | 0.0% | **2.0%** | — |
| 前台 + 持续滑动 | 65.0%（对照组） | **42.6%** | **37.3%** |
| 后台 + 屏幕持续变化 | 0.0% | **3.6%** | — |
| 后台 + 静止 | 0.0% | 0.0% | — |

说明：
- 前台滑动场景下，开销主要来自 Compose 滚动重组本身（对照组无采集时同样 65%），采集只是叠加项；模拟器为软件渲染，绝对值不具代表性。
- 后台真正关键的两组：**静止 0.0% / 有画面变化 3.6%**，后台不再有每帧状态流与 UI 刷新。
- 10 FPS 的 CPU 未显著高于 5 FPS（37.3% vs 42.6%，差值在滑动抖动范围内），说明瓶颈已从采集转移到 UI 滚动。

### 4.2 内存（TOTAL PSS，KB）

| 时点 | PSS | 说明 |
|------|-----|------|
| 采集前（进程已运行） | 87,537 | 基准 |
| 5 FPS 采集中 | 92,023 | +4.5 MB（Native Heap 16,308） |
| 后台采集中 | 93,335 | 与前台基本一致，无后台膨胀 |
| 10 FPS 采集中 | 92,622 | 与 5 FPS 基本一致 |
| 停止后 | 90,085 ~ 93,990 | 未瞬间回落到基准（ART/Compose 缓存驻留），但**无持续增长**；两次采集（161s + 87s，累计约 250s）PSS 始终在 90 ~ 94 MB 区间波动 |

关键结论：`maxImages=2` + 每帧 `finally close()` 生效，长时间采集未见缓冲区堆积（否则 ImageReader 直接抛 `IllegalStateException: maxImages has already been acquired`）。

---

## 五、后台流畅度测试结果

能直接测到的客观指标：

| 指标 | 结果 |
|------|------|
| 后台采集是否继续 | 是（切 Home 后原始帧计数持续增长，服务与通知保持） |
| 前台服务通知是否保留 | 是。`dumpsys notification` 显示 `flags=ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE`，含「停止采集」Action |
| 后台 App 进程 CPU（静止） | 0.0% |
| 后台 App 进程 CPU（屏幕持续变化） | 3.6% |
| 后台 UI 刷新 | 页面不可见时 Compose 停止收集（`WhileSubscribed(5s)`），统计最多 1 秒 1 次，不产生逐帧重组 |
| 后台进程 PSS 增长 | 无（92 MB → 93 MB 区间波动） |

优化前后对比（逻辑层面）：
- 优化前：54 次/秒状态流发射 + 54 次/秒 Compose 重组，后台持续存在；
- 优化后：≤5 次/秒状态流发射，UI 采样 ≤1 次/秒，后台静态时接近 0。

**真机流畅度无法用模拟器结论替代。** 模拟器自身基线（无采集时滑动）就已是 42 帧/100% janky，无法给出有意义的 jank 对比，因此本文不输出「真机流畅度提升 X%」这类未经测量的结论。建议在 Mate 60 Pro 上按第六节步骤复测。

---

## 六、停止与资源释放验证

| 检查 | 结果 |
|------|------|
| 日志释放顺序 | `采集引擎已释放（原始帧累计=726）`；顺序为：监听置空 → `VirtualDisplay.release()` → `ImageReader.close()` → `unregisterCallback` → `MediaProjection.stop()` → 线程退出 |
| `dumpsys activity services com.silvershield.app` | 停止后无 `ServiceRecord`（服务已销毁） |
| `dumpsys media_projection` | 采集期间 `(com.silvershield.app, uid=10218): TYPE_SCREEN_CAPTURE`；停止后 `null` |
| 通知 | 停止后随前台服务一并移除 |
| 重复启动 | 采集中状态位拒绝重复启动（`采集已在进行中，无需重复启动`），单测覆盖 |
| 服务导出 | `exported=false`；shell 直接 `am start-foreground-service` 被系统拒绝（`Requires permission not exported from uid`），符合最小暴露原则 |

---

## 七、APK 构建结果

```
> Task :app:compileDebugKotlin
> Task :app:testDebugUnitTest
> Task :app:assembleDebug
BUILD SUCCESSFUL
```

- 产物：`apps/android/app/build/outputs/apk/debug/app-debug.apk`
- 大小：11,179,770 字节（约 10.66 MB），构建时间 2026-11-17 15:45:56
- 单元测试：42 项全部通过（0 failures / 0 errors），其中采集统计相关 14 项
- 已安装至模拟器并完成上述实测

---

## 八、待真机复测项

1. Mate 60 Pro 前台连续采集 5 分钟：观察整机滑动是否仍卡顿，记录 `帧率实测` 原始/处理/丢弃三值。
2. 切后台 5 分钟（保持通知）：观察整机流畅度、耗电与发热。
3. 真机 CPU：`adb shell top -p <pid>`；内存：`adb shell dumpsys meminfo <pid>`。
4. 真机切换 10 FPS 复测一组，确认「5 FPS 够用则不必上 10 FPS」。
5. 若真机仍卡：下一步应评估降低 `VirtualDisplay` 输出分辨率或改用 `ImageReader` 图片格式优化，而不是继续堆节流。
