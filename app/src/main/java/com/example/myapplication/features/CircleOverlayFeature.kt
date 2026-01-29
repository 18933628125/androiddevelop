package com.example.myapplication.features

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.DisplayMetrics
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
    private var layoutParams: WindowManager.LayoutParams? = null

    // 最终使用的【物理屏幕绝对坐标】，左上角(0,0)
    private var absoluteClickX = 0f
    private var absoluteClickY = 0f
    private var circleRadius = 0

    private var isShowing = false

    /**
     * 外部调用不变，仍然传入千分比 x, y, radiu
     * x: 0~1000 ‰ 屏幕宽度
     * y: 0~1000 ‰ 屏幕高度
     * 内部全部转换为【物理屏幕绝对坐标】，不受状态栏影响
     */
    fun showCircleOverlay(
        x: Int,
        y: Int,
        r: Int,
        onClick: () -> Unit = {}
    ) {
        // 权限检查
        if (!OverlayPermissionHelper.hasPermission(activity)) {
            OverlayPermissionHelper.requestPermission(activity)
            Toast.makeText(activity, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            return
        }
        if (!AssistsPermissionHelper.isAssistsEnabled(activity)) {
            Toast.makeText(activity, "请开启辅助功能", Toast.LENGTH_LONG).show()
            AssistsPermissionHelper.openAssistsSettings(activity)
            return
        }

        // 1. 计算【物理屏幕真实宽高】（不再获取状态栏高度）
        val realScreenSize = getRealScreenSize()
        val screenWidth = realScreenSize.first
        val screenHeight = realScreenSize.second

        Log.d(TAG, "物理屏幕真实宽高: ${screenWidth}x$screenHeight")

        // 2. 千分比 → 物理屏幕绝对坐标（0,0 = 屏幕最左上角）
        val percentX = x / 1000f
        val percentY = y / 1000f
        val rawX = screenWidth * percentX
        val rawY = screenHeight * percentY

        Log.d(TAG, "千分比转换后原始坐标: ($rawX, $rawY)")

        // 3. 不再减去状态栏高度！直接使用原始物理坐标
        absoluteClickX = rawX
        absoluteClickY = rawY
        circleRadius = r

        val overlayX = rawX.toInt()
        val overlayY = rawY.toInt()

        Log.d(TAG, "最终物理点击坐标(AssistsCore使用): ($absoluteClickX, $absoluteClickY)")
        Log.d(TAG, "悬浮窗实际布局坐标: ($overlayX, $overlayY)")

        // 先隐藏旧视图
        hideCircleOverlay()

        windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 4. 自定义绘制绿色实心圆
        circleView = object : View(activity) {
            private val circlePaint = Paint().apply {
                isAntiAlias = true
                color = Color.GREEN
                alpha = 160
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, circleRadius.toFloat(), circlePaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    hideCircleOverlay()
                    // 延迟执行，保证圆先消失再点击
                    postDelayed({
                        performAssistsClick()
                        onClick()
                    }, 30)
                    return true
                }
                return true
            }
        }

        // 5. 关键 Window 参数，强制全屏、无视系统栏
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                .or(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                .or(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                .or(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                .or(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        layoutParams = WindowManager.LayoutParams(
            circleRadius * 2,
            circleRadius * 2,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            // 直接使用原始物理坐标，不再减半径外的任何值
            this.x = overlayX - circleRadius
            this.y = overlayY - circleRadius

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 6. 添加到窗口
        try {
            windowManager?.addView(circleView, layoutParams)
            isShowing = true
            Log.d(TAG, "绿色实心圆已显示（物理屏幕坐标系）")
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮窗失败", e)
            Toast.makeText(activity, "显示点击区域失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 获取物理屏幕真实分辨率（包含状态栏、导航栏）
     */
    private fun getRealScreenSize(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.getRealMetrics(metrics)
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * 使用 AssistsCore 在【物理屏幕绝对坐标】执行点击
     */
    private fun performAssistsClick() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                if (!AssistsPermissionHelper.isAssistsEnabled(activity)) {
                    Toast.makeText(activity, "辅助功能未开启", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d(TAG, "执行模拟点击: ($absoluteClickX, $absoluteClickY)")
                val success = AssistsCore.gestureClick(absoluteClickX, absoluteClickY, 200L)

                if (success) {
                    Toast.makeText(activity, "点击成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "点击失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "模拟点击异常", e)
                Toast.makeText(activity, "点击异常: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun hideCircleOverlay() {
        circleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮窗失败", e)
            }
        }
        circleView = null
        layoutParams = null
        isShowing = false
    }

    fun isShowing(): Boolean = isShowing
    fun release() = hideCircleOverlay()
}