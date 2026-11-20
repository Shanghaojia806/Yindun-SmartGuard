package com.silvershield.app.guard.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.silvershield.app.MainActivity
import com.silvershield.app.R

/**
 * MediaProjection 前台采集服务（P05 PoC）。
 *
 * 合规要点（targetSdk 35）：
 * - Android 14+ 必须先 [ServiceCompat.startForeground]（类型 mediaProjection）
 *   再调用 `getMediaProjection()` / `createVirtualDisplay()`；
 * - 授权令牌由用户在系统授权窗口中主动确认后传入，服务自身不申请、不绕过授权；
 * - 通知栏常驻说明"屏幕采集正在进行"，并提供停止入口；
 * - 全程不落盘、不上传、不保存录像。
 */
class ScreenCaptureService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var engine: ScreenCaptureEngine? = null
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture("已停止屏幕采集。")
            ACTION_START -> handleStart(intent)
            else -> {
                // 系统重启服务时不会携带授权令牌，直接结束，避免出现"无授权仍在采集"。
                Log.w(TAG, "收到无授权的启动请求，停止服务")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (engine != null) {
            // 重复启动防护：不创建第二个 MediaProjection 会话。
            Log.w(TAG, "采集会话已存在，忽略重复启动请求")
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        val notificationGranted = intent.getBooleanExtra(EXTRA_NOTIFICATION_GRANTED, true)
        val targetFps = intent.getIntExtra(EXTRA_TARGET_FPS, DEFAULT_CAPTURE_FPS)
            .coerceIn(1, 30)

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            ScreenCaptureSession.onError("未获得有效的系统屏幕采集授权，采集未启动。")
            stopSelf()
            return
        }

        try {
            // Android 14+ 要求：创建虚拟显示前必须先进入 mediaProjection 类型前台服务。
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    0
                },
            )

            val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection 创建失败")

            projection = mediaProjection
            ScreenCaptureSession.onPermissionGranted(notificationGranted, targetFps)

            val (widthPx, heightPx, densityDpi) = resolveCaptureSize()
            val captureEngine = ScreenCaptureEngine(
                mediaProjection = mediaProjection,
                targetFps = targetFps,
                onFrame = { snapshot ->
                    ScreenCaptureSession.onFrame(snapshot)
                },
                onSystemStop = {
                    stopCapture("系统已结束屏幕共享，本次采集已停止。")
                },
                onError = { reason ->
                    stopCapture(reason, isError = true)
                },
            )
            engine = captureEngine
            captureEngine.start(widthPx, heightPx, densityDpi)
            ScreenCaptureSession.onCapturing(widthPx, heightPx)
            Log.i(TAG, "真实采集已启动 ${widthPx}x$heightPx，处理频率=${targetFps}fps")
        } catch (error: Throwable) {
            Log.e(TAG, "启动真实采集失败", error)
            stopCapture(
                "屏幕采集启动失败：${error.message ?: error.javaClass.simpleName}",
                isError = true,
            )
        }
    }

    /**
     * 统一停止入口，保证顺序：停帧 → 释放虚拟显示 → 关闭 ImageReader
     * → 停止 MediaProjection → 移除前台通知 → 结束服务。
     */
    private fun stopCapture(reason: String, isError: Boolean = false) {
        val stop = Runnable {
            engine?.release()
            engine = null
            projection = null

            if (isError) {
                ScreenCaptureSession.onError(reason)
            } else {
                ScreenCaptureSession.onStopped(reason)
            }

            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.i(TAG, "采集已停止：$reason")
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            stop.run()
        } else {
            mainHandler.post(stop)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户划掉任务时立即结束采集，不留下"看不见但仍在录"的服务。
        stopCapture("应用任务已被移除，屏幕采集已停止。")
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.release()
        engine = null
        projection = null

        if (ScreenCaptureSession.status.value.isActive) {
            ScreenCaptureSession.onStopped("采集服务已结束，屏幕采集已停止。")
        }
    }

    /** 采集分辨率：PoC 阶段限制最大宽度 720px，控制内存占用（不下发、不落盘）。 */
    private fun resolveCaptureSize(): CaptureSize {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels.coerceAtLeast(1)
        val screenHeight = metrics.heightPixels.coerceAtLeast(1)
        val scale = if (screenWidth > MAX_CAPTURE_WIDTH) {
            MAX_CAPTURE_WIDTH.toFloat() / screenWidth
        } else {
            1f
        }
        val width = ((screenWidth * scale).toInt() / 2 * 2).coerceAtLeast(2)
        val height = ((screenHeight * scale).toInt() / 2 * 2).coerceAtLeast(2)
        return CaptureSize(width, height, metrics.densityDpi)
    }

    private fun buildNotification(): Notification {
        ensureChannel()

        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("银盾智鉴 · 屏幕采集验证中")
            .setContentText("正在本地验证采集帧，不上传、不保存、不录像。可随时停止。")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "正在本地验证采集帧：仅在内存中统计帧数、分辨率与时间戳，" +
                        "不上传、不保存、不录像。点击「停止采集」可立即结束。",
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "停止采集", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "屏幕采集验证",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "屏幕采集验证期间显示的常驻通知，可随时停止采集。"
                setShowBadge(false)
            },
        )
    }

    private data class CaptureSize(val widthPx: Int, val heightPx: Int, val densityDpi: Int)

    companion object {
        private const val TAG = "SilverScreenCapture"
        private const val CHANNEL_ID = "silver_shield_screen_capture"
        private const val NOTIFICATION_ID = 2105
        private const val REQUEST_CODE_STOP = 2106
        private const val REQUEST_CODE_OPEN = 2107
        private const val MAX_CAPTURE_WIDTH = 720

        private const val ACTION_START = "com.silvershield.app.action.START_SCREEN_CAPTURE"
        private const val ACTION_STOP = "com.silvershield.app.action.STOP_SCREEN_CAPTURE"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val EXTRA_NOTIFICATION_GRANTED = "extra_notification_granted"
        private const val EXTRA_TARGET_FPS = "extra_target_fps"

        /** 构造启动 Intent；必须由用户在系统授权窗口确认后调用。 */
        fun startIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            notificationGranted: Boolean,
            targetFps: Int = DEFAULT_CAPTURE_FPS,
        ): Intent = Intent(context, ScreenCaptureService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_RESULT_CODE, resultCode)
            .putExtra(EXTRA_RESULT_DATA, resultData)
            .putExtra(EXTRA_NOTIFICATION_GRANTED, notificationGranted)
            .putExtra(EXTRA_TARGET_FPS, targetFps)

        /** 构造停止 Intent（应用内按钮与通知栏共用）。 */
        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
    }
}
