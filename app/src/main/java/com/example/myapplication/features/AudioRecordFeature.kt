package com.example.myapplication.features

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.myapplication.permission.AudioPermissionHelper
import com.example.myapplication.services.AudioRecordService
import java.io.File

class AudioRecordFeature(
    private val activity: Activity
) {
    private var isRecording = false
    private var outputFile: File? = null
    // 初始化截图功能类（全局截图）
    private val screenshotFeature = ScreenshotFeature(activity)

    fun startRecord() {
        // 检查录音权限
        if (!AudioPermissionHelper.hasPermission(activity)) {
            AudioPermissionHelper.requestPermission(activity)
            Log.e("AudioRecord", "没有录音权限，已请求")
            return
        }
        if (isRecording) return

        // 创建录音文件（和截图同目录）
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
            // 启动前台服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(startIntent)
            } else {
                activity.startService(startIntent)
            }
            isRecording = true
            Log.d("AudioRecord", "开始录音：${outputFile!!.absolutePath}")
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

            // 触发全局截图（支持后台）
            screenshotFeature.takeScreenshot()

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isRecording = false
            Log.d("AudioRecord", "停止录音，已触发全局截图")
        }
    }

    /**
     * 创建录音文件（和截图同目录）
     */
    private fun createOutputFile(): File? {
        val saveDir = screenshotFeature.getSaveDir()
        val isDirReady = checkAndCreateDir(saveDir)
        if (!isDirReady) {
            Log.e("AudioRecord", "录音目录准备失败")
            return null
        }
        return File(saveDir, "audio_${System.currentTimeMillis()}.m4a")
    }

    /**
     * 检查并创建目录
     */
    private fun checkAndCreateDir(dir: File): Boolean {
        if (dir.exists() && dir.isDirectory) return true
        return dir.mkdirs()
    }
}