package com.example.myapplication

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.features.AudioRecordFeature
import com.example.myapplication.features.OverlayFeature
import com.example.myapplication.permission.AudioPermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var overlayFeature: OverlayFeature
    private lateinit var audioRecordFeature: AudioRecordFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioRecordFeature = AudioRecordFeature(this)
        overlayFeature = OverlayFeature(this, audioRecordFeature)
        overlayFeature.show()
    }

    /**
     * 仅处理**录音权限**回调（删除截图权限相关代码）
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AudioPermissionHelper.REQUEST_CODE) {
            if (grantResults.isNotEmpty()) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("MainActivity", "录音权限被拒绝")
                    Toast.makeText(this, "需要录音权限才能使用录音功能", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayFeature.hide()
    }
}