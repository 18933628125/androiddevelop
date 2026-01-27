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
import androidx.core.app.NotificationCompat
import com.example.myapplication.R

class AudioRecordService : Service() {
    // 服务相关常量
    private val CHANNEL_ID = "AudioRecordChannel_1001"
    private val NOTIFICATION_ID = 1001

    // Intent参数常量
    companion object {
        const val ACTION_START_RECORD = "com.example.myapplication.action.START_RECORD"
        const val ACTION_STOP_RECORD = "com.example.myapplication.action.STOP_RECORD"
        const val EXTRA_FILE_PATH = "com.example.myapplication.extra.FILE_PATH"
    }

    // 录音相关变量
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFilePath: String? = null

    override fun onCreate() {
        super.onCreate()
        // 创建通知渠道（Android O+ 必须）
        createNotificationChannel()
        // 服务创建后立即启动前台（关键：避免超时）
        startForeground(NOTIFICATION_ID, createEmptyNotification())
        Log.d("AudioRecordService", "服务创建并启动前台")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_RECORD -> {
                    // 从Intent获取文件路径
                    currentFilePath = it.getStringExtra(EXTRA_FILE_PATH)
                    currentFilePath?.let { path ->
                        startRecording(path)
                    } ?: Log.e("AudioRecordService", "录音文件路径为空")
                }
                ACTION_STOP_RECORD -> {
                    stopRecording()
                    // 停止服务
                    stopSelf(startId)
                }
            }
        }
        // 服务被杀死后自动重启
        return START_STICKY
    }

    /**
     * 开始录音（内部方法）
     */
    private fun startRecording(filePath: String) {
        if (isRecording) return

        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(filePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d("AudioRecordService", "后台录音开始：$filePath")

            // 更新通知内容为“录音中”
            updateNotification("录音中", "正在录制音频...")

        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            Log.e("AudioRecordService", "录音启动失败：${e.message}")
        }
    }

    /**
     * 停止录音（内部方法）
     */
    private fun stopRecording() {
        if (!isRecording) return

        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder = null
            isRecording = false
            currentFilePath = null
            Log.d("AudioRecordService", "后台录音结束")

            // 更新通知内容为“录音已停止”
            updateNotification("录音已停止", "音频文件已保存")
        }
    }

    /**
     * 创建空通知（用于服务启动时快速前台化）
     */
    private fun createEmptyNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("录音服务")
            .setContentText("服务已启动")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * 更新通知内容
     */
    private fun updateNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "录音服务",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "用于后台悬浮窗录音的前台服务"
            channel.setSound(null, null) // 关闭通知声音
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // 服务销毁时确保停止录音
        stopRecording()
        Log.d("AudioRecordService", "录音服务已销毁")
    }
}