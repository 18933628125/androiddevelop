package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.features.AudioRecordFeature
import com.example.myapplication.features.OverlayFeature

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
}