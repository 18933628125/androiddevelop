package com.example.myapplication.features

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.TextView
import com.example.myapplication.permission.OverlayPermissionHelper

class OverlayFeature(
    private val activity: Activity,
    private val audioRecordFeature: AudioRecordFeature
) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    fun show() {
        // 悬浮窗权限检查
        if (!OverlayPermissionHelper.hasPermission(activity)) {
            OverlayPermissionHelper.requestPermission(activity)
            return
        }

        if (overlayView != null) return

        windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 悬浮窗参数配置
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 300
        params.y = 600

        // 创建悬浮窗视图
        val view = TextView(activity).apply {
            text = "🎙"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#88000000"))
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        // 触摸事件绑定（逻辑无变化）
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    audioRecordFeature.startRecord()
                    view.setBackgroundColor(Color.RED)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    audioRecordFeature.stopRecord()
                    view.setBackgroundColor(Color.parseColor("#88000000"))
                    true
                }

                else -> false
            }
        }

        windowManager?.addView(view, params)
        overlayView = view
    }

    fun hide() {
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
    }
}