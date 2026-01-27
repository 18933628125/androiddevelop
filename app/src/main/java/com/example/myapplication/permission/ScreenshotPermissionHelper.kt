package com.example.myapplication.permission

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log

object ScreenshotPermissionHelper {
    private val TAG = "ScreenshotPermissionHelper"
    // 权限请求码
    const val REQUEST_CODE_STORAGE = 2002
    const val REQUEST_CODE_SCREEN_CAPTURE = 3001

    // 全局保存MediaProjection授权结果（截图用）
    var mediaProjectionResultData: Intent? = null

    /**
     * 检查截图所需的所有权限（存储+屏幕捕获）
     */
    fun hasAllScreenshotPermissions(activity: Activity): Boolean {
        // 屏幕捕获权限（Android 5.0+ 必需）
        val hasScreenCapture = mediaProjectionResultData != null
        // 存储权限（仅Android 10以下需要，这里已改用私有目录，实际可忽略）
        val hasStorage = true

        return hasScreenCapture && hasStorage
    }

    /**
     * 申请屏幕捕获权限（系统级弹窗，核心）
     */
    fun requestScreenCapturePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.e(TAG, "屏幕捕获功能仅支持Android 5.0及以上")
            return
        }

        // 获取MediaProjectionManager系统服务
        val mediaProjectionManager = activity.getSystemService(Activity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // 启动权限申请弹窗
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE)
    }
}