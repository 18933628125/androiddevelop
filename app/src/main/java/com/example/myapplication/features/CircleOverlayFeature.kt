package com.example.myapplication.features

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build

import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CircleOverlayFeature(
    private val activity: Activity,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private val TAG = "CircleOverlayFeature"
    private var windowManager: WindowManager? = null
    private var circleView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    /**
     * 显示圆形悬浮窗（带点击回调）
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun showCircleOverlay(
        x: Int,
        y: Int,
        r: Int,
        onClick: () -> Unit = {}
    ) {
        // 先隐藏之前的悬浮窗
        hideCircleOverlay()

        // 检查悬浮窗权限
        if (!android.provider.Settings.canDrawOverlays(activity)) {
            Log.e(TAG, "没有悬浮窗权限，请先授予悬浮窗权限")
            // 引导用户开启悬浮窗权限
            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            activity.startActivity(intent)
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                // 创建圆形View（确保能看到的绿色半透明圆）
                circleView = View(activity).apply {
                    // 设置圆形背景（高透明度绿色，确保可见）
                    setBackgroundColor(Color.parseColor("#9900FF00")) // 60%透明度绿色
                    // 设置圆角为圆形
                    this.clipToOutline = true
                    this.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, r.toFloat())
                        }
                    }
                    layoutParams = FrameLayout.LayoutParams(r * 2, r * 2)

                    // 设置点击事件
                    setOnClickListener {
                        Log.d(TAG, "圆形悬浮窗被点击，坐标：($x, $y)")
                        // 执行点击回调
                        onClick.invoke()
                        // 模拟点击下方元素
                        simulateClick(x, y)
                    }

                    // 确保可点击
                    isClickable = true
                    isFocusable = true
                }

                // 配置WindowManager参数（关键修复：确保悬浮窗层级正确）
                windowParams = WindowManager.LayoutParams().apply {
                    width = r * 2
                    height = r * 2
                    val x = x + r // 居中显示
                    val y = y + r

                    // 适配Android版本（关键：使用正确的type）
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }

                    // 关键修复：调整flags确保能点击且显示在最上层
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.TOP or Gravity.START
                    // 设置悬浮窗优先级
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }

                // 添加到WindowManager（关键：确保添加成功）
                if (circleView != null && windowParams != null) {
                    windowManager?.addView(circleView, windowParams)
                    isShowing = true
                    Log.d(TAG, "圆形悬浮窗显示成功：x=$x, y=$y, radius=$r")
                } else {
                    Log.e(TAG, "悬浮窗View或参数为空")
                }

            } catch (e: Exception) {
                Log.e(TAG, "显示圆形悬浮窗失败：${e.message}", e)
                // 打印完整异常栈
                e.printStackTrace()
            }
        }
    }

    /**
     * 模拟点击屏幕坐标（原有逻辑）
     */
    private fun simulateClick(x: Int, y: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 执行shell命令模拟点击
                    val command = arrayOf("input", "tap", x.toString(), y.toString())
                    Runtime.getRuntime().exec(command)
                    Log.d(TAG, "已模拟点击坐标：($x, $y)")
                } catch (e: Exception) {
                    Log.e(TAG, "模拟点击失败：${e.message}", e)
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 隐藏圆形悬浮窗
     */
    fun hideCircleOverlay() {
        if (isShowing && circleView != null && windowManager != null) {
            try {
                windowManager?.removeView(circleView)
                Log.d(TAG, "圆形悬浮窗已隐藏")
            } catch (e: Exception) {
                Log.e(TAG, "隐藏悬浮窗失败：${e.message}", e)
                e.printStackTrace()
            } finally {
                circleView = null
                windowParams = null
                isShowing = false
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        hideCircleOverlay()
        windowManager = null
    }

    /**
     * 判断悬浮窗是否显示
     */
    fun isShowing(): Boolean = isShowing
}