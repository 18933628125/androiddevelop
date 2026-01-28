package com.example.myapplication.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCaptureService"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var saveDir: String? = null
    private var threadId: String? = null
    private var screenshotFileName: String? = null

    companion object {
        const val ACTION_CAPTURE_SCREEN = "com.example.myapplication.action.CAPTURE_SCREEN"
        const val EXTRA_SAVE_DIR = "save_dir"
        const val EXTRA_MEDIA_PROJECTION_DATA = "media_projection_data"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "ScreenCaptureService"
        private const val RESULT_OK = -1
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_CAPTURE_SCREEN) {
            saveDir = intent.getStringExtra(EXTRA_SAVE_DIR)
            val mediaProjectionData = intent.getParcelableExtra<Intent>(EXTRA_MEDIA_PROJECTION_DATA)
            threadId = intent.getStringExtra("thread_id") ?: System.currentTimeMillis().toString()
            screenshotFileName = intent.getStringExtra("screenshot_filename") ?: "screenshot_$threadId.png"

            if (saveDir.isNullOrEmpty() || mediaProjectionData == null) {
                Log.e(TAG, "保存目录或MediaProjection数据为空")
                stopSelf()
                return START_NOT_STICKY
            }

            // 启动前台服务
            startForeground(NOTIFICATION_ID, createNotification())

            // 初始化MediaProjection
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(RESULT_OK, mediaProjectionData)

            // 初始化截图
            initScreenshot()
        }
        return START_NOT_STICKY
    }

    private fun initScreenshot() {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        Log.d(TAG, "截图尺寸：$width x $height，密度：$density")

        // 创建ImageReader（增加maxImages数量解决缓冲区问题）
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            try {
                // 读取截图并保存
                val image = reader.acquireLatestImage()
                image?.let {
                    saveScreenshot(it)
                    it.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理截图失败：${e.message}", e)
            } finally {
                // 停止服务
                stopSelf()
            }
        }, Handler(HandlerThread("ScreenshotThread").also { it.start() }.looper))

        // 创建VirtualDisplay
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        ) ?: run {
            Log.e(TAG, "创建VirtualDisplay失败")
            stopSelf()
            return
        }
    }

    private fun saveScreenshot(image: android.media.Image) {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            // 创建Bitmap
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // 裁剪掉多余的padding
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                image.width,
                image.height
            )

            // 确保保存目录存在
            val dir = File(saveDir!!)
            if (!dir.exists()) dir.mkdirs()

            // 使用指定的文件名保存（确保和录音的thread_id一致）
            val saveFile = File(dir, screenshotFileName!!)
            val outputStream: OutputStream = FileOutputStream(saveFile)
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            Log.d(TAG, "截图保存成功：${saveFile.absolutePath}，文件大小：${saveFile.length()}字节")

            // 释放Bitmap
            bitmap.recycle()
            croppedBitmap.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "保存截图失败：${e.message}", e)
        } finally {
            // 释放资源
            mediaProjection?.stop()
            virtualDisplay?.release()
            imageReader?.close()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕捕获服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("屏幕捕获中")
            .setContentText("正在截取屏幕内容")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}