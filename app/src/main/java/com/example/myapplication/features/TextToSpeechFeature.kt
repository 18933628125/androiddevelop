package com.example.myapplication.features

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TextToSpeechFeature(private val context: Context) {
    private val TAG = "TextToSpeechFeature"
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "中文语音数据缺失或不支持")
                } else {
                    isInitialized = true
                    Log.d(TAG, "TextToSpeech 初始化成功")
                    // 如果有待播报的文本，立即播报
                    pendingText?.let {
                        speak(it)
                        pendingText = null
                    }
                }
            } else {
                Log.e(TAG, "TextToSpeech 初始化失败，状态码: $status")
            }
        }
    }

    fun speak(text: String) {
        if (!isInitialized) {
            Log.d(TAG, "TextToSpeech 尚未初始化，暂存文本: $text")
            pendingText = text
            return
        }

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "utteranceId"

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
        Log.d(TAG, "播报语音: $text")
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        isInitialized = false
    }
}
