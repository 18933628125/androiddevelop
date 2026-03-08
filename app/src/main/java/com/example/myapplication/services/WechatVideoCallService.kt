package com.example.myapplication.services

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WechatVideoCallService(private val activity: Activity) {
    private val TAG = "WechatVideoCallService"
    
    suspend fun performWechatVideoCall(contactName: String, onComplete: () -> Unit) {
        Log.d(TAG, "开始微信视频通话自动化流程，联系人：$contactName")
        
        // 1. 打开微信
        openWechat()
        
        // 2. 等待搜索按钮并点击
        val searchBtn = waitForNode {
            AssistsCore.getAllNodes().firstOrNull {
                it.contentDescription == "搜索"
            }
        }
        if (searchBtn != null) {
            val result = searchBtn.click()
            Log.d(TAG, "点击微信搜索成功")
        } else {
            Log.d(TAG, "找不到微信搜索按钮")
            showToast("找不到微信搜索按钮")
            onComplete()
            return
        }
        
        // 3. 等待输入框并输入联系人名称
        val inputBox = waitForNode {
            AssistsCore.findById("com.tencent.mm:id/dk1").firstOrNull()
                ?: AssistsCore.findById("com.tencent.mm:id/b4k").firstOrNull()
                ?: AssistsCore.findByTags("android.widget.EditText").firstOrNull()
        }
        if (inputBox != null) {
            inputBox.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = android.os.Bundle().apply {
                putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, contactName)
            }
            inputBox.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "输入联系人 $contactName 成功")
        } else {
            Log.d(TAG, "找不到输入框")
            showToast("找不到输入框")
            onComplete()
            return
        }
        
        delay(1000)
        
        // 4. 等待联系人出现并点击
        val contactNode = waitForNode {
            AssistsCore.findByText(contactName).firstOrNull {
                it.viewIdResourceName == "com.tencent.mm:id/odf"
            }
        }
        if (contactNode != null) {
            contactNode.click()
            Log.d(TAG, "点击联系人 $contactName 成功")
        } else {
            Log.d(TAG, "找不到联系人 $contactName")
            showToast("找不到联系人 $contactName")
            onComplete()
            return
        }
        
        delay(1000)
        
        // 5. 等待+号按钮并点击
        val plusBtn = waitForNode {
            AssistsCore.getAllNodes().firstOrNull {
                it.contentDescription == "更多功能按钮"
            }
        }
        if (plusBtn != null) {
            plusBtn.click()
            Log.d(TAG, "点击+号按钮成功")
        } else {
            Log.d(TAG, "找不到+号按钮")
            showToast("找不到+号按钮")
            onComplete()
            return
        }
        
        delay(1000)
        
        // 6. 等待视频通话选项并点击
        val videoCallOption = waitForNode {
            AssistsCore.findByText("视频通话").firstOrNull()
        }
        if (videoCallOption != null) {
            videoCallOption.click()
            Log.d(TAG, "点击视频通话选项成功")
        } else {
            Log.d(TAG, "找不到视频通话选项")
            showToast("找不到视频通话选项")
            onComplete()
            return
        }
        
        delay(1000)
        
        // 7. 等待视频通话确认并点击
        val videoCallConfirm = waitForNode {
            AssistsCore.findByText("视频通话").firstOrNull()
        }
        if (videoCallConfirm != null) {
            videoCallConfirm.click()
            Log.d(TAG, "点击视频通话确认成功")
            showToast("微信视频通话已发起")
        } else {
            Log.d(TAG, "找不到视频通话确认按钮")
            showToast("找不到视频通话确认按钮")
        }
        
        onComplete()
    }
    
    private suspend fun waitForNode(nodeFinder: () -> android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
        repeat(20) {
            val node = nodeFinder()
            if (node != null) {
                return node
            }
            delay(500)
        }
        return null
    }
    
    private fun openWechat() {
        try {
            val intent = activity.packageManager.getLaunchIntentForPackage("com.tencent.mm")
            if (intent != null) {
                activity.startActivity(intent)
                Log.d(TAG, "打开微信成功")
            } else {
                Log.d(TAG, "未安装微信")
                showToast("未安装微信")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开微信失败：${e.message}")
            showToast("打开微信失败")
        }
    }
    
    private fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}