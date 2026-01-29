package com.example.myapplication.state

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.myapplication.R
import com.example.myapplication.features.CircleOverlayFeature
import com.example.myapplication.features.ScreenshotFeature
import com.example.myapplication.features.WaitOverlayManager
import com.example.myapplication.utils.HttpUtils
import java.io.File

/**
 * 决策状态机 - 处理click/wait/end三种状态流转
 */
class DecisionStateMachine(
    private val activity: Activity,
    private val circleOverlayFeature: CircleOverlayFeature,
    private val screenshotFeature: ScreenshotFeature,
    private val threadId: String,
    // 回调：状态机结束
    private val onStateMachineEnd: () -> Unit
) {
    private val TAG = "DecisionStateMachine"
    private val handler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper()) // 主线程Handler
    private var currentState: State = State.IDLE
    private lateinit var waitOverlayManager: WaitOverlayManager // 替换原有的Toast
    private var isRunning = false

    // 状态枚举
    enum class State {
        IDLE,        // 空闲
        INIT,        // 初始状态（状态1）
        CLICK,       // 点击状态（状态2）
        WAIT,        // 等待状态（状态3）
        FEEDBACK,    // 反馈状态（状态4）
        END          // 结束状态（状态5）
    }

    init {
        // 初始化等待悬浮窗管理器
        waitOverlayManager = WaitOverlayManager(activity)
    }

    /**
     * 启动状态机（初始状态1）
     */
    fun start(initialAction: String, data: Map<String, Any>) {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "状态机启动，初始指令：$initialAction，thread_id：$threadId")
        handleAction(initialAction, data)
    }

    /**
     * 处理后端返回的动作指令
     */
    private fun handleAction(actionType: String, data: Map<String, Any>) {
        when (actionType.lowercase()) {
            "click" -> enterClickState(data)
            "wait" -> enterWaitState(data)
            "end" -> enterEndState()
            else -> {
                Log.e(TAG, "未知指令：$actionType")
                enterEndState()
            }
        }
    }

    /**
     * 进入状态2：点击状态
     */
    private fun enterClickState(data: Map<String, Any>) {
        currentState = State.CLICK
        Log.d(TAG, "进入点击状态（状态2）")

        // 解析点击参数
        val x = (data["x"] as? Double ?: 0.0).toInt()
        val y = (data["y"] as? Double ?: 0.0).toInt()
        val radius = (data["radiu"] as? Double ?: 30.0).toInt()

        // 显示提示信息
        showToast("请点击绿色圆框位置：($x, $y)")

        // 显示绿色圆形悬浮窗
        circleOverlayFeature.showCircleOverlay(
            x = x,
            y = y,
            r = radius,
            // 点击回调：用户点击了圆框
            onClick = {
                Log.d(TAG, "用户完成点击动作，等待1秒进入状态4")
                // 隐藏圆框
                circleOverlayFeature.hideCircleOverlay()

                // 等待1秒后进入反馈状态
                handler.postDelayed({
                    enterFeedbackState()
                }, 1000)
            }
        )
    }

    /**
     * 进入状态3：等待状态（使用系统级悬浮窗，后台也可见）
     */
    private fun enterWaitState(data: Map<String, Any>) {
        currentState = State.WAIT
        val totalSeconds = data["seconds"] as? Double ?: 0.0
        var remainingSeconds = totalSeconds
        Log.d(TAG, "进入等待状态（状态3），总时长：$totalSeconds 秒")

        // 显示系统级等待悬浮窗（替代Toast，后台也可见）
        waitOverlayManager.showWaitOverlay(remainingSeconds)

        // 倒计时逻辑
        val countdownRunnable = object : Runnable {
            override fun run() {
                remainingSeconds -= 0.1
                if (remainingSeconds > 0) {
                    // 更新倒计时显示
                    waitOverlayManager.updateCountdown(remainingSeconds)
                    handler.postDelayed(this, 100)
                } else {
                    // 倒计时结束
                    waitOverlayManager.hideWaitOverlay()
                    Log.d(TAG, "等待计时结束，等待1秒进入状态4")

                    // 等待1秒后进入反馈状态
                    handler.postDelayed({
                        enterFeedbackState()
                    }, 1000)
                }
            }
        }
        handler.post(countdownRunnable)
    }

    /**
     * 进入状态4：反馈状态（截图+发送到/decision/feedback）
     */
    private fun enterFeedbackState() {
        currentState = State.FEEDBACK
        Log.d(TAG, "进入反馈状态（状态4），开始截图并发送反馈")

        // 执行截图
        screenshotFeature.takeScreenshotAsync(threadId) { screenshotFile ->
            if (screenshotFile != null && screenshotFile.exists()) {
                // 发送反馈请求
                sendFeedbackRequest(screenshotFile)
            } else {
                Log.e(TAG, "截图失败，无法发送反馈")
                showToast("截图失败，状态机结束")
                enterEndState()
            }
        }
    }

    /**
     * 发送反馈请求到/decision/feedback
     */
    private fun sendFeedbackRequest(screenshotFile: File) {
        Log.d(TAG, "进入反馈状态，延迟2秒发送feedback请求")
        handler.postDelayed({HttpUtils.sendFeedback(
            threadId = threadId,
            imageFile = screenshotFile,
            callback = { isSuccess, response, errorMsg ->
                if (isSuccess && response != null) {
                    // 解析反馈结果
                    parseFeedbackResponse(response)
                } else {
                    Log.e(TAG, "反馈请求失败：$errorMsg")
                    showToast("反馈失败：$errorMsg")
                    enterEndState()
                }
            }
        )},2000)
    }

    /**
     * 解析反馈接口返回的结果
     */
    private fun parseFeedbackResponse(response: String) {
        try {
            // 简单JSON解析（实际项目建议用Gson/Moshi）
            val cleanResponse = response.replace("{", "").replace("}", "").replace("\"", "")
            val keyValuePairs = cleanResponse.split(",").associate {
                val parts = it.split(":")
                parts[0].trim() to parts[1].trim()
            }

            val actionType = keyValuePairs["action_type"] ?: "end"
            val data = mutableMapOf<String, Any>()

            when (actionType) {
                "click" -> {
                    data["x"] = keyValuePairs["x"]?.toDouble() ?: 0.0
                    data["y"] = keyValuePairs["y"]?.toDouble() ?: 0.0
                    data["radiu"] = keyValuePairs["radiu"]?.toDouble() ?: 0.0
                }
                "wait" -> {
                    data["seconds"] = keyValuePairs["seconds"]?.toDouble() ?: 0.0
                }
            }

            // 继续处理下一个动作
            handleAction(actionType, data)

        } catch (e: Exception) {
            Log.e(TAG, "解析反馈结果失败：${e.message}", e)
            showToast("解析反馈结果失败：${e.message}")
            enterEndState()
        }
    }

    /**
     * 进入状态5：结束状态
     */
    private fun enterEndState() {
        currentState = State.END
        isRunning = false
        // 确保隐藏等待悬浮窗
        waitOverlayManager.hideWaitOverlay()
        Log.d(TAG, "进入结束状态（状态5），解除录音冷冻并结束状态机")

        // 清理资源
        handler.removeCallbacksAndMessages(null)
        circleOverlayFeature.hideCircleOverlay()

        // 回调：解除录音冷冻
        onStateMachineEnd.invoke()

        // 提示用户
        showToast("任务完成，状态机结束")
    }

    /**
     * 安全显示Toast
     */
    private fun showToast(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        } else {
            mainHandler.post {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 停止状态机
     */
    fun stop() {
        isRunning = false
        currentState = State.IDLE
        handler.removeCallbacksAndMessages(null)
        waitOverlayManager.hideWaitOverlay() // 隐藏等待悬浮窗
        circleOverlayFeature.hideCircleOverlay()
        Log.d(TAG, "状态机已手动停止")
    }

    /**
     * 获取当前状态
     */
    fun getCurrentState(): State = currentState
}