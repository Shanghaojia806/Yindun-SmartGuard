package com.silvershield.app.guard.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P05 真实采集状态机单元测试（纯 JVM，不依赖 Android 运行时）。
 *
 * 覆盖：授权同意 / 拒绝、启动、原始帧与处理帧分离统计、节流丢弃、停止、异常、重复启动防护。
 */
class ScreenCaptureStateTest {

    private var now = 1_000L
    private val holder = ScreenCaptureStateHolder(clock = { now })

    @Test
    fun `初始状态为未开始`() {
        val status = holder.status.value
        assertEquals(CapturePhase.IDLE, status.phase)
        assertFalse(status.isActive)
        assertEquals(0L, status.stats.frameCount)
        assertEquals(0L, status.stats.rawFrameCount)
        assertNull(status.reason)
    }

    @Test
    fun `等待授权期间重复点击被拒绝`() {
        assertTrue(holder.beginPermissionRequest())
        assertEquals(CapturePhase.REQUESTING_PERMISSION, holder.status.value.phase)
        assertFalse(holder.beginPermissionRequest())
    }

    @Test
    fun `拒绝系统授权回到未开始而非异常`() {
        holder.beginPermissionRequest()
        holder.onPermissionDenied()

        val status = holder.status.value
        assertEquals(CapturePhase.IDLE, status.phase)
        assertFalse(status.isActive)
        assertTrue(status.reason!!.contains("未开始采集"))
    }

    @Test
    fun `授权通过后记录目标处理频率`() {
        holder.beginPermissionRequest()
        holder.onPermissionGranted(
            notificationPermissionGranted = false,
            targetFps = TEST_CAPTURE_FPS,
        )

        val status = holder.status.value
        assertEquals(CapturePhase.STARTING_SERVICE, status.phase)
        assertEquals(TEST_CAPTURE_FPS, status.targetFps)
        assertFalse(status.notificationPermissionGranted)
        assertTrue(status.isActive)
    }

    @Test
    fun `启动成功后进入采集中`() {
        holder.beginPermissionRequest()
        holder.onPermissionGranted(notificationPermissionGranted = true, targetFps = 5)
        now = 2_000L
        holder.onCapturing(widthPx = 720, heightPx = 1554)

        val status = holder.status.value
        assertEquals(CapturePhase.CAPTURING, status.phase)
        assertEquals(720, status.stats.widthPx)
        assertEquals(1554, status.stats.heightPx)
        assertEquals(2_000L, status.startedAtMillis)
        assertNull(status.reason)
    }

    @Test
    fun `原始帧与处理帧分开统计并计算丢弃数`() {
        startCapturing()

        // 1 秒内系统产生 54 帧，业务只处理 6 帧（模拟 5 FPS 节流）。
        holder.onFrame(snapshot(raw = 1, at = 2_000L))
        holder.onFrame(snapshot(raw = 11, at = 2_040L))
        holder.onFrame(snapshot(raw = 22, at = 2_080L))
        holder.onFrame(snapshot(raw = 33, at = 2_120L))
        holder.onFrame(snapshot(raw = 44, at = 2_160L))
        holder.onFrame(snapshot(raw = 54, at = 2_200L))

        val stats = holder.status.value.stats
        assertEquals(6L, stats.frameCount)
        assertEquals(54L, stats.rawFrameCount)
        assertEquals(48L, stats.droppedFrameCount)
    }

    @Test
    fun `处理帧与原始帧平均频率分别计算`() {
        startCapturing()

        // 处理帧 5 帧 / 400ms = 10 FPS；原始 40 帧 / 400ms = 97.5 FPS。
        holder.onFrame(snapshot(raw = 1, at = 1_000L))
        holder.onFrame(snapshot(raw = 10, at = 1_100L))
        holder.onFrame(snapshot(raw = 20, at = 1_200L))
        holder.onFrame(snapshot(raw = 30, at = 1_300L))
        holder.onFrame(snapshot(raw = 40, at = 1_400L))

        val stats = holder.status.value.stats
        assertEquals(97.5f, stats.averageRawFps!!, 0.01f)
        assertEquals(10f, stats.averageProcessedFps!!, 0.01f)
    }

