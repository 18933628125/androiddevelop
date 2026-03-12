package com.example.myapplication.features

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.ven.assists.AssistsCore
import com.ven.assists.AssistsCore.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WechatVideoCallFeature(private val activity: Activity) {
    private val TAG = "WechatVideoCallFeature"

    suspend fun performWechatVideoCall(contactName: String, onComplete: () -> Unit) {
        Log.d(TAG, "开始微信视频通话自动化流程，联系人：$contactName")

        // 1. 打开微信
        openWechat()


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
