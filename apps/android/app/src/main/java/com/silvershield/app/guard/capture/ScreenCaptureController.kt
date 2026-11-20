package com.silvershield.app.guard.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow

/**
 * 真实屏幕采集控制接口（P05 PoC）。
 *
 * 与 P04 的 [com.silvershield.app.guard.GuardService] 并列而非替代：
 * MediaProjection 必须先由 Activity 弹出系统授权窗口并回传 resultCode / data，
 * 无法套用 `GuardService.requestStart()` 的无参挂起契约，因此独立建模。
 */
interface ScreenCaptureController {

    /** 真实采集会话状态。 */
    val status: StateFlow<ScreenCaptureStatus>

    /** 是否需要先申请通知权限（Android 13+）。 */
    fun needsNotificationPermission(): Boolean

    /**
     * 进入"等待系统授权"阶段。
     *
     * @return false 表示已有会话在进行，调用方应忽略本次点击。
     */
    fun beginPermissionRequest(): Boolean

    /** 系统屏幕采集授权 Intent，必须交给 ActivityResultLauncher 启动。 */
    fun createPermissionIntent(): Intent

    /** 用户在系统窗口中同意授权。 */
    fun onPermissionGranted(
        resultCode: Int,
        data: Intent,
        notificationGranted: Boolean,
        targetFps: Int,
    )

    /** 用户取消系统授权（正常操作，不是故障）。 */
    fun onPermissionDenied()

    /** 用户主动停止采集。 */
    fun stop()

    /** 页面销毁时清理悬挂的"等待授权"状态。 */
    fun onScreenDisposed()
}

/** 基于 MediaProjection 的真实实现。 */
class MediaProjectionScreenCaptureController(
    private val context: Context,
) : ScreenCaptureController {

    private val appContext: Context = context.applicationContext
    private val projectionManager: MediaProjectionManager by lazy {
        appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override val status: StateFlow<ScreenCaptureStatus> = ScreenCaptureSession.status

    override fun needsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    }

    override fun beginPermissionRequest(): Boolean = ScreenCaptureSession.beginPermissionRequest()

    override fun createPermissionIntent(): Intent = projectionManager.createScreenCaptureIntent()

    override fun onPermissionGranted(
        resultCode: Int,
        data: Intent,
        notificationGranted: Boolean,
        targetFps: Int,
    ) {
        if (resultCode != Activity.RESULT_OK) {
            onPermissionDenied()
            return
        }
        ScreenCaptureSession.onPermissionGranted(notificationGranted, targetFps)
        ContextCompat.startForegroundService(
            appContext,
            ScreenCaptureService.startIntent(
                context = appContext,
                resultCode = resultCode,
                resultData = data,
                notificationGranted = notificationGranted,
                targetFps = targetFps,
            ),
        )
    }

    override fun onPermissionDenied() {
        ScreenCaptureSession.onPermissionDenied()
    }

    override fun stop() {
        runCatching {
            appContext.startService(ScreenCaptureService.stopIntent(appContext))
        }
        // 服务若已被系统回收，界面状态也必须离开运行中。
        if (ScreenCaptureSession.status.value.isActive) {
            ScreenCaptureSession.onStopped("已停止屏幕采集。")
        }
    }

    override fun onScreenDisposed() {
        ScreenCaptureSession.resetPendingRequest()
    }
}
