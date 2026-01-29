package com.example.myapplication.features

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import com.example.myapplication.R
import com.example.myapplication.permission.AudioPermissionHelper

class OverlayFeature(private val activity: Activity, private val audioRecordFeature: AudioRecordFeature) {
    private val TAG = "OverlayFeature"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var recordIcon: ImageView? = null
    private var isRecording = false
    private var isFrozen = false // 录音冷冻标记

    // 拖动相关变量
    private var isDragging = false
    private var startX = 0f
    private var startY = 0f
    private var originalX = 0
    private var originalY = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // 录音图标资源
    private val RECORD_ICON_NORMAL = R.drawable.ic_mic // 正常录音图标
    private val RECORD_ICON_RECORDING = R.drawable.ic_mic_recording // 录音中图标
    private val RECORD_ICON_FROZEN = R.drawable.ic_mic_frozen // 冷冻状态图标

    fun show() {
        // 检查悬浮窗权限
        if (!android.provider.Settings.canDrawOverlays(activity)) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            activity.startActivity(intent)
            return
        }

        windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        // 获取录音图标
        recordIcon = overlayView?.findViewById(R.id.iv_record)
        updateRecordIcon() // 初始化图标

        // 设置触摸事件（点击+拖动）
        recordIcon?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下时记录初始位置
                    isDragging = false
                    startX = event.rawX
                    startY = event.rawY

                    // 获取当前悬浮窗的原始坐标
                    val params = overlayView?.layoutParams as WindowManager.LayoutParams
                    originalX = params.x
                    originalY = params.y

                    // 长按判定（500ms后判定为拖动）
                    mainHandler.postDelayed({
                        isDragging = true
                        Log.d(TAG, "开始拖动悬浮窗")
                    }, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        // 计算偏移量
                        val dx = (event.rawX - startX).toInt()
                        val dy = (event.rawY - startY).toInt()

                        // 更新悬浮窗位置
                        val params = overlayView?.layoutParams as WindowManager.LayoutParams
                        params.x = originalX + dx
                        params.y = originalY + dy

                        // 确保悬浮窗不超出屏幕边界
                        val screenWidth = activity.resources.displayMetrics.widthPixels
                        val screenHeight = activity.resources.displayMetrics.heightPixels
                        val viewWidth = v.width
                        val viewHeight = v.height

                        // 左边界
                        if (params.x < 0) params.x = 0
                        // 右边界
                        if (params.x > screenWidth - viewWidth) params.x = screenWidth - viewWidth
                        // 上边界
                        if (params.y < 0) params.y = 0
                        // 下边界（预留导航栏高度）
                        if (params.y > screenHeight - viewHeight - 100) params.y = screenHeight - viewHeight - 100

                        // 更新悬浮窗位置
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 取消长按判定
                    mainHandler.removeCallbacksAndMessages(null)

                    if (!isDragging) {
                        // 未拖动，执行点击逻辑
                        handleRecordIconClick()
                    } else {
                        // 拖动结束，重置状态
                        isDragging = false
                        Log.d(TAG, "悬浮窗拖动结束，新位置：x=${(overlayView?.layoutParams as WindowManager.LayoutParams).x}, y=${(overlayView?.layoutParams as WindowManager.LayoutParams).y}")
                    }
                    true
                }
                else -> false
            }
        }

        // 配置WindowManager参数（关键：调整flags支持拖动）
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START // 改为TOP+START，方便坐标计算
            x = 30 // 初始x坐标（右侧30dp）
            y = activity.resources.displayMetrics.heightPixels - 130 // 初始y坐标（底部100dp）

            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // 调整flags：移除FLAG_WATCH_OUTSIDE_TOUCH，保留可触摸
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

                    format = PixelFormat.TRANSLUCENT

            // 适配刘海屏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 添加悬浮窗
        try {
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "悬浮窗显示成功（支持自由拖动）")
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败：${e.message}", e)
        }
    }

    /**
     * 处理录音图标的点击逻辑（抽离出来，方便复用）
     */
    private fun handleRecordIconClick() {
        if (isFrozen) {
            Toast.makeText(activity, "录音已冻结，无法操作", Toast.LENGTH_SHORT).show()
            return
        }

        if (!AudioPermissionHelper.hasPermission(activity)) {
            AudioPermissionHelper.requestPermission(activity)
            return
        }

        if (isRecording) {
            // 停止录音
            audioRecordFeature.stopRecord()
            isRecording = false
            updateRecordIcon()
            Toast.makeText(activity, "录音已停止", Toast.LENGTH_SHORT).show()
        } else {
            // 开始录音
            audioRecordFeature.startRecord()
            isRecording = true
            updateRecordIcon()
            Toast.makeText(activity, "录音已开始", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 冷冻录音（禁用录音功能，改变图标）
     */
    fun freezeRecord() {
        isFrozen = true
        isRecording = false // 强制停止录音
        audioRecordFeature.stopRecord()
        updateRecordIcon()
        Log.d(TAG, "录音已冷冻，图标已更新")
    }

    /**
     * 解冻录音（恢复录音功能，恢复原图标）
     */
    fun unfreezeRecord() {
        isFrozen = false
        updateRecordIcon()
        Log.d(TAG, "录音已解冻，图标已恢复")
    }

    /**
     * 更新录音图标状态
     */
    private fun updateRecordIcon() {
        activity.runOnUiThread {
            when {
                isFrozen -> recordIcon?.setImageResource(RECORD_ICON_FROZEN)
                isRecording -> recordIcon?.setImageResource(RECORD_ICON_RECORDING)
                else -> recordIcon?.setImageResource(RECORD_ICON_NORMAL)
            }
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
                Log.d(TAG, "悬浮窗已隐藏")
            } catch (e: Exception) {
                Log.e(TAG, "隐藏悬浮窗失败：${e.message}", e)
            } finally {
                overlayView = null
                recordIcon = null
                windowManager = null
                // 清理拖动相关状态
                isDragging = false
                mainHandler.removeCallbacksAndMessages(null)
            }
        }
    }

    /**
     * 检查是否正在录音
     */
    fun isRecording(): Boolean = isRecording

    /**
     * 获取当前悬浮窗位置（可选，方便后续保存位置）
     */
    fun getCurrentPosition(): Pair<Int, Int> {
        val params = overlayView?.layoutParams as? WindowManager.LayoutParams
        return Pair(params?.x ?: 30, params?.y ?: 100)
    }
}