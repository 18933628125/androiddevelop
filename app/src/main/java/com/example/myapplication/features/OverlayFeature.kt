package com.example.myapplication.features

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.TextView
import com.example.myapplication.permission.OverlayPermissionHelper
import com.example.myapplication.utils.HttpUtils
import java.io.File
import kotlin.math.absoluteValue

class OverlayFeature(
    private val activity: Activity,
    private val audioRecordFeature: AudioRecordFeature
) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    // 拖动相关
    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val DRAG_THRESHOLD = 10f
    private var isDragging = false
    // 录音状态
    private var isRecording = false
    // 新增：冻结状态（录音+截图完成后不可点击）
    private var isFrozen = false
    // 按钮尺寸（原120→240，放大两倍）
    private val BUTTON_SIZE = 240

    fun show() {
        // 检查悬浮窗权限
        if (!OverlayPermissionHelper.hasPermission(activity)) {
            OverlayPermissionHelper.requestPermission(activity)
            return
        }

        if (overlayView != null) return

        windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 初始化悬浮窗参数
        params = WindowManager.LayoutParams(
            BUTTON_SIZE,
            BUTTON_SIZE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params?.gravity = Gravity.TOP or Gravity.START
        params?.x = 300
        params?.y = 600

        // 创建悬浮窗视图（放大+点击切换）
        val view = TextView(activity).apply {
            text = "🎙"
            textSize = 48f // 文字放大两倍
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createRoundBackground(Color.parseColor("#88000000"))
            layoutParams = ViewGroup.LayoutParams(BUTTON_SIZE, BUTTON_SIZE)
        }

        // 触摸事件：拖动+点击切换录音
        view.setOnTouchListener { v, event ->
            // 新增：冻结状态下不响应点击（仍可拖动）
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置
                    initialX = params?.x?.toFloat() ?: 0f
                    initialY = params?.y?.toFloat() ?: 0f
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // 判断是否拖动
                    if (dx.absoluteValue > DRAG_THRESHOLD || dy.absoluteValue > DRAG_THRESHOLD) {
                        isDragging = true
                        // 更新位置
                        params?.x = (initialX + dx).toInt()
                        params?.y = (initialY + dy).toInt()

                        // 边界限制
                        val displayMetrics = activity.resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels

                        params?.x = params?.x?.coerceAtLeast(0) ?: 0
                        params?.x = params?.x?.coerceAtMost(screenWidth - BUTTON_SIZE) ?: 0
                        params?.y = params?.y?.coerceAtLeast(0) ?: 0
                        params?.y = params?.y?.coerceAtMost(screenHeight - BUTTON_SIZE) ?: 0

                        windowManager?.updateViewLayout(v, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                    } else {
                        // 新增：冻结状态下不执行点击逻辑
                        if (!isFrozen) {
                            // 点击切换录音
                            toggleRecording(v as TextView)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }

                else -> false
            }
        }

        windowManager?.addView(view, params)
        overlayView = view
    }

    /**
     * 切换录音状态（点击开始/再次点击停止）
     */
    private fun toggleRecording(btn: TextView) {
        if (!isRecording) {
            // 开始录音
            audioRecordFeature.startRecord()
            isRecording = true
            btn.background = createRoundBackground(Color.RED)
            btn.text = "⏹" // 停止图标
        } else {
            // 停止录音
            audioRecordFeature.stopRecord()
            isRecording = false
            // 新增：冻结按钮+修改图标
            isFrozen = true
            btn.background = createRoundBackground(Color.parseColor("#88888888")) // 灰色
            btn.text = "⌛" // 加载图标
        }
    }

    /**
     * 新增：发送数据到后端并处理结果
     */
    fun sendDataToBackend(audioFile: File?, screenshotFile: File?, threadId: String) {
        activity.runOnUiThread {
            // 更新按钮为加载状态
            (overlayView as? TextView)?.text = "⌛"
        }

        // 调用网络工具类发送数据
        HttpUtils.sendInitDecision(threadId, audioFile, screenshotFile) { isSuccess, response, error ->
            activity.runOnUiThread {
                if (isSuccess) {
                    Log.d("OverlayFeature", "后端返回数据：$response")
                    // 可选：打印到控制台（也可以Toast显示）
                    (overlayView as? TextView)?.text = "✅" // 成功图标
                } else {
                    Log.e("OverlayFeature", "发送失败：$error")
                    (overlayView as? TextView)?.text = "❌" // 失败图标
                }
                // 可选：解锁按钮（根据需求决定是否解锁）
                // isFrozen = false
                // (overlayView as? TextView)?.text = "🎙"
                // (overlayView as? TextView)?.background = createRoundBackground(Color.parseColor("#88000000"))
            }
        }
    }

    /**
     * 创建圆形背景
     */
    private fun createRoundBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            alpha = 200
        }
    }

    fun hide() {
        // 隐藏时停止录音
        if (isRecording) {
            audioRecordFeature.stopRecord()
            isRecording = false
        }
        // 移除悬浮窗
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
        params = null
        // 重置冻结状态
        isFrozen = false
    }

    // 新增：解锁按钮（可选）
    fun unfreeze() {
        isFrozen = false
        (overlayView as? TextView)?.apply {
            text = "🎙"
            background = createRoundBackground(Color.parseColor("#88000000"))
        }
        audioRecordFeature.reset()
    }
}