    @Test
    fun `未开始或已停止时不接收新帧`() {
        holder.onFrame(snapshot(raw = 1, at = 1_100L))
        assertEquals(0L, holder.status.value.stats.frameCount)

        startCapturing()
        holder.onFrame(snapshot(raw = 2, at = 1_200L))
        assertEquals(1L, holder.status.value.stats.frameCount)

        holder.onStopped("用户停止")
        holder.onFrame(snapshot(raw = 3, at = 1_300L))
        assertEquals(1L, holder.status.value.stats.frameCount)
    }

    @Test
    fun `启动过程中已到达的帧不会丢失`() {
        holder.beginPermissionRequest()
        holder.onPermissionGranted(notificationPermissionGranted = true, targetFps = 5)
        holder.onFrame(snapshot(raw = 1, at = 1_050L))
        holder.onCapturing(widthPx = 720, heightPx = 1554)

        val status = holder.status.value
        assertEquals(CapturePhase.CAPTURING, status.phase)
        assertEquals(1L, status.stats.frameCount)
        assertEquals(1_050L, status.stats.firstFrameAtMillis)
    }

    @Test
    fun `系统撤销后必须离开运行中`() {
        startCapturing()
        holder.onFrame(snapshot(raw = 1, at = 1_200L))
        now = 3_000L

        holder.onStopped("系统已结束屏幕共享，本次采集已停止。")

        val status = holder.status.value
        assertEquals(CapturePhase.STOPPED, status.phase)
        assertFalse(status.isActive)
        assertTrue(status.reason!!.contains("系统"))
        assertEquals(3_000L, status.lastChangedAtMillis)
    }

    @Test
    fun `重复停止保持幂等`() {
        startCapturing()
        holder.onStopped("用户停止")
        holder.onStopped("服务结束")

        assertEquals(CapturePhase.STOPPED, holder.status.value.phase)
        assertEquals("服务结束", holder.status.value.reason)
    }

    @Test
    fun `采集异常进入错误态并携带原因`() {
        startCapturing()
        holder.onError("屏幕采集启动失败：SecurityException")

        val status = holder.status.value
        assertEquals(CapturePhase.ERROR, status.phase)
        assertFalse(status.isActive)
        assertTrue(status.reason!!.contains("SecurityException"))
    }

    @Test
    fun `页面销毁只清理等待授权状态`() {
        holder.beginPermissionRequest()
        holder.resetPendingRequest()
        assertEquals(CapturePhase.IDLE, holder.status.value.phase)

        startCapturing()
        holder.resetPendingRequest()
        assertEquals(CapturePhase.CAPTURING, holder.status.value.phase)
    }

    @Test
    fun `重新开始会清空上一轮帧统计并保留所选频率`() {
        startCapturing()
        holder.onFrame(snapshot(raw = 1, at = 1_500L))
        holder.onStopped("用户停止")

        assertTrue(holder.beginPermissionRequest())
        holder.onPermissionGranted(notificationPermissionGranted = true, targetFps = TEST_CAPTURE_FPS)
        holder.onCapturing(widthPx = 720, heightPx = 1554)

        val status = holder.status.value
        assertEquals(0L, status.stats.frameCount)
        assertEquals(0L, status.stats.rawFrameCount)
        assertEquals(TEST_CAPTURE_FPS, status.targetFps)
    }

    @Test
    fun `不足两帧时不计算平均帧率`() {
        startCapturing()
        holder.onFrame(snapshot(raw = 1, at = 1_000L))

        val stats = holder.status.value.stats
        assertNull(stats.averageProcessedFps)
        assertNull(stats.averageRawFps)
        assertNull(stats.lastFrameIntervalMillis)
    }

    private fun startCapturing(targetFps: Int = DEFAULT_CAPTURE_FPS) {
        holder.beginPermissionRequest()
        holder.onPermissionGranted(notificationPermissionGranted = true, targetFps = targetFps)
        holder.onCapturing(widthPx = 720, heightPx = 1554)
    }

    private fun snapshot(raw: Long, at: Long): CaptureFrameSnapshot = CaptureFrameSnapshot(
        widthPx = 720,
        heightPx = 1554,
        timestampMillis = at,
        rawFrameCount = raw,
        rawFirstFrameAtMillis = 1_000L,
        rawLastFrameAtMillis = at,
    )
}
