package com.example.myapplication.features

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ScreenshotFeature(private val activity: Activity) {
    private val TAG = "ScreenshotFeature"
    // 固定和录音同目录：应用外部私有目录的Test文件夹
    private val baseSaveDir by lazy { File(activity.getExternalFilesDir(null), "Test") }

    /**
     * 执行截图并保存到【应用外部私有目录】的Test文件夹（和录音同目录）
     * 核心逻辑：先检查目录→不存在则创建→创建成功后才保存截图
     * @return 截图文件路径（失败返回null）
     */
    fun takeScreenshot(): String? {
        // 第一步：检查并创建目标目录（核心修改）
        val isDirReady = checkAndCreateDir(baseSaveDir)
        if (!isDirReady) {
            Log.e(TAG, "目标目录准备失败，无法保存截图")
            return null
        }

        // 第二步：目录就绪后，创建截图文件
        val screenshotFile = File(baseSaveDir, "screenshot_${System.currentTimeMillis()}.png")

        return try {
            // 获取屏幕Bitmap（适配悬浮窗/后台场景）
            val bitmap = getScreenBitmap()
            // 第三步：保存Bitmap到文件
            saveBitmapToFile(bitmap, screenshotFile)
            // 释放Bitmap内存
            if (!bitmap.isRecycled) bitmap.recycle()
            Log.d(TAG, "截图保存成功：${screenshotFile.absolutePath}")
            screenshotFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "截图保存失败：${e.message}")
            null
        }
    }

    /**
     * 【核心工具方法】检查目录是否存在，不存在则创建
     * @param dir 目标目录
     * @return true=目录存在/创建成功；false=创建失败
     */
    private fun checkAndCreateDir(dir: File): Boolean {
        // 1. 目录已存在 → 直接返回成功
        if (dir.exists() && dir.isDirectory) {
            Log.d(TAG, "目标目录已存在：${dir.absolutePath}")
            return true
        }

        // 2. 目录不存在 → 尝试创建（mkdirs() 递归创建多级目录）
        val isCreated = dir.mkdirs()
        if (isCreated) {
            Log.d(TAG, "目标目录创建成功：${dir.absolutePath}")
            return true
        } else {
            Log.e(TAG, "目标目录创建失败：${dir.absolutePath}（可能是权限/路径错误）")
            return false
        }
    }

    /**
     * 获取屏幕完整Bitmap（适配所有Android版本，包括悬浮窗场景）
     */
    private fun getScreenBitmap(): Bitmap {
        val windowManager = activity.getSystemService(WindowManager::class.java)
        val display = windowManager.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics) // 获取真实屏幕尺寸（包含刘海/导航栏）

        // 创建和屏幕同尺寸的Bitmap
        val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 绘制当前屏幕的根视图
        activity.window.decorView.draw(canvas)
        return bitmap
    }

    /**
     * 保存Bitmap到文件（仅在目录就绪后调用）
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        var outputStream: OutputStream? = null
        try {
            outputStream = FileOutputStream(file)
            // PNG格式（无损，适合截图），质量100
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        } finally {
            outputStream?.close()
        }
    }

    /**
     * 对外提供保存目录（给录音功能调用，保证同目录）
     */
    fun getSaveDir(): File = baseSaveDir
}