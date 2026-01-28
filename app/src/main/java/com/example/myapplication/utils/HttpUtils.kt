package com.example.myapplication.utils

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object HttpUtils {
    private const val TAG = "HttpUtils"
    // 替换为你的实际后端地址
    private const val BASE_URL = "http://10.195.143.13:5000"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // 开启自动重试
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * 发送录音+截图数据到后端
     */
    fun sendInitDecision(
        threadId: String,
        audioFile: File?,
        imageFile: File?,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("thread_id", threadId)

        // 添加音频文件
        audioFile?.let {
            if (it.exists() && it.isFile && it.length() > 0) {
                val audioBody = it.asRequestBody("audio/m4a".toMediaType())
                requestBodyBuilder.addFormDataPart(
                    "audio",
                    it.name,
                    audioBody
                )
                Log.d(TAG, "添加音频：${it.name}，大小：${it.length()}字节")
            } else {
                Log.w(TAG, "音频文件无效：${it.absolutePath}")
            }
        }

        // 添加截图文件（增加最后检查）
        imageFile?.let {
            // 最后一次检查文件
            if (!it.exists()) {
                Log.w(TAG, "截图文件不存在，等待1秒再检查...")
                Thread.sleep(1000)
            }

            if (it.exists() && it.isFile && it.length() > 0) {
                val imageBody = it.asRequestBody("image/png".toMediaType())
                requestBodyBuilder.addFormDataPart(
                    "image",
                    it.name,
                    imageBody
                )
                Log.d(TAG, "添加截图：${it.name}，大小：${it.length()}字节")
            } else {
                Log.w(TAG, "截图文件无效：${it?.absolutePath ?: "null"}")
            }
        }

        // 构建请求
        val request = Request.Builder()
            .url("$BASE_URL/decision/init")
            .post(requestBodyBuilder.build())
            .build()

        Log.d(TAG, "发送请求到：$BASE_URL/decision/init，thread_id：$threadId")

        // 执行请求
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "请求失败：${e.message}", e)
                callback(false, null, e.message ?: "网络请求失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.d(TAG, "请求成功，返回：\n$responseBody")
                    callback(true, responseBody, null)
                } else {
                    val errorMsg = "状态码：${response.code}，信息：${response.message}"
                    Log.e(TAG, "请求失败：$errorMsg")
                    callback(false, null, errorMsg)
                }
            }
        })
    }

    /**
     * 测试网络连接
     */
    fun testNetworkConnection(callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/health")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(false, e.message)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                callback(response.isSuccessful, "状态码：${response.code}")
            }
        })
    }
}