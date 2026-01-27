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
    // 长按相关变量
    private val LONG_PRESS_DELAY = 300L // 长按判定时间（300毫秒）
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false // 是否触发了长按
    // 悬浮窗固定尺寸（关键：解决宽窄变化）
    private val BUTTON_SIZE = 120 // 按钮宽高（单位：px，可自定义）

    fun show() {
        // 悬浮窗权限检查
        if (!OverlayPermissionHelper.hasPermission(activity)) {
            OverlayPermissionHelper.requestPermission(activity)
            return
        }

        if (overlayView != null) return

        windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 初始化悬浮窗参数（改为固定尺寸，不再WRAP_CONTENT）
        params = WindowManager.LayoutParams(
            BUTTON_SIZE, // 固定宽度
            BUTTON_SIZE, // 固定高度
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

        // 创建悬浮窗视图（统一样式，固定尺寸）
        val view = TextView(activity).apply {
            text = "🎙"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            // 移除系统背景，自定义圆角背景（避免样式不一致）
            background = createRoundBackground(Color.parseColor("#88000000"))
            // 强制设置视图尺寸（双重保障）
            layoutParams = ViewGroup.LayoutParams(BUTTON_SIZE, BUTTON_SIZE)
        }

        // 初始化长按Runnable
        longPressRunnable = Runnable {
            if (!isDragging) {
                // 非拖动状态下，触发长按录音
                isLongPressTriggered = true
                audioRecordFeature.startRecord()
                // 切换为红色背景（保持样式一致）
                view.background = createRoundBackground(Color.RED)
            }
        }

        // 核心：重构触摸事件，支持拖动+录音
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置
                    initialX = params?.x?.toFloat() ?: 0f
                    initialY = params?.y?.toFloat() ?: 0f
                    // 记录触摸点相对于视图的位置
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    // 重置状态
                    isDragging = false
                    isLongPressTriggered = false
                    // 延迟触发长按检测
                    handler.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // 计算偏移量
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // 判断是否为拖动（超过阈值）
                    if (dx.absoluteValue > DRAG_THRESHOLD || dy.absoluteValue > DRAG_THRESHOLD) {
                        isDragging = true
                        // 取消长按检测（拖动时不触发录音）
                        handler.removeCallbacks(longPressRunnable!!)
                        // 更新悬浮窗位置
                        params?.x = (initialX + dx).toInt()
                        params?.y = (initialY + dy).toInt()

                        // 获取屏幕尺寸
                        val displayMetrics = activity.resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels

                        // 左右边界（适配固定尺寸）
                        params?.x = params?.x?.coerceAtLeast(0) ?: 0
                        params?.x = params?.x?.coerceAtMost(screenWidth - BUTTON_SIZE) ?: 0
                        // 上下边界（适配固定尺寸）
                        params?.y = params?.y?.coerceAtLeast(0) ?: 0
                        params?.y = params?.y?.coerceAtMost(screenHeight - BUTTON_SIZE) ?: 0

                        // 更新悬浮窗位置
                        windowManager?.updateViewLayout(v, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // 取消长按检测
                    handler.removeCallbacks(longPressRunnable!!)

                    if (isDragging) {
                        // 拖动结束，不处理录音
                        isDragging = false
                    } else if (isLongPressTriggered) {
                        // 长按后松开，停止录音
                        audioRecordFeature.stopRecord()
                        // 恢复默认背景（保持样式一致）
                        view.background = createRoundBackground(Color.parseColor("#88000000"))
                        isLongPressTriggered = false
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    // 取消长按检测
                    handler.removeCallbacks(longPressRunnable!!)

                    if (isLongPressTriggered) {
                        // 取消事件，停止录音
                        audioRecordFeature.stopRecord()
                        // 恢复默认背景
                        view.background = createRoundBackground(Color.parseColor("#88000000"))
                        isLongPressTriggered = false
                    }
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
     * 自定义圆角背景（统一样式，避免宽窄变化）
     */
    private fun createRoundBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL // 圆形（也可以用RECTANGLE+cornerRadius做圆角矩形）
            setColor(color)
            // 可选：添加边框
            // setStroke(2, Color.WHITE)
            alpha = 200 // 透明度（和之前保持一致）
        }
    }

    fun hide() {
        // 清理handler回调
        handler.removeCallbacks(longPressRunnable!!)
        // 移除悬浮窗
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
        params = null
    }
}