package com.example.myapplication.features

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.util.Log
import com.example.myapplication.permission.ScreenshotPermissionHelper
import com.example.myapplication.services.ScreenCaptureService
import java.io.File

class ScreenshotFeature(private val activity: Activity) {
    private val TAG = "ScreenshotFeature"
    private val baseSaveDir by lazy { File(activity.getExternalFilesDir(null), "Test") }
    private var latestScreenshotFile: File? = null

    /**
     * 执行全局屏幕截图（异步版本，解决时间差问题）
     * @param threadId 线程ID
     * @param callback 截图完成回调
     */
    fun takeScreenshotAsync(threadId: String, callback: (File?) -> Unit) {
        // 1. 检查基础条件
        if (!checkAndCreateDir(baseSaveDir)) {
            Log.e(TAG, "目标目录准备失败")
            callback(null)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.e(TAG, "仅支持Android 5.0+")
            callback(null)
            return
        }

        val resultData = ScreenshotPermissionHelper.mediaProjectionResultData
        if (resultData == null) {
            Log.w(TAG, "无截图权限")
            callback(null)
            return
        }

        // 2. 构建截图文件对象
        val screenshotFile = File(baseSaveDir, "screenshot_$threadId.png")
        latestScreenshotFile = screenshotFile
        Log.d(TAG, "准备生成截图文件：${screenshotFile.absolutePath}")

        // 3. 启动截图服务
        val intent = Intent(activity, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_CAPTURE_SCREEN
            putExtra(ScreenCaptureService.EXTRA_SAVE_DIR, baseSaveDir.absolutePath)
            putExtra(ScreenCaptureService.EXTRA_MEDIA_PROJECTION_DATA, resultData as Parcelable)
            putExtra("thread_id", threadId)
            putExtra("screenshot_filename", screenshotFile.name)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(intent)
            } else {
                activity.startService(intent)
            }
            Log.d(TAG, "截图服务启动成功，thread_id：$threadId")
        } catch (e: Exception) {
            Log.e(TAG, "启动截图服务失败：${e.message}")
            callback(null)
            return
        }

        // 4. 智能等待：最多等5秒，文件生成后立即回调
        Thread {
            var waitTime = 0
            val maxWaitTime = 5000 // 延长到5秒
            val checkInterval = 200 // 每200ms检查一次

            while (waitTime < maxWaitTime) {
                if (screenshotFile.exists() && screenshotFile.length() > 0) {
                    Log.d(TAG, "截图文件生成成功：${screenshotFile.length()}字节")
                    callback(screenshotFile)
                    return@Thread
                }
                Thread.sleep(checkInterval.toLong())
                waitTime += checkInterval
                Log.d(TAG, "等待截图生成... $waitTime/$maxWaitTime ms")
            }

            // 超时检查：即使文件存在但为空也返回null
            if (screenshotFile.exists() && screenshotFile.length() > 0) {
                Log.d(TAG, "截图生成（超时前最后检查）")
                callback(screenshotFile)
            } else {
                Log.e(TAG, "截图生成超时，文件不存在或为空")
                callback(null)
            }
        }.start()
    }

    /**
     * 同步截图方法（兼容原有逻辑）
     */
    fun takeScreenshot(threadId: String): File? {
        var result: File? = null
        val lock = Object()

        takeScreenshotAsync(threadId) { file ->
            result = file
            synchronized(lock) {
                lock.notify()
            }
        }

        synchronized(lock) {
            lock.wait(5000) // 最多等5秒
        }

        return result
    }

    private fun checkAndCreateDir(dir: File): Boolean {
        if (dir.exists() && dir.isDirectory) return true
        return dir.mkdirs()
    }

    fun getSaveDir(): File = baseSaveDir
    fun getLatestScreenshotFile(): File? = latestScreenshotFile
    fun takeScreenshot(): File? = takeScreenshot(System.currentTimeMillis().toString())
}