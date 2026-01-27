package com.example.myapplication.features

import android.app.Activity
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import com.example.myapplication.permission.AudioPermissionHelper
import java.io.File

class AudioRecordFeature(
    private val activity: Activity
) {

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: File? = null

    fun startRecord() {

        //  关键：再次检查录音权限（悬浮窗必须这么做）
        if (!AudioPermissionHelper.hasPermission(activity)) {
            AudioPermissionHelper.requestPermission(activity)
            Log.e("AudioRecord", "没有录音权限，已请求")
            return
        }

        if (isRecording) return

        outputFile = createOutputFile()

        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile!!.absolutePath)

                prepare()
                start()
            }

            isRecording = true
            Log.d("AudioRecord", "开始录音：${outputFile!!.absolutePath}")

        } catch (e: Exception) {
            e.printStackTrace()
            recorder = null
            isRecording = false
        }
    }

    fun stopRecord() {
        if (!isRecording) return

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder = null
            isRecording = false
        }

        Log.d("AudioRecord", "录音结束")
    }

    private fun createOutputFile(): File {
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_ALARMS)!!
        if (!dir.exists()) dir.mkdirs()

        return File(
            dir,
            "audio_${System.currentTimeMillis()}.m4a"
        )
    }
}