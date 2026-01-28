package com.example.myapplication.features

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.myapplication.permission.AudioPermissionHelper
import com.example.myapplication.permission.ScreenshotPermissionHelper
import com.example.myapplication.services.AudioRecordService
import java.io.File

class AudioRecordFeature(
    private val activity: Activity,
    private val onRecordAndScreenshotComplete: (File?, File?, String) -> Unit
) {
    private val TAG = "AudioRecordFeature"
    private var isRecording = false
    private var outputFile: File? = null
    private val screenshotFeature = ScreenshotFeature(activity)
    private var screenshotFile: File? = null
    private var threadId: String = ""
    private val handler = Handler(Looper.getMainLooper())

    fun startRecord() {
        if (!AudioPermissionHelper.hasPermission(activity)) {
            AudioPermissionHelper.requestPermission(activity)
            Log.e("AudioRecord", "无录音权限，已请求")
            return
        }
        if (isRecording) return

        threadId = System.currentTimeMillis().toString()
        outputFile = createOutputFile()
        if (outputFile == null) {
            Log.e("AudioRecord", "录音文件创建失败")
            return
        }

        try {
            val startIntent = Intent(activity, AudioRecordService::class.java).apply {
                action = AudioRecordService.ACTION_START_RECORD
                putExtra(AudioRecordService.EXTRA_FILE_PATH, outputFile!!.absolutePath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(startIntent)
            } else {
                activity.startService(startIntent)
            }
            isRecording = true
            Log.d("AudioRecord", "开始录音：${outputFile!!.absolutePath}，thread_id：$threadId")
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            Log.e("AudioRecord", "启动录音失败：${e.message}")
        }
    }

    fun stopRecord() {
        if (!isRecording) return
        try {
            // 停止录音
            val stopIntent = Intent(activity, AudioRecordService::class.java).apply {
                action = AudioRecordService.ACTION_STOP_RECORD
            }
            activity.startService(stopIntent)

            // 处理截图权限和截图
            handleScreenshotWithPermission()

        } catch (e: Exception) {
            e.printStackTrace()
            onRecordAndScreenshotComplete(outputFile, null, threadId)
        } finally {
            isRecording = false
            Log.d("AudioRecord", "停止录音，thread_id：$threadId")
        }
    }

    /**
     * 处理截图权限并执行异步截图
     */
    private fun handleScreenshotWithPermission() {
        if (ScreenshotPermissionHelper.mediaProjectionResultData == null) {
            Log.w(TAG, "无截图权限，申请中...")
            ScreenshotPermissionHelper.requestScreenCapturePermission(activity)

            // 等待权限授权
            var waitCount = 0
            val maxWaitCount = 20 // 10秒
            val checkPermissionRunnable = object : Runnable {
                override fun run() {
                    waitCount++
                    if (ScreenshotPermissionHelper.mediaProjectionResultData != null) {
                        // 权限已获取，执行异步截图
                        performAsyncScreenshot()
                    } else if (waitCount < maxWaitCount) {
                        handler.postDelayed(this, 500)
                    } else {
                        Log.e(TAG, "截图权限申请超时")
                        onRecordAndScreenshotComplete(outputFile, null, threadId)
                    }
                }
            }
            handler.postDelayed(checkPermissionRunnable, 500)
        } else {
            // 已有权限，直接异步截图
            performAsyncScreenshot()
        }
    }

    /**
     * 执行异步截图
     */
    private fun performAsyncScreenshot() {
        Log.d(TAG, "开始异步截图，thread_id：$threadId")
        screenshotFeature.takeScreenshotAsync(threadId) { screenshotFile ->
            this.screenshotFile = screenshotFile
            Log.d(TAG, "异步截图完成，文件：${screenshotFile?.absolutePath}")

            // 切回主线程回调
            handler.post {
                onRecordAndScreenshotComplete(outputFile, screenshotFile, threadId)
            }
        }
    }

    private fun createOutputFile(): File? {
        val saveDir = screenshotFeature.getSaveDir()
        if (!checkAndCreateDir(saveDir)) {
            Log.e("AudioRecord", "目录创建失败")
            return null
        }
        return File(saveDir, "audio_$threadId.m4a")
    }

    private fun checkAndCreateDir(dir: File): Boolean {
        if (dir.exists() && dir.isDirectory) return true
        return dir.mkdirs()
    }

    fun isRecording(): Boolean = isRecording
    fun reset() {
        isRecording = false
        outputFile = null
        screenshotFile = null
        threadId = ""
        handler.removeCallbacksAndMessages(null)
    }
}