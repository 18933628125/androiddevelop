package com.example.myapplication

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.features.AudioRecordFeature
import com.example.myapplication.features.CircleOverlayFeature
import com.example.myapplication.features.OverlayFeature
import com.example.myapplication.permission.AudioPermissionHelper
import com.example.myapplication.permission.ScreenshotPermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var overlayFeature: OverlayFeature
    private lateinit var audioRecordFeature: AudioRecordFeature
    // 修正：传入lifecycleScope
    private lateinit var circleOverlayFeature: CircleOverlayFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化原有功能类
        audioRecordFeature = AudioRecordFeature(this)
        overlayFeature = OverlayFeature(this, audioRecordFeature)
        // 初始化圆形悬浮窗：传入Activity和lifecycleScope
        circleOverlayFeature = CircleOverlayFeature(this, lifecycleScope)

        // 显示原有悬浮窗（不注释）
        overlayFeature.show()

        // 按钮点击事件 - 显示圆形悬浮窗
        val btnShowCircle = findViewById<Button>(R.id.btnShowCircleOverlay)
        btnShowCircle.setOnClickListener {
            // 示例：在坐标(500, 800)显示半径100px的绿色圆形悬浮窗
            val targetX = 370
            val targetY = 1740
            val radius = 100
            circleOverlayFeature.showCircleOverlay(x = targetX, y = targetY, r = radius)
            Toast.makeText(this, "圆形悬浮窗已显示，点击后将模拟点击($targetX,$targetY)", Toast.LENGTH_SHORT).show()
        }
    }

    // 以下原有代码无需修改
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ScreenshotPermissionHelper.REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                ScreenshotPermissionHelper.mediaProjectionResultData = data
                Toast.makeText(this, "屏幕捕获权限已授予，可截取当前屏幕", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("MainActivity", "用户拒绝了屏幕捕获权限")
                Toast.makeText(this, "需要屏幕捕获权限才能截取当前界面", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayFeature.hide()
        circleOverlayFeature.release()
    }
}