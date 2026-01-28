package com.example.myapplication.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Assists辅助功能权限工具类：检查+引导开启
 */
object AssistsPermissionHelper {
    private const val TAG = "AssistsPermissionHelper"

    /**
     * 检查Assists辅助功能是否已开启
     */
    fun isAssistsEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        // 获取已开启的辅助服务列表
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        // 遍历检查目标服务是否开启（替换为你的AssistsService完整类名）
        for (serviceInfo in enabledServices) {
            val serviceName = serviceInfo.resolveInfo.serviceInfo.name
            // 你的AssistsService类名：com.ven.assists.service.AssistsService
            if ("com.ven.assists.service.AssistsService" == serviceName) {
                return true
            }
        }
        return false
    }

    /**
     * 跳转到辅助功能设置页面，引导用户开启Assists权限
     */
    fun openAssistsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}