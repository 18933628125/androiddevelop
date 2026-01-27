package com.example.myapplication.features

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.TextView
import com.example.myapplication.permission.OverlayPermissionHelper
import kotlin.math.absoluteValue

class OverlayFeature(
    private val activity: Activity,
    private val audioRecordFeature: AudioRecordFeature
) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    // 拖动相关变量
    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    // 区分点击和拖动的阈值（超过这个距离判定为拖动）
    private val DRAG_THRESHOLD = 10f
    private var isDragging = false
    // 新增：录音状态标记（用于点击切换）
    private var isRecording = false
    // 悬浮窗尺寸修改：从120px改为240px（两倍大小）
    private val BUTTON_SIZE = 240 // 按钮宽高（单位：px）

    fun show() {
        // 悬浮窗权限检查
        if (!OverlayPermissionHelper.hasPermission(activity)) {
            OverlayPermissionHelper.requestPermission(activity)
            return
        }

        if (overlayView != null) return

        windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 初始化悬浮窗参数（尺寸改为240px）
        params = WindowManager.LayoutParams(
            BUTTON_SIZE, // 放大后的宽度（原120→240）
            BUTTON_SIZE, // 放大后的高度（原120→240）
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

        // 创建悬浮窗视图（尺寸放大，文字也同步放大）
        val view = TextView(activity).apply {
            text = "🎙"
            textSize = 48f // 文字大小从24f改为48f（两倍）
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            // 自定义圆角背景（适配放大后的尺寸）
            background = createRoundBackground(Color.parseColor("#88000000"))
            // 强制设置视图尺寸（双重保障）
            layoutParams = ViewGroup.LayoutParams(BUTTON_SIZE, BUTTON_SIZE)
        }

        // 核心修改：重构触摸事件，改为「点击切换录音」+ 拖动
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置（用于拖动）
                    initialX = params?.x?.toFloat() ?: 0f
                    initialY = params?.y?.toFloat() ?: 0f
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false // 重置拖动状态
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // 计算偏移量
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // 判断是否为拖动（超过阈值）
                    if (dx.absoluteValue > DRAG_THRESHOLD || dy.absoluteValue > DRAG_THRESHOLD) {
                        isDragging = true
                        // 更新悬浮窗位置
                        params?.x = (initialX + dx).toInt()
                        params?.y = (initialY + dy).toInt()

                        // 获取屏幕尺寸（适配放大后的按钮边界）
                        val displayMetrics = activity.resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels

                        // 左右边界（适配240px尺寸）
                        params?.x = params?.x?.coerceAtLeast(0) ?: 0
                        params?.x = params?.x?.coerceAtMost(screenWidth - BUTTON_SIZE) ?: 0
                        // 上下边界（适配240px尺寸）
                        params?.y = params?.y?.coerceAtLeast(0) ?: 0
                        params?.y = params?.y?.coerceAtMost(screenHeight - BUTTON_SIZE) ?: 0

                        // 更新悬浮窗位置
                        windowManager?.updateViewLayout(v, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // 拖动结束，不处理点击
                        isDragging = false
                    } else {
                        // 未拖动 = 点击事件 → 切换录音状态
                        toggleRecording(v as TextView)
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
     * 新增：切换录音状态（点击一次开始，再点击一次停止）
     */
    private fun toggleRecording(btn: TextView) {
        if (!isRecording) {
            // 开始录音
            audioRecordFeature.startRecord()
            isRecording = true
            // 按钮样式改为录音中（红色背景）
            btn.background = createRoundBackground(Color.RED)
            btn.text = "⏹" // 切换为停止图标
        } else {
            // 停止录音
            audioRecordFeature.stopRecord()
            isRecording = false
            // 恢复按钮默认样式
            btn.background = createRoundBackground(Color.parseColor("#88000000"))
            btn.text = "🎙" // 恢复为录音图标
        }
    }

    /**
     * 自定义圆角背景（适配放大后的圆形按钮）
     */
    private fun createRoundBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL // 圆形
            setColor(color)
            alpha = 200 // 透明度保持不变
        }
    }

    fun hide() {
        // 隐藏时如果正在录音，先停止
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
    }
}