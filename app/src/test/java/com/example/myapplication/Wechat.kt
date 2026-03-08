package com.cike.phoneassist

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import com.ven.assists.utils.CoroutineWrapper
import kotlinx.coroutines.delay

suspend fun sendGetUserSync(user: String): StepResponse {
    // 等待搜索按钮并点击
    val searchBtn = waitForNode {
        AssistsCore.getAllNodes().firstOrNull {
            it.contentDescription == "搜索"
        }
    }
    if (searchBtn != null) {
        val result = searchBtn.click()
//        val rect = Rect()
//        searchBtn.getBoundsInScreen(rect)
//        val x = (rect.left + rect.right) / 2f
//        val y = (rect.top + rect.bottom) / 2f
//        CoroutineWrapper.launch {
//            AssistsCore.gestureClick(x, y)
//        }
        Log.d("MyTag", "点击微信搜索成功")
    }
    else{
        Log.d("MyTag", "找不到微信搜索按钮")
        return StepResponse(false, "找不到微信搜索按钮")
    }
    // 等待输入框并输入内容
    val inputBox = waitForNode {
        AssistsCore.findById("com.tencent.mm:id/dk1").firstOrNull()
            ?: AssistsCore.findById("com.tencent.mm:id/b4k").firstOrNull()
            ?: AssistsCore.findByTags("android.widget.EditText").firstOrNull()
    }
    if (inputBox != null) {
        inputBox?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, user)
            }
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        Log.d("MyTag", "输入 $user 成功")
    }
    else{
        Log.d("MyTag", "找不到输入框")
        return StepResponse(false, "找不到输入框")
    }

    delay(1000)

    // 等待联系人出现并点击
    val contactNode = waitForNode {
        AssistsCore.findByText(user).firstOrNull {
            it.viewIdResourceName == "com.tencent.mm:id/odf"
        }
    }
    if (contactNode != null) {
        val rect = Rect()
        contactNode.getBoundsInScreen(rect)
        val x = (rect.left + rect.right) / 2f
        val y = (rect.top + rect.bottom) / 2f
        CoroutineWrapper.launch {
            AssistsCore.gestureClick(x, y)
        }
        Log.d("MyTag", "点击联系人成功")
        return StepResponse(true, "")
    }
    else {
        Log.d("MyTag", "找不到联系人")
        return StepResponse(false, "找不到联系人")
    }
}

suspend fun sendMessageSync(message: String): StepResponse {
    // 等待输入框并输入内容
    val chatInput = waitForNode {
        AssistsCore.findById("com.tencent.mm:id/b4k").firstOrNull()
            ?: AssistsCore.findById("com.tencent.mm:id/dk1").firstOrNull()
            ?: AssistsCore.findByTags("android.widget.EditText").firstOrNull()
    }

    if (chatInput != null) {
        chatInput.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
        }
        chatInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d("MyTag", "输入框输入$message 成功")
    }
    else{
        Log.d("MyTag", "找不到输入框")
        return StepResponse(false, "找不到输入框")
    }
    // 等待发送按钮并点击
    val sendBtn = waitForNode {
        AssistsCore.findById("com.tencent.mm:id/bql").firstOrNull()}
    if (sendBtn != null) {
        sendBtn.click()
        Log.d("MyTag", "点击发送成功")
        return StepResponse(true, "")
    } else {
        Log.d("MyTag", "找不到发送按钮")
        return StepResponse(false, "找不到发送按钮")
    }
}



