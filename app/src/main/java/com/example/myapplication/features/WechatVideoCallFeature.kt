package com.example.myapplication.features

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.example.myapplication.permission.AssistsPermissionHelper
import com.ven.assists.AssistsCore
import kotlinx.coroutines.delay

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

        try {
            // 1. 打开微信
            openWechat()

            // 等待微信启动
            Log.d(TAG, "等待微信启动...")
            delay(5000)

            // 2. 点击搜索按钮
            clickSearchButton()

            // 3. 点击搜索框并输入联系人
            performSearch(contactName)

            // 4. 点击第一个联系人进入聊天界面
            clickFirstContact()

            // 5. 点击拓展按钮（+号）
            clickExpandButton()

            // 6. 点击视频通话按钮
            clickVideoCallButton()

            // 7. 确认视频通话
            confirmVideoCall()

            Log.d(TAG, "微信视频通话流程执行完毕")
        } catch (e: Exception) {
            Log.e(TAG, "微信视频通话流程异常: ${e.message}", e)
            showToast("流程执行失败: ${e.message}")
        } finally {
            onComplete()
        }
    }

    private suspend fun clickSearchButton() {
        Log.d(TAG, "点击搜索按钮 (890, 201)")
        try {
            val result = AssistsCore.gestureClick(890f, 201f, 200L)
            Log.d(TAG, "点击搜索按钮结果: $result")
            delay(2000)
        } catch (e: Exception) {
            Log.e(TAG, "点击搜索按钮异常: ${e.message}", e)
            throw e
        }
    }

    private suspend fun performSearch(contactName: String) {
        Log.d(TAG, "开始搜索联系人: $contactName")

        try {
            // 点击搜索输入框位置 (540, 237)
            Log.d(TAG, "点击搜索输入框位置: (540, 237)")
            val clickResult = AssistsCore.gestureClick(540f, 237f, 200L)
            Log.d(TAG, "点击搜索框结果: $clickResult")
            delay(500)

            // 输入联系人名称
            Log.d(TAG, "输入联系人: $contactName")
            inputText(contactName)

            delay(1000)
        } catch (e: Exception) {
            Log.e(TAG, "搜索联系人异常: ${e.message}", e)
            throw e
        }
    }

    private suspend fun inputText(text: String) {
        Log.d(TAG, "开始输入文字: $text")

        // 使用 ClipboardManager 复制粘贴
        try {
            Log.d(TAG, "使用剪贴板粘贴")
            val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("contact", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "文字已复制到剪贴板: $text")

            // 长按搜索框 (540, 237) 约5秒调出粘贴菜单
            Log.d(TAG, "长按搜索框 (540, 237) 5秒调出粘贴菜单")
            AssistsCore.gestureClick(540f, 237f, 5000L)
            delay(1000)

            // 点击粘贴按钮 (130, 380)
            Log.d(TAG, "点击粘贴按钮 (130, 380)")
            val pasteResult = AssistsCore.gestureClick(130f, 380f, 200L)
            Log.d(TAG, "点击粘贴按钮结果: $pasteResult")
            delay(1000)

            Log.d(TAG, "文字粘贴完成")
        } catch (e: Exception) {
            Log.e(TAG, "剪贴板粘贴失败: ${e.message}", e)
        }
    }

    private suspend fun clickFirstContact() {
        Log.d(TAG, "点击第一个联系人 (540, 527)")
        try {
            val result = AssistsCore.gestureClick(540f, 527f, 200L)
            Log.d(TAG, "点击第一个联系人结果: $result")
            delay(2000)
        } catch (e: Exception) {
            Log.e(TAG, "点击第一个联系人异常: ${e.message}", e)
            throw e
        }
    }

    private suspend fun clickExpandButton() {
        Log.d(TAG, "点击拓展按钮 (1018, 2260)")
        try {
            val result = AssistsCore.gestureClick(1018f, 2260f, 200L)
            Log.d(TAG, "点击拓展按钮结果: $result")
            delay(1500)
        } catch (e: Exception) {
            Log.e(TAG, "点击拓展按钮异常: ${e.message}", e)
            throw e
        }
    }

    private suspend fun clickVideoCallButton() {
        Log.d(TAG, "点击视频通话按钮 (657, 1836)")
        try {
            val result = AssistsCore.gestureClick(657f, 1836f, 200L)
            Log.d(TAG, "点击视频通话按钮结果: $result")
            delay(1500)
        } catch (e: Exception) {
            Log.e(TAG, "点击视频通话按钮异常: ${e.message}", e)
            throw e
        }
    }

    private suspend fun confirmVideoCall() {
        Log.d(TAG, "确认视频通话 (536, 1943)")
        try {
            val result = AssistsCore.gestureClick(536f, 1943f, 200L)
            Log.d(TAG, "确认视频通话结果: $result")
            delay(2000)
            Log.d(TAG, "视频通话已发起")
        } catch (e: Exception) {
            Log.e(TAG, "确认视频通话异常: ${e.message}", e)
            throw e
        }
    }

    private fun openWechat() {
        try {
            val intent = android.content.Intent().apply {
                setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            activity.startActivity(intent)
            Log.d(TAG, "直接启动微信 LauncherUI 成功")
        } catch (e: Exception) {
            Log.e(TAG, "直接启动微信失败：${e.message}")
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
