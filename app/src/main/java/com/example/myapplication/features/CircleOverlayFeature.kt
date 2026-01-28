package com.example.myapplication.features

import android.app.Activity
import android.graphics.*
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.example.myapplication.permission.AssistsPermissionHelper
import com.example.myapplication.permission.OverlayPermissionHelper
import com.ven.assists.AssistsCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CircleOverlayFeature(
    private val activity: Activity,
    private val coroutineScope: CoroutineScope
) {
    private var windowManager: WindowManager? = null
    private var circleView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var targetClickX = 0
    private var targetClickY = 0

    fun showCircleOverlay(x: Int, y: Int, r: Int) {
        if (!OverlayPermissionHelper.hasPermission(activity)) {
            OverlayPermissionHelper.requestPermission(activity)
            return
        }

        if (!AssistsPermissionHelper.isAssistsEnabled(activity)) {
            Toast.makeText(activity, "请先开启辅助功能权限！", Toast.LENGTH_LONG).show()
            AssistsPermissionHelper.openAssistsSettings(activity)
            return
        }

        targetClickX = x
        targetClickY = y
        hideCircleOverlay()

        windowManager = activity.getSystemService(WindowManager::class.java)

        circleView = object : View(activity) {
            private val paint = Paint().apply {
                color = Color.GREEN
                isAntiAlias = true
                alpha = 180
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val centerX = width / 2f
                val centerY = height / 2f
                canvas.drawCircle(centerX, centerY, r.toFloat(), paint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    hideCircleOverlay()
                    // 优化：延迟50ms再点击（确保浮窗完全隐藏，底层元素可响应）
                    activity.window.decorView.postDelayed({
                        simulateClickUnderOverlay()
                    }, 50)
                    return false
                }
                return true
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            r * 2,
            r * 2,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = targetClickX - r
            this.y = targetClickY - r
        }

        try {
            windowManager?.addView(circleView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun simulateClickUnderOverlay() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                if (!AssistsPermissionHelper.isAssistsEnabled(activity)) {
                    Toast.makeText(activity, "辅助功能权限已关闭！", Toast.LENGTH_SHORT).show()
                    AssistsPermissionHelper.openAssistsSettings(activity)
                    return@launch
                }

                val xFloat = targetClickX.toFloat()
                val yFloat = targetClickY.toFloat()

                // ========== 核心优化：调整点击参数 ==========
                // 1. 延长点击时长（200ms，适配大部分元素）
                val duration = 200


                val isSuccess = AssistsCore.gestureClick(xFloat, yFloat, duration.toLong())

                if (isSuccess) {
                    Toast.makeText(activity, "模拟点击指令发送成功！", Toast.LENGTH_SHORT).show()
                    android.util.Log.d("CircleOverlay", "模拟点击坐标($xFloat, $yFloat)，时长$duration ms，指令发送成功")
                } else {
                    Toast.makeText(activity, "模拟点击指令发送失败！", Toast.LENGTH_SHORT).show()
                    android.util.Log.e("CircleOverlay", "模拟点击指令发送失败")
                }
            } catch (e: Exception) {
                Toast.makeText(activity, "模拟点击异常：${e.message}", Toast.LENGTH_SHORT).show()
                android.util.Log.e("CircleOverlay", "调用Assists API失败：${e.message}")
                e.printStackTrace()
            }
        }
    }

    // 新增：获取屏幕真实坐标（排除状态栏/导航栏偏移）
    fun getRealScreenCoordinate(x: Int, y: Int): Pair<Int, Int> {
        val decorView = activity.window.decorView
        val location = IntArray(2)
        decorView.getLocationOnScreen(location)
        // 真实坐标 = 传入坐标 - 装饰视图的偏移（适配沉浸式/状态栏）
        val realX = x - location[0]
        val realY = y - location[1]
        return Pair(realX, realY)
    }

    fun hideCircleOverlay() {
        circleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        circleView = null
        params = null
    }

    fun release() {
        hideCircleOverlay()
        windowManager = null
    }
}