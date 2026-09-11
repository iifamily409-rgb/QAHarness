package com.example.qaharness

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.qaharness.databinding.ActivityMainBinding
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>

    private val captureResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != CaptureService.ACTION_ANALYSIS_RESULT) {
                return
            }

            val result = intent.getSerializableExtra(CaptureService.EXTRA_ANALYSIS_RESULT) as? AnalysisResult
                ?: return
            renderAnalysisResult(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                appendLog("MediaProjection permission denied by user.")
                return@registerForActivityResult
            }

            val data = result.data
            if (data == null) {
                appendLog("MediaProjection data was null.")
                return@registerForActivityResult
            }

            val serviceIntent = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            appendLog("Capture service started.")
        }

        binding.startCaptureButton.setOnClickListener {
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
        }

        val filter = IntentFilter(CaptureService.ACTION_ANALYSIS_RESULT)
        registerReceiver(captureResultReceiver, filter)
        appendLog("Application ready. Press Start Capture to begin.")
    }

    override fun onDestroy() {
        unregisterReceiver(captureResultReceiver)
        super.onDestroy()
    }

    private fun renderAnalysisResult(result: AnalysisResult) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(result.frameBytes, 0, result.frameBytes.size)
            if (bitmap != null) {
                binding.previewImage.setImageBitmap(bitmap)
            }
            binding.previewOverlay.setAnalysisResult(result)
            binding.logTextView.text = result.logs.joinToString(separator = "\n")
            Log.d(TAG, "Rendered ${result.pieces.size} pieces and ${result.pockets.size} pockets.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render analysis result", e)
        }
    }

    private fun appendLog(message: String) {
        val current = binding.logTextView.text.toString()
        val updated = if (current.isBlank()) message else "$current\n$message"
        binding.logTextView.text = updated
    }
}
