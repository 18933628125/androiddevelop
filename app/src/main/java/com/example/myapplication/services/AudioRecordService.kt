package com.example.myapplication.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecordService : Service() {
    private val TAG = "AudioRecordService"
    private var mediaRecorder: MediaRecorder? = null
    private var outputFilePath: String? = null

    companion object {
        const val ACTION_START_RECORD = "com.example.myapplication.action.START_RECORD"
        const val ACTION_STOP_RECORD = "com.example.myapplication.action.STOP_RECORD"
        const val EXTRA_FILE_PATH = "file_path"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "AudioRecordService"
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "服务创建")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            ACTION_START_RECORD -> {
                outputFilePath = intent.getStringExtra(EXTRA_FILE_PATH)
                startRecording()
            }
            ACTION_STOP_RECORD -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (outputFilePath.isNullOrEmpty()) {
            Log.e(TAG, "录音文件路径为空")
            stopSelf()
            return
        }

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "服务创建并启动前台")

        // 初始化MediaRecorder
        mediaRecorder = MediaRecorder().apply {
            try {
                // 设置音频源
                setAudioSource(MediaRecorder.AudioSource.MIC)
                // 设置输出格式
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                // 设置音频编码
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // 设置输出文件
                setOutputFile(outputFilePath)

                // 准备并开始录音
                prepare()
                start()

                Log.d(TAG, "后台录音开始：$outputFilePath")
            } catch (e: IOException) {
                Log.e(TAG, "录音启动失败：${e.message}", e)
                stopSelf()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "MediaRecorder状态异常：${e.message}", e)
                stopSelf()
            }
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.d(TAG, "后台录音结束")

            // 验证文件是否生成
            val file = File(outputFilePath ?: "")
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "录音文件生成成功：${file.length()}字节")
            } else {
                Log.w(TAG, "录音文件为空或不存在")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败：${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "录音服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("录音中")
            .setContentText("正在后台录制音频")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        Log.d(TAG, "录音服务已销毁")
    }
}