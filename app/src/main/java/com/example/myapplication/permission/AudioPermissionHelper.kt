package com.example.myapplication.permission

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object AudioPermissionHelper {
    private const val TAG = "AudioPermissionHelper"
    // 新增：定义录音权限请求码（解决Unresolved reference错误）
    const val REQUEST_CODE_RECORD_AUDIO = 1001
    private const val RECORD_AUDIO_PERMISSION = android.Manifest.permission.RECORD_AUDIO

    /**
     * 检查是否有录音权限
     */
    fun hasPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            RECORD_AUDIO_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求录音权限
     */
    fun requestPermission(activity: Activity) {
        if (!hasPermission(activity)) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(RECORD_AUDIO_PERMISSION),
                REQUEST_CODE_RECORD_AUDIO // 使用定义的常量
            )
            Log.d(TAG, "已发起录音权限请求")
        } else {
            Log.d(TAG, "已拥有录音权限")
        }
    }

    /**
     * 处理权限请求结果（可选）
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (requestCode == REQUEST_CODE_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "录音权限授予成功")
                onGranted.invoke()
            } else {
                Log.d(TAG, "录音权限被拒绝")
                onDenied.invoke()
            }
        }
    }
}