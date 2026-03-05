package com.example.myapplication

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.features.AudioRecordFeature
import com.example.myapplication.features.CircleOverlayFeature
import com.example.myapplication.features.OverlayFeature
import com.example.myapplication.features.ScreenshotFeature
import com.example.myapplication.permission.AssistsPermissionHelper
import com.example.myapplication.permission.AudioPermissionHelper
import com.example.myapplication.permission.OverlayPermissionHelper
import com.example.myapplication.permission.ScreenshotPermissionHelper
import com.example.myapplication.state.DecisionStateMachine
import com.example.myapplication.utils.HttpUtils
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var overlayFeature: OverlayFeature
    private lateinit var audioRecordFeature: AudioRecordFeature
    private lateinit var circleOverlayFeature: CircleOverlayFeature
    private lateinit var screenshotFeature: ScreenshotFeature
    private var decisionStateMachine: DecisionStateMachine? = null
    private var currentThreadId: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    // 新增：定义截图权限请求码
    private val REQUEST_CODE_SCREEN_CAPTURE = ScreenshotPermissionHelper.REQUEST_CODE_SCREEN_CAPTURE

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkAllPermissionsOnAppStart()
        // 初始化功能类
        screenshotFeature = ScreenshotFeature(this)
        circleOverlayFeature = CircleOverlayFeature(this, lifecycleScope)

        audioRecordFeature = AudioRecordFeature(this) { audioFile, screenshotFile, threadId ->
            currentThreadId = threadId
            Log.d("MainActivity", "录音+截图完成，开始发送初始请求")

            // 冷冻录音（更新图标）
            overlayFeature.freezeRecord()
            Log.d("MainActivity", "录音+截图完成，延迟2秒发送初始请求")
            // 发送初始请求到/decision/init
            mainHandler.postDelayed({HttpUtils.sendInitDecision(
                threadId = threadId,
                audioFile = audioFile,
                imageFile = screenshotFile,
                callback = { isSuccess, response, errorMsg ->
                    if (isSuccess && response != null) {
                        // 解析初始响应
                        decisionStateMachine = DecisionStateMachine(
                            activity = this,
                            circleOverlayFeature = circleOverlayFeature,
                            screenshotFeature = screenshotFeature,
                            threadId = threadId,
                            onStateMachineEnd = {
                                // 状态机结束回调：解冻录音（恢复图标）
                                overlayFeature.unfreezeRecord()
                                showToast("状态机结束，可重新录音")
                            }
                        )
                        decisionStateMachine?.start(response)
                    } else {
                        Log.e("MainActivity", "初始请求失败：$errorMsg")
                        showToast("初始请求失败：$errorMsg")
                        // 解冻录音（恢复图标）
                        overlayFeature.unfreezeRecord()
                    }
                }
            )},2000)
        }
        // 初始化悬浮窗（包含录音图标）
        overlayFeature = OverlayFeature(this, audioRecordFeature)
        overlayFeature.show()

    }
    /**
     * 核心新增：App启动时检查所有必要权限（已添加截图权限检查）
     */
    private fun checkAllPermissionsOnAppStart() {
        // 1. 检查录音权限
        if (!AudioPermissionHelper.hasPermission(this)) {
            AudioPermissionHelper.requestPermission(this)
            showToast("请授予录音权限以使用核心功能")
        }

        // 2. 检查悬浮窗权限（你的OverlayPermissionHelper）
        if (!OverlayPermissionHelper.hasPermission(this)) {
            showToast("请授予悬浮窗权限，否则无法显示绿色点击区域")
            OverlayPermissionHelper.requestPermission(this)
        }

        // 3. 检查辅助功能权限（你的AssistsPermissionHelper）
        if (!AssistsPermissionHelper.isAssistsEnabled(this)) {
            showToast("请开启辅助功能权限，否则无法完成模拟点击")
            AssistsPermissionHelper.openAssistsSettings(this)

        }

        // 4. 检查是否可以绘制悬浮窗（冗余检查，保留）
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
        }

        // 5. 新增：检查并请求截图权限（MediaProjection）
        if (ScreenshotPermissionHelper.mediaProjectionResultData == null) {
            showToast("请授予截图权限，否则录音停止后无法截取屏幕内容")
            // 延迟1.5秒请求，避免和其他权限请求弹窗堆叠
            mainHandler.postDelayed({
                ScreenshotPermissionHelper.requestScreenCapturePermission(this)
            }, 1500)
        }
    }



    /**
     * 安全显示Toast
     */
    private fun showToast(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else {
            mainHandler.post {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AudioPermissionHelper.REQUEST_CODE_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("录音权限已授予")
            } else {
                Log.e("MainActivity", "录音权限被拒绝")
                showToast("需要录音权限才能使用录音功能")
            }
        }
    }

    // 完善：处理截图权限的ActivityResult回调
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // 处理截图权限回调
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            ScreenshotPermissionHelper.onActivityResult(requestCode, resultCode, data)
            // 给用户反馈
            if (resultCode == RESULT_OK && data != null) {
                showToast("截图权限已授予")
                Log.d("MainActivity", "截图权限获取成功")
            } else {
                showToast("截图权限被拒绝，部分功能将无法使用")
                Log.w("MainActivity", "用户拒绝了截图权限")
            }
        } else {
            // 其他ActivityResult处理
            ScreenshotPermissionHelper.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayFeature.hide()
        circleOverlayFeature.release()
        decisionStateMachine?.stop()
    }
}