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
    private var maskView: View? = null // 改为全屏蒙版视图
    private var layoutParams: WindowManager.LayoutParams? = null

    // 最终使用的【物理屏幕绝对坐标】，左上角(0,0)
    private var absoluteClickX = 0f
    private var absoluteClickY = 0f
    private var circleRadius = 0
    // 屏幕尺寸
    private var screenWidth = 0
    private var screenHeight = 0

    private var isShowing = false

    /**
     * 外部调用不变，仍然传入千分比 x, y, radiu
     * x: 0~1000 ‰ 屏幕宽度
     * y: 0~1000 ‰ 屏幕高度
     * 核心修改：全屏红色蒙版 + 绿色可点击圆
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

        // 1. 获取物理屏幕真实宽高
        val realScreenSize = getRealScreenSize()
        screenWidth = realScreenSize.first
        screenHeight = realScreenSize.second

        Log.d(TAG, "物理屏幕真实宽高: ${screenWidth}x$screenHeight")

        // 2. 千分比 → 物理屏幕绝对坐标（0,0 = 屏幕最左上角）
        val percentX = x / 1000f
        val percentY = y / 1000f
        absoluteClickX = screenWidth * percentX
        absoluteClickY = screenHeight * percentY

        // 3. 半径放大2倍
        circleRadius = r * 2
        Log.d(TAG, "原始半径：$r，放大2倍后：$circleRadius")
        Log.d(TAG, "可点击区域坐标: ($absoluteClickX, $absoluteClickY)，半径: $circleRadius")

        // 先隐藏旧视图
        hideCircleOverlay()

        windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 4. 创建全屏蒙版视图（红色背景 + 绿色可点击圆）
        maskView = object : View(activity) {
            // 红色蒙版画笔（半透明）
            private val redMaskPaint = Paint().apply {
                isAntiAlias = true
                color = Color.RED
                alpha = 128 // 半透明（0-255）
                style = Paint.Style.FILL
            }
            // 绿色可点击圆画笔
            private val greenCirclePaint = Paint().apply {
                isAntiAlias = true
                color = Color.GREEN
                alpha = 160
                style = Paint.Style.FILL
            }
            // 路径裁剪：用于绘制除绿色圆外的红色蒙版
            private val clipPath = Path()

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                // 初始化裁剪路径：整个屏幕区域减去绿色圆区域
                clipPath.reset()
                // 添加整个屏幕区域
                clipPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
                // 减去绿色圆区域（反向）
                clipPath.addCircle(
                    absoluteClickX,
                    absoluteClickY,
                    circleRadius.toFloat(),
                    Path.Direction.CW
                )
                clipPath.fillType = Path.FillType.EVEN_ODD
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                // 第一步：绘制全屏红色蒙版（除了绿色圆区域）
                canvas.save()
                canvas.clipPath(clipPath)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), redMaskPaint)
                canvas.restore()

                // 第二步：绘制绿色可点击圆
                canvas.drawCircle(
                    absoluteClickX,
                    absoluteClickY,
                    circleRadius.toFloat(),
                    greenCirclePaint
                )
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    val touchX = event.rawX
                    val touchY = event.rawY

                    // 计算点击位置到圆心的距离
                    val distance = Math.sqrt(
                        Math.pow((touchX - absoluteClickX).toDouble(), 2.0) +
                                Math.pow((touchY - absoluteClickY).toDouble(), 2.0)
                    )

                    // 判断是否点击在绿色圆范围内
                    if (distance <= circleRadius) {
                        Log.d(TAG, "点击在绿色圆范围内，触发响应")
                        hideCircleOverlay()
                        // 延迟执行，保证蒙版先消失再点击
                        postDelayed({
                            performAssistsClick()
                            onClick()
                        }, 500)
                    } else {
                        Log.d(TAG, "点击在红色蒙版区域，忽略响应")
                        // 可选：给用户提示
                        // Toast.makeText(activity, "请点击绿色圆圈区域", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                // 拦截所有触摸事件，防止透传到下层界面
                return true
            }
        }

        // 5. 配置全屏Window参数
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
                .or(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // 全屏宽度
            WindowManager.LayoutParams.MATCH_PARENT, // 全屏高度
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 6. 添加全屏蒙版到WindowManager
        try {
            maskView?.let {
                windowManager?.addView(it, layoutParams)
                isShowing = true
                Log.d(TAG, "全屏红色蒙版+绿色可点击圆已显示")
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加全屏蒙版失败", e)
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
        maskView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "移除全屏蒙版失败", e)
            }
        }
        maskView = null
        layoutParams = null
        isShowing = false
    }

    fun isShowing(): Boolean = isShowing
    fun release() = hideCircleOverlay()
}