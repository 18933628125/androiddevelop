package com.example.myapplication.permission

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log

object ScreenshotPermissionHelper {
    private const val TAG = "ScreenshotPermissionHelper"
    const val REQUEST_CODE_SCREEN_CAPTURE = 1003
    var mediaProjectionResultData: Intent? = null

    /**
     * 请求屏幕捕获权限
     */
    fun requestScreenCapturePermission(activity: Activity) {
        try {
            val mediaProjectionManager = activity.getSystemService(Activity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            activity.startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE)
            Log.d(TAG, "已发起屏幕捕获权限请求")
        } catch (e: Exception) {
            Log.e(TAG, "请求屏幕捕获权限失败：${e.message}", e)
        }
    }

    /**
     * 处理权限请求结果
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                mediaProjectionResultData = data
                Log.d(TAG, "屏幕捕获权限获取成功")
            } else {
                mediaProjectionResultData = null
                Log.w(TAG, "用户拒绝了屏幕捕获权限")
            }
        }
    }

    /**
     * 检查是否有有效的截图权限
     */
    fun hasScreenshotPermission(): Boolean {
        return mediaProjectionResultData != null
    }
}