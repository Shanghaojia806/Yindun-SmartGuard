package com.silvershield.app.guard.capture

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaProjection 采集引擎（P05 PoC）。
 *
 * 性能策略：
 * - 取帧回调在独立 HandlerThread，绝不在主线程做图像处理；
 * - 基于 [SystemClock.elapsedRealtimeNanos]（单调时钟）节流，默认 5 FPS，可按配置升到 10 FPS；
 * - 每个回调都立即 `acquireLatestImage()` + `close()`，被节流跳过的帧同样释放，不积累缓冲区；
 * - 原始帧频率与业务处理频率分开计数，每 5 秒输出一次日志用于实测；
 * - 处理频率上限由 [targetFps] 控制（默认 5 FPS），原始帧仍按系统刷新率产生。
 *
 * 不保存 Bitmap、不落盘、不上传。
 */
class ScreenCaptureEngine(
    private val mediaProjection: MediaProjection,
    private val targetFps: Int = DEFAULT_CAPTURE_FPS,
    private val onFrame: (CaptureFrameSnapshot) -> Unit,
    private val onSystemStop: () -> Unit,
    private val onError: (String) -> Unit,
) {

    private val handlerThread = HandlerThread(THREAD_NAME).apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val released = AtomicBoolean(false)

    private val frameIntervalNanos: Long =
        1_000_000_000L / targetFps.coerceIn(1, 30)

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // 以下计数只会在采集线程访问，无需加锁。
    private var lastProcessedNanos = 0L
    private var rawFrameCount = 0L
    private var processedFrameCount = 0L
    private var rawFirstFrameAtMillis = 0L
    private var rawLastFrameAtMillis = 0L

    // 每 5 秒输出一次实测帧率。
    private var logWindowStartNanos = 0L
    private var rawFramesInWindow = 0
    private var processedFramesInWindow = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // 主动 stop() 时也会触发该回调，已释放则忽略，避免重复释放。
            if (released.get()) return
            onSystemStop()
        }
    }

    /** 创建虚拟显示并开始取帧；失败时抛出由调用方统一处理。 */
    fun start(widthPx: Int, heightPx: Int, densityDpi: Int) {
        check(!released.get()) { "采集引擎已释放，不能重复启动" }

        // maxImages=2：容量受控，配合 acquireLatestImage 丢弃旧帧。
        val reader = ImageReader.newInstance(widthPx, heightPx, PixelFormat.RGBA_8888, MAX_IMAGES)
        reader.setOnImageAvailableListener({ source -> onImageAvailable(source) }, handler)

        mediaProjection.registerCallback(projectionCallback, handler)

        imageReader = reader
        virtualDisplay = createDisplay(reader, widthPx, heightPx, densityDpi)

        logWindowStartNanos = SystemClock.elapsedRealtimeNanos()
        Log.i(
            TAG,
            "采集启动 ${widthPx}x$heightPx 目标处理频率=${targetFps}fps（原始帧由系统刷新率决定）",
        )
    }

    private fun createDisplay(
        reader: ImageReader,
        widthPx: Int,
        heightPx: Int,
        densityDpi: Int,
    ): VirtualDisplay = mediaProjection.createVirtualDisplay(
        DISPLAY_NAME,
        widthPx,
        heightPx,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        handler,
    )

    /** 采集线程：取帧 → 即时关闭 → 节流决定是否上报统计。 */
    private fun onImageAvailable(source: ImageReader) {
        try {
            val image = source.acquireLatestImage() ?: return
            try {
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val nowMillis = System.currentTimeMillis()

                rawFrameCount += 1
                rawFramesInWindow += 1
                if (rawFirstFrameAtMillis == 0L) rawFirstFrameAtMillis = nowMillis
                rawLastFrameAtMillis = nowMillis

                // 单调时钟节流：不 sleep、不阻塞采集线程。
                if (nowNanos - lastProcessedNanos >= frameIntervalNanos) {
                    lastProcessedNanos = nowNanos
                    processedFramesInWindow += 1
                    processedFrameCount += 1
                    onFrame(
                        CaptureFrameSnapshot(
                            widthPx = image.width,
                            heightPx = image.height,
                            timestampMillis = nowMillis,
                            rawFrameCount = rawFrameCount,
                            rawFirstFrameAtMillis = rawFirstFrameAtMillis,
                            rawLastFrameAtMillis = rawLastFrameAtMillis,
                        ),
                    )
                }
                logWindowIfNeeded(nowNanos)
            } finally {
                // 无论是否被节流，都必须立即释放图像缓冲区。
                image.close()
            }
        } catch (closed: IllegalStateException) {
            Log.d(TAG, "ImageReader 已关闭，停止取帧")
        } catch (error: RuntimeException) {
            onError("读取采集帧失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun logWindowIfNeeded(nowNanos: Long) {
        val elapsed = nowNanos - logWindowStartNanos
        if (elapsed < LOG_WINDOW_NANOS) return
        val rawFps = rawFramesInWindow * 1_000_000_000f / elapsed
        val processedFps = processedFramesInWindow * 1_000_000_000f / elapsed
        Log.i(
            TAG,
            "帧率实测：原始=%.1f fps 处理=%.1f fps 原始累计=%d 处理累计=%d 丢弃累计=%d"
                .format(
                    rawFps,
                    processedFps,
                    rawFrameCount,
                    processedFrameCount,
                    rawFrameCount - processedFrameCount,
                ),
        )
        rawFramesInWindow = 0
        processedFramesInWindow = 0
        logWindowStartNanos = nowNanos
    }

    /**
     * 释放顺序与 Android 官方要求一致：
     * 停止取帧 → 释放 VirtualDisplay → 关闭 ImageReader → 停止 MediaProjection → 退出采集线程。
     * 幂等，可被 onStop 与 onDestroy 重复调用。
     */
    fun release() {
        if (!released.compareAndSet(false, true)) return

        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection.stop() }
        runCatching { handlerThread.quitSafely() }
        Log.i(TAG, "采集引擎已释放（原始帧累计=$rawFrameCount）")
    }

    private companion object {
        const val TAG = "SilverScreenCapture"
        const val THREAD_NAME = "silver-screen-capture"
        const val DISPLAY_NAME = "silver-shield-poc"

        /** 缓冲上限 2 帧，配合 acquireLatestImage 丢弃旧帧。 */
        const val MAX_IMAGES = 2

        const val LOG_WINDOW_NANOS = 5_000_000_000L
    }
}
