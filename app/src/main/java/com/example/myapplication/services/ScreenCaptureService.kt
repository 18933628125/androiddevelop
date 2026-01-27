package com.example.myapplication.services

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCaptureService"
    // 通知渠道（专门用于屏幕捕获）
    private val CHANNEL_ID = "ScreenCaptureChannel_1002"
    private val NOTIFICATION_ID = 1002

    // 静态实例（供外部调用）
    companion object {
        private var instance: ScreenCaptureService? = null
        fun getInstance(): ScreenCaptureService? = instance

        // Intent常量
        const val ACTION_CAPTURE_SCREEN = "com.example.myapplication.action.CAPTURE_SCREEN"
        const val EXTRA_SAVE_DIR = "com.example.myapplication.extra.SAVE_DIR"
        const val EXTRA_MEDIA_PROJECTION_DATA = "com.example.myapplication.extra.MEDIA_PROJECTION_DATA"
    }

    // 截图相关
    private var mediaProjection: MediaProjection? = null
    private var saveDir: String? = null
    private var screenshotPath: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 创建通知渠道并启动前台服务（指定MediaProjection类型）
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "屏幕捕获服务已启动（前台）")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到服务启动指令：action=${intent?.action}")

        intent?.let {
            when (it.action) {
                ACTION_CAPTURE_SCREEN -> {
                    // 获取参数并打印日志（核心修复：增强校验）
                    saveDir = it.getStringExtra(EXTRA_SAVE_DIR)
                    val mediaProjectionData = it.getParcelableExtra<Intent>(EXTRA_MEDIA_PROJECTION_DATA)

                    Log.d(TAG, "参数校验：saveDir=$saveDir, mediaProjectionData=${mediaProjectionData != null}")

                    // 增强参数校验
                    if (saveDir.isNullOrEmpty()) {
                        Log.e(TAG, "保存目录为空！")
                        stopSelf(startId)
                        return START_NOT_STICKY
                    }
                    if (mediaProjectionData == null) {
                        Log.e(TAG, "MediaProjection授权数据为空！请先申请屏幕捕获权限")
                        stopSelf(startId)
                        return START_NOT_STICKY
                    }

                    // 创建MediaProjection并截图
                    createMediaProjection(mediaProjectionData)
                    screenshotPath = captureScreen()
                    Log.d(TAG, "截图完成：$screenshotPath")

                    // 截图完成后停止服务
                    stopSelf(startId)
                }
                else -> {
                    Log.w(TAG, "未知的服务指令：${it.action}")
                    stopSelf(startId)
                }
            }
        } ?: run {
            Log.e(TAG, "Intent为空！")
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * 创建MediaProjection（核心修复：第一个参数必须是Activity.RESULT_OK）
     */
    private fun createMediaProjection(resultData: Intent) {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // 关键修复：将 START_NOT_STICKY 改为 Activity.RESULT_OK
        mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, resultData)
        Log.d(TAG, "MediaProjection创建成功：${mediaProjection != null}")
    }

    /**
     * 执行屏幕捕获（核心逻辑）
     */
    private fun captureScreen(): String? {
        if (mediaProjection == null || saveDir.isNullOrEmpty()) {
            Log.e(TAG, "MediaProjection或保存目录为空：mediaProjection=${mediaProjection != null}, saveDir=$saveDir")
            return null
        }

        // 获取屏幕参数
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val display = windowManager.defaultDisplay
        val point = android.graphics.Point()
        display.getRealSize(point)
        val screenWidth = point.x
        val screenHeight = point.y
        Log.d(TAG, "屏幕参数：宽=$screenWidth, 高=$screenHeight")

        val displayMetrics = android.util.DisplayMetrics()
        display.getRealMetrics(displayMetrics)
        val screenDpi = displayMetrics.densityDpi

        // 创建ImageReader
        val imageReader = android.media.ImageReader.newInstance(
            screenWidth, screenHeight,
            android.graphics.PixelFormat.RGBA_8888, 1
        )

        // 创建虚拟显示
        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        // 等待截图数据（增加短暂延迟，确保数据就绪）
        try {
            Thread.sleep(100) // 短暂延迟，避免数据未就绪
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        // 获取截图数据
        val image = imageReader.acquireLatestImage() ?: run {
            Log.e(TAG, "获取截图数据失败")
            virtualDisplay?.release()
            imageReader.close()
            mediaProjection?.stop()
            return null
        }

        // 解析Bitmap
        val bitmap = try {
            val width = image.width
            val height = image.height
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = android.graphics.Bitmap.createBitmap(
                width + rowPadding / pixelStride, height,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            image.close()
            virtualDisplay?.release()
            imageReader.close()
            mediaProjection?.stop()
        }

        // 保存Bitmap到文件
        if (bitmap != null) {
            val screenshotFile = java.io.File(saveDir, "screenshot_${System.currentTimeMillis()}.png")
            return try {
                val outputStream = java.io.FileOutputStream(screenshotFile)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                bitmap.recycle()
                Log.d(TAG, "截图保存成功：${screenshotFile.absolutePath}")
                screenshotFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "保存截图失败：${e.message}")
                null
            }
        } else {
            Log.e(TAG, "Bitmap解析失败")
        }

        return null
    }

    /**
     * 创建通知渠道（指定MediaProjection类型）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕捕获服务",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "用于全局屏幕截图的前台服务"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera) // 系统默认图标（避免找不到资源）
            .setContentTitle("屏幕捕获中")
            .setContentText("正在截取当前屏幕...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        mediaProjection?.stop()
        Log.d(TAG, "屏幕捕获服务已销毁")
    }
}