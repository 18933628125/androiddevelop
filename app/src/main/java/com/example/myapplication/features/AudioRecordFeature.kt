package com.example.myapplication.features

import android.app.Activity
import android.content.Intent
import android.os.Environment
import android.util.Log
import com.example.myapplication.permission.AudioPermissionHelper
import com.example.myapplication.services.AudioRecordService
import java.io.File

class AudioRecordFeature(
    private val activity: Activity
) {
    private var isRecording = false
    private var outputFile: File? = null

    fun startRecord() {
        // 二次检查录音权限
        if (!AudioPermissionHelper.hasPermission(activity)) {
            AudioPermissionHelper.requestPermission(activity)
            Log.e("AudioRecord", "没有录音权限，已请求")
            return
        }

        if (isRecording) return

        // 创建录音文件（路径已修改）
        outputFile = createOutputFile()

        try {
            // 构建启动录音的Intent
            val startIntent = Intent(activity, AudioRecordService::class.java).apply {
                action = AudioRecordService.ACTION_START_RECORD
                putExtra(AudioRecordService.EXTRA_FILE_PATH, outputFile!!.absolutePath)
            }

            // 启动前台服务（适配Android O+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                activity.startForegroundService(startIntent)
            } else {
                activity.startService(startIntent)
            }

            isRecording = true
            Log.d("AudioRecord", "请求后台录音：${outputFile!!.absolutePath}")

        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            Log.e("AudioRecord", "启动录音失败：${e.message}")
        }
    }

    fun stopRecord() {
        if (!isRecording) return

        try {
            // 构建停止录音的Intent
            val stopIntent = Intent(activity, AudioRecordService::class.java).apply {
                action = AudioRecordService.ACTION_STOP_RECORD
            }

            // 发送停止录音指令
            activity.startService(stopIntent)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isRecording = false
            Log.d("AudioRecord", "停止后台录音")
        }
    }

    /**
     * 核心修改：创建录音输出文件（路径改为/storage/emulated/0/Alarms/Test）
     */
    private fun createOutputFile(): File {
        // 1. 获取公共Alarms目录
        val alarmsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS)
        // 2. 创建Test子目录
        val testDir = File(alarmsDir, "Test")
        // 3. 确保目录存在（不存在则创建）
        if (!testDir.exists()) {
            testDir.mkdirs() // 递归创建目录
            Log.d("AudioRecord", "创建Test目录：${testDir.absolutePath}")
        }

        // 4. 创建录音文件（时间戳命名）
        return File(
            testDir,
            "audio_${System.currentTimeMillis()}.m4a"
        )
    }
}