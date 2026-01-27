package com.example.myapplication

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.features.AudioRecordFeature
import com.example.myapplication.features.OverlayFeature
import com.example.myapplication.permission.AudioPermissionHelper
import com.example.myapplication.permission.ScreenshotPermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var overlayFeature: OverlayFeature
    private lateinit var audioRecordFeature: AudioRecordFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化功能类
        audioRecordFeature = AudioRecordFeature(this)
        overlayFeature = OverlayFeature(this, audioRecordFeature)

        // 显示悬浮窗
        overlayFeature.show()
    }

    /**
     * 处理权限申请结果（录音权限）
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AudioPermissionHelper.REQUEST_CODE) {
            if (grantResults.isNotEmpty()) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("MainActivity", "录音权限被拒绝")
                    Toast.makeText(this, "需要录音权限才能使用录音功能", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 处理屏幕捕获权限回调
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ScreenshotPermissionHelper.REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                // 保存授权结果
                ScreenshotPermissionHelper.mediaProjectionResultData = data
                Toast.makeText(this, "屏幕捕获权限已授予，可截取当前屏幕", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("MainActivity", "用户拒绝了屏幕捕获权限")
                Toast.makeText(this, "需要屏幕捕获权限才能截取当前界面", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 应用销毁时清理资源
     */
    override fun onDestroy() {
        super.onDestroy()
        overlayFeature.hide()
    }
}