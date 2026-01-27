package com.example.myapplication.permission

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object OverlayPermissionHelper {

    fun hasPermission(activity: Activity): Boolean {
        return Settings.canDrawOverlays(activity)
    }

    fun requestPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivity(intent)
    }
}