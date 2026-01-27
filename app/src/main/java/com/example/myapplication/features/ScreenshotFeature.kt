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
    // 和录音同目录：应用外部私有目录的Test文件夹
    private val baseSaveDir by lazy { File(activity.getExternalFilesDir(null), "Test") }

    /**
     * 执行全局屏幕截图（通过前台服务，兼容Android 12+）
     * @return 截图文件路径（失败返回null）
     */
    fun takeScreenshot(): String? {
        // 1. 检查并创建目标目录
        val isDirReady = checkAndCreateDir(baseSaveDir)
        if (!isDirReady) {
            Log.e(TAG, "目标目录准备失败，无法保存截图")
            return null
        }
        val saveDirPath = baseSaveDir.absolutePath
        Log.d(TAG, "截图保存目录：$saveDirPath")

        // 2. 检查Android版本（MediaProjection仅支持5.0+）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.e(TAG, "全局截图仅支持Android 5.0及以上系统")
            return null
        }

        // 3. 检查屏幕捕获权限
        val resultData = ScreenshotPermissionHelper.mediaProjectionResultData
        if (resultData == null) {
            // 未授权，先申请权限
            ScreenshotPermissionHelper.requestScreenCapturePermission(activity)
            Log.w(TAG, "未获取屏幕捕获权限，已触发权限申请")
            return null
        }
        Log.d(TAG, "已获取MediaProjection授权数据")

        // 4. 启动屏幕捕获前台服务（核心修复：显式传递参数）
        val intent = Intent(activity, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_CAPTURE_SCREEN
            // 传递保存目录（确保非空）
            putExtra(ScreenCaptureService.EXTRA_SAVE_DIR, saveDirPath)
            // 修复：显式设置类加载器传递Parcelable
            putExtra(ScreenCaptureService.EXTRA_MEDIA_PROJECTION_DATA, resultData as Parcelable)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(intent)
            } else {
                activity.startService(intent)
            }
            Log.d(TAG, "屏幕捕获前台服务启动成功")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "启动屏幕捕获服务失败：${e.message}")
        }

        return saveDirPath // 返回目录（实际路径在服务中生成）
    }

    /**
     * 检查目录，不存在则创建
     */
    private fun checkAndCreateDir(dir: File): Boolean {
        if (dir.exists() && dir.isDirectory) {
            Log.d(TAG, "目录已存在：${dir.absolutePath}")
            return true
        }
        val isCreated = dir.mkdirs()
        if (isCreated) {
            Log.d(TAG, "目录创建成功：${dir.absolutePath}")
            return true
        } else {
            Log.e(TAG, "目录创建失败：${dir.absolutePath}")
            return false
        }
    }

    /**
     * 对外提供保存目录（给录音功能调用）
     */
    fun getSaveDir(): File = baseSaveDir
}