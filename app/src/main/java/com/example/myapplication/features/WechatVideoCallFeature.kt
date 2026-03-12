package com.example.myapplication.features

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.example.myapplication.permission.AssistsPermissionHelper
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WechatVideoCallFeature(private val activity: Activity) {
    private val TAG = "WechatVideoCallFeature"

    suspend fun performWechatVideoCall(contactName: String, onComplete: () -> Unit) {
        Log.d(TAG, "开始微信视频通话自动化流程，联系人：$contactName")

        // 检查辅助功能权限
        if (!AssistsPermissionHelper.isAssistsEnabled(activity)) {
            Log.e(TAG, "辅助功能未开启")
            showToast("请先开启辅助功能")
            return
        }

        // 1. 打开微信
        openWechat()

        // 等待微信启动
        Log.d(TAG, "等待微信启动...")
        delay(5000)

        // 2. 尝试使用坐标点击搜索按钮（不依赖节点获取）
        clickSearchButtonByCoordinate()

    }

    private suspend fun clickSearchButtonByCoordinate() {
        Log.d(TAG, "使用绝对坐标点击搜索按钮")

        // 方式1：使用 gestureClick 点击坐标 (890, 201)
        Log.d(TAG, "尝试使用 gestureClick(890, 201)")
        try {
            val result1 = AssistsCore.gestureClick(890f, 201f, 200L)
            Log.d(TAG, "gestureClick(890, 201) 结果: $result1")
            if (result1) {
                Log.d(TAG, "点击成功，等待搜索界面加载...")
                delay(2000)
                // 继续后续流程...
                performSearch(contactName = "小曾")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "gestureClick 异常: ${e.message}", e)
        }

        // 方式2：如果方式1失败，尝试稍微调整坐标
        Log.d(TAG, "方式1失败，尝试调整坐标 (885, 200)")
        try {
            val result2 = AssistsCore.gestureClick(885f, 200f, 200L)
            Log.d(TAG, "gestureClick(885, 200) 结果: $result2")
            if (result2) {
                Log.d(TAG, "点击成功，等待搜索界面加载...")
                delay(2000)
                performSearch(contactName = "小曾")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "gestureClick 异常: ${e.message}", e)
        }

        Log.e(TAG, "所有点击方式均失败")
    }

    private suspend fun performSearch(contactName: String) {
        Log.d(TAG, "开始搜索联系人: $contactName")

        // 等待搜索框出现并输入文字
        delay(1000)

        // 尝试使用 gestureClick 点击搜索输入框位置 (假设在屏幕中间)
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        val searchBoxX = screenWidth / 2f
        val searchBoxY = screenHeight * 0.15f  // 搜索框通常在屏幕上方

        Log.d(TAG, "点击搜索输入框位置: ($searchBoxX, $searchBoxY)")
        val clickResult = AssistsCore.gestureClick(searchBoxX, searchBoxY, 200L)
        Log.d(TAG, "点击搜索框结果: $clickResult")

        delay(500)

        // 输入联系人名称（使用系统输入法）
        // 注意：AssistsCore 可能没有直接输入文字的方法，需要通过其他方式
        // 这里先打印日志，后续可以实现输入逻辑
        Log.d(TAG, "需要输入联系人: $contactName")

        // 由于微信阻止了节点获取，后续流程可能需要完全依赖坐标点击
        // 或者使用其他方式（如 adb shell input）来输入文字
    }

    private fun openWechat() {
        try {
            // 直接启动微信的 LauncherUI
            val intent = android.content.Intent().apply {
                setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            activity.startActivity(intent)
            Log.d(TAG, "直接启动微信 LauncherUI 成功")
        } catch (e: Exception) {
            Log.e(TAG, "直接启动微信失败：${e.message}")
            // 如果直接启动失败，尝试使用包名启动
            try {
                val intent = activity.packageManager.getLaunchIntentForPackage("com.tencent.mm")
                if (intent != null) {
                    activity.startActivity(intent)
                    Log.d(TAG, "使用包名启动微信成功")
                } else {
                    Log.d(TAG, "未安装微信")
                    showToast("未安装微信")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "打开微信失败：${e2.message}")
                showToast("打开微信失败")
            }
        }
    }

    private fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
