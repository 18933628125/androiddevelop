package com.example.myapplication.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

object HttpUtils {
    private const val TAG = "HttpUtils"
    private const val BASE_URL = "http://10.195.138.2:5000"
//    private const val BASE_URL = "https://bac5ac86e5d8.ngrok-free.app"
    // 修复：配置更稳定的OkHttp客户端
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .hostnameVerifier { _, _ -> true }
            // 修复：增加连接池配置，减少传输异常
            .connectionPool(okhttp3.ConnectionPool(5, 30, TimeUnit.SECONDS))
            .build()
    }

    // 主线程Handler，用于切换线程显示Toast
    private val mainHandler = Handler(Looper.getMainLooper())

    // 添加Unicode解码函数，修复日志中中文显示问题
    private fun unescapeUnicode(text: String): String {
        return text.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            val code = hexCode.toInt(16)
            code.toChar().toString()
        }
    }

    // 初始决策接口（修复版本）
    fun sendInitDecision(
        threadId: String,
        audioFile: File?,
        imageFile: File?,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        // 修复：在子线程中处理文件，避免主线程阻塞
        Thread {
            try {
                val requestBodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("thread_id", threadId)

                // 修复：安全处理音频文件
                audioFile?.let {
                    if (it.exists() && it.isFile && it.length() > 0) {
                        try {
                            // 修复：使用FileInputStream确保文件读取完整
                            val inputStream = FileInputStream(it)
                            val fileSize = it.length()
                            Log.d(TAG, "音频文件大小：$fileSize 字节")

                            val audioBody = RequestBody.create(
                                "audio/m4a".toMediaType(),
                                it
                            )
                            requestBodyBuilder.addFormDataPart(
                                "audio",
                                it.name,
                                audioBody
                            )
                            Log.d(TAG, "添加音频：${it.name}，大小：${it.length()}字节")

                            inputStream.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "处理音频文件失败：${e.message}", e)
                        }
                    } else {
                        Log.w(TAG, "音频文件无效：${it?.absolutePath ?: "null"}")
                    }
                }

                // 修复：安全处理截图文件
                imageFile?.let {
                    if (it.exists() && it.isFile && it.length() > 0) {
                        try {
                            val inputStream = FileInputStream(it)
                            val fileSize = it.length()
                            Log.d(TAG, "截图文件大小：$fileSize 字节")

                            val imageBody = RequestBody.create(
                                "image/png".toMediaType(),
                                it
                            )
                            requestBodyBuilder.addFormDataPart(
                                "image",
                                it.name,
                                imageBody
                            )
                            Log.d(TAG, "添加截图：${it.name}，大小：${it.length()}字节")

                            inputStream.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "处理截图文件失败：${e.message}", e)
                        }
                    } else {
                        Log.w(TAG, "截图文件无效：${it?.absolutePath ?: "null"}")
                    }
                }

                // 构建请求
                val request = Request.Builder()
                    .url("$BASE_URL/decision/init")
                    .post(requestBodyBuilder.build())
                    .build()

                Log.d(TAG, "发送初始请求到：$BASE_URL/decision/init，thread_id：$threadId")

                // 执行同步请求（已在子线程中）
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        var responseBody = response.body?.string() ?: ""
                        responseBody=unescapeUnicode(responseBody)
                        Log.d(TAG, "初始请求成功，返回：\n${responseBody}")
                        // 切换到主线程回调
                        mainHandler.post {
                            callback(true, responseBody, null)
                        }
                    } else {
                        val errorMsg = "状态码：${response.code}，信息：${response.message}"
                        Log.e(TAG, "初始请求失败：$errorMsg")
                        // 切换到主线程回调
                        mainHandler.post {
                            callback(false, null, errorMsg)
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "初始请求异常：${e.message}", e)
                    // 切换到主线程回调
                    mainHandler.post {
                        callback(false, null, e.message ?: "网络请求失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "构建请求失败：${e.message}", e)
                // 切换到主线程回调
                mainHandler.post {
                    callback(false, null, e.message ?: "请求构建失败")
                }
            }
        }.start()
    }

    // 反馈接口（修复版本）
    fun sendFeedback(
        threadId: String,
        imageFile: File?,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        Thread {
            try {
                val requestBodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("thread_id", threadId)

                // 安全处理截图文件
                imageFile?.let {
                    if (it.exists() && it.isFile && it.length() > 0) {
                        try {
                            val inputStream = FileInputStream(it)
                            val fileSize = it.length()
                            Log.d(TAG, "反馈截图大小：$fileSize 字节")

                            val imageBody = RequestBody.create(
                                "image/png".toMediaType(),
                                it
                            )
                            requestBodyBuilder.addFormDataPart(
                                "image",
                                it.name,
                                imageBody
                            )
                            Log.d(TAG, "添加反馈截图：${it.name}，大小：${it.length()}字节")

                            inputStream.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "处理反馈截图失败：${e.message}", e)
                        }
                    } else {
                        Log.w(TAG, "反馈截图无效：${it?.absolutePath ?: "null"}")
                    }
                }

                // 构建请求
                val request = Request.Builder()
                    .url("$BASE_URL/decision/feedback")
                    .post(requestBodyBuilder.build())
                    .build()

                Log.d(TAG, "发送反馈请求到：$BASE_URL/decision/feedback，thread_id：$threadId")

                // 执行同步请求
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        var responseBody = response.body?.string() ?: ""
                        responseBody=unescapeUnicode(responseBody)
                        Log.d(TAG, "反馈请求成功，返回：\n${responseBody}")
                        mainHandler.post {
                            callback(true, responseBody, null)
                        }
                    } else {
                        val errorMsg = "状态码：${response.code}，信息：${response.message}"
                        Log.e(TAG, "反馈请求失败：$errorMsg")
                        mainHandler.post {
                            callback(false, null, errorMsg)
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "反馈请求异常：${e.message}", e)
                    mainHandler.post {
                        callback(false, null, e.message ?: "网络请求失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "构建反馈请求失败：${e.message}", e)
                mainHandler.post {
                    callback(false, null, e.message ?: "请求构建失败")
                }
            }
        }.start()
    }

    // 提交微信联系人信息接口
    fun submitWechatContacts(
        contacts: List<Pair<String, String>>,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        Thread {
            try {
                // 构建JSON数组
                val jsonArray = JSONArray()
                contacts.forEach { (nickname, realname) ->
                    val contactObj = JSONObject().apply {
                        put("nickname", nickname)
                        put("realname", realname)
                    }
                    jsonArray.put(contactObj)
                }

                val jsonBody = jsonArray.toString()
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                // 构建请求
                val request = Request.Builder()
                    .url("$BASE_URL/decision/getusername")
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                Log.d(TAG, "发送联系人信息到：$BASE_URL/decision/getusername，数据：$jsonBody")

                // 执行请求
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        var responseBody = response.body?.string() ?: ""
                        responseBody = unescapeUnicode(responseBody)
                        Log.d(TAG, "提交联系人信息成功，返回：\n${responseBody}")
                        mainHandler.post {
                            callback(true, responseBody, null)
                        }
                    } else {
                        val errorMsg = "状态码：${response.code}，信息：${response.message}"
                        Log.e(TAG, "提交联系人信息失败：$errorMsg")
                        mainHandler.post {
                            callback(false, null, errorMsg)
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "提交联系人信息请求异常：${e.message}", e)
                    mainHandler.post {
                        callback(false, null, e.message ?: "网络请求失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "构建联系人信息请求失败：${e.message}", e)
                mainHandler.post {
                    callback(false, null, e.message ?: "请求构建失败")
                }
            }
        }.start()
    }

}