package com.example.myapplication.features

import android.app.Activity
import android.graphics.*
import android.os.Build
import android.util.Log
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
    private val TAG = "CircleOverlayFeature"
    private var windowManager: WindowManager? = null
    private var circleView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var targetClickX = 0
    private var targetClickY = 0
    private var isShowing = false

    // 完全保留你的原始方法，仅新增onClick回调适配状态机
    fun showCircleOverlay(
        x: Int,
        y: Int,
        r: Int,
        onClick: () -> Unit = {} // 仅新增：点击回调，不影响原有逻辑
    ) {
        // 1. 保留你的原始权限检查逻辑
        if (!OverlayPermissionHelper.hasPermission(activity)) {
            OverlayPermissionHelper.requestPermission(activity)
            return
        }

        if (!AssistsPermissionHelper.isAssistsEnabled(activity)) {
            Toast.makeText(activity, "请先开启辅助功能权限！", Toast.LENGTH_LONG).show()
            AssistsPermissionHelper.openAssistsSettings(activity)
            return
        }

        // 2. 保留你的原始坐标保存逻辑
        targetClickX = x
        targetClickY = y
        hideCircleOverlay()

        // 3. 保留你的原始WindowManager获取逻辑
        windowManager = activity.getSystemService(WindowManager::class.java)

        // 4. 完全保留你的自定义View绘制逻辑
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
                canvas.drawCircle(centerX, centerY, r.toFloat(), paint) // 你的原始绘制逻辑
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    hideCircleOverlay()
                    // 保留你的原始延迟50ms逻辑
                    activity.window.decorView.postDelayed({
                        simulateClickUnderOverlay()
                        onClick.invoke() // 仅新增：触发状态机回调
                    }, 50)
                    return false // 保留你的原始返回值
                }
                return true // 保留你的原始返回值
            }
        }

        // 5. 完全保留你的原始Window参数逻辑（核心：坐标计算正确）
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            r * 2,
            r * 2,
            layoutType,
            // 保留你的原始flags（无多余flags）
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START // 保留你的原始gravity
            // 核心：保留你的原始坐标计算（这是坐标正确的关键）
            this.x = targetClickX - r
            this.y = targetClickY - r

            // 仅新增：适配Android P+的刘海屏（不影响坐标）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 6. 保留你的原始添加逻辑，仅新增日志
        try {
            windowManager?.addView(circleView, params)
            isShowing = true
            Log.d(TAG, "圆形悬浮窗显示成功：圆心($targetClickX, $targetClickY)，半径$r，左上角坐标(${params?.x}, ${params?.y})")
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败：${e.message}", e)
            e.printStackTrace()
        }
    }

    // 7. 完全保留你的原始AssistsCore模拟点击逻辑
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

                // 保留你的200ms点击时长
                val duration = 200

                // 核心：你的AssistsCore穿透点击逻辑
                val isSuccess = AssistsCore.gestureClick(xFloat, yFloat, duration.toLong())

                if (isSuccess) {
                    Toast.makeText(activity, "模拟点击指令发送成功！", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "模拟点击坐标($xFloat, $yFloat)，时长$duration ms，指令发送成功")
                } else {
                    Toast.makeText(activity, "模拟点击指令发送失败！", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "模拟点击指令发送失败")
                }
            } catch (e: Exception) {
                Toast.makeText(activity, "模拟点击异常：${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "调用Assists API失败：${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    // 8. 保留你的原始坐标转换方法
    fun getRealScreenCoordinate(x: Int, y: Int): Pair<Int, Int> {
        val decorView = activity.window.decorView
        val location = IntArray(2)
        decorView.getLocationOnScreen(location)
        val realX = x - location[0]
        val realY = y - location[1]
        Log.d(TAG, "坐标转换：原始($x, $y) → 偏移(${location[0]}, ${location[1]}) → 真实($realX, $realY)")
        return Pair(realX, realY)
    }

    // 9. 保留你的原始隐藏方法，仅新增日志
    fun hideCircleOverlay() {
        circleView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "悬浮窗已隐藏")
            } catch (e: Exception) {
                Log.e(TAG, "隐藏悬浮窗失败：${e.message}", e)
                e.printStackTrace()
            }
        }
        circleView = null
        params = null
        isShowing = false
    }

    // 10. 保留你的原始释放方法
    fun release() {
        hideCircleOverlay()
        windowManager = null
    }

    // 新增：适配状态机的状态检查（非核心）
    fun isShowing(): Boolean = isShowing
}