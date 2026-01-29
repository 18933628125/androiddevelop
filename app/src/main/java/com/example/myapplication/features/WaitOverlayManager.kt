package com.example.myapplication.features

import android.app.Activity
import android.graphics.PixelFormat
import android.os.Build
import android.service.autofill.Validators.or
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.myapplication.R

/**
 * 等待状态的系统级悬浮窗管理器（后台也能显示）
 */
class WaitOverlayManager(private val activity: Activity) {
    private val TAG = "WaitOverlayManager"
    private var windowManager: WindowManager? = null
    private var waitView: View? = null
    private var tvCountdown: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var isShowing = false

    /**
     * 初始化并显示等待悬浮窗
     */
    fun showWaitOverlay(initialSeconds: Double) {
        // 检查悬浮窗权限
        if (!android.provider.Settings.canDrawOverlays(activity)) {
            Log.w(TAG, "无悬浮窗权限，无法显示等待悬浮窗")
            return
        }

        if (isShowing) {
            updateCountdown(initialSeconds)
            return
        }

        // 初始化WindowManager
        windowManager = activity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(activity)
        waitView = inflater.inflate(R.layout.overlay_wait, null)
        tvCountdown = waitView?.findViewById(R.id.tv_countdown)
        tvCountdown?.text = String.format("请等待：%.1f秒", initialSeconds)

        // 配置悬浮窗参数（系统级，后台也能显示）
        params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.CENTER

            // 关键：使用系统级窗口类型
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    format = PixelFormat.TRANSLUCENT
            // 适配刘海屏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 添加悬浮窗到WindowManager
        try {
            waitView?.let {
                windowManager?.addView(it, params)
                isShowing = true
                Log.d(TAG, "等待悬浮窗显示成功（后台也可见）")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示等待悬浮窗失败：${e.message}", e)
        }
    }

    /**
     * 更新倒计时显示
     */
    fun updateCountdown(remainingSeconds: Double) {
        if (!isShowing) return
        activity.runOnUiThread {
            tvCountdown?.text = String.format("请等待：%.1f秒", remainingSeconds)
        }
    }

    /**
     * 隐藏等待悬浮窗
     */
    fun hideWaitOverlay() {
        if (isShowing && waitView != null && windowManager != null) {
            try {
                windowManager?.removeView(waitView)
                Log.d(TAG, "等待悬浮窗已隐藏")
            } catch (e: Exception) {
                Log.e(TAG, "隐藏等待悬浮窗失败：${e.message}", e)
            } finally {
                waitView = null
                tvCountdown = null
                windowManager = null
                isShowing = false
            }
        }
    }

    /**
     * 检查是否正在显示
     */
    fun isShowing(): Boolean = isShowing
}