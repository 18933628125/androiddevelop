package com.example.myapplication.features

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
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

        // 设置录音点击事件
        recordIcon?.setOnClickListener {
            if (isFrozen) {
                Toast.makeText(activity, "录音已冻结，无法操作", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!AudioPermissionHelper.hasPermission(activity)) {
                AudioPermissionHelper.requestPermission(activity)
                return@setOnClickListener
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

        // 配置WindowManager参数
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            x = 30
            y = 100

            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

            format = PixelFormat.TRANSLUCENT
        }

        // 添加悬浮窗
        try {
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "悬浮窗显示成功")
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败：${e.message}", e)
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
            }
        }
    }

    /**
     * 检查是否正在录音
     */
    fun isRecording(): Boolean = isRecording
}