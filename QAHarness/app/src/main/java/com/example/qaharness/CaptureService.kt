package com.example.qaharness

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class CaptureService : Service() {

    companion object {
        private const val TAG = "CaptureService"
        private const val CHANNEL_ID = "qa_capture_channel"
        private const val NOTIFICATION_ID = 42
        const val ACTION_ANALYSIS_RESULT = "com.example.qaharness.ANALYSIS_RESULT"
        const val EXTRA_ANALYSIS_RESULT = "extra_analysis_result"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CAPTURE_WIDTH = 1280
        private const val CAPTURE_HEIGHT = 720
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameLock = Any()
    private var isProcessing = false

    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        captureThread = HandlerThread("CaptureThread").apply { start() }
        captureHandler = Handler(captureThread.looper)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == 0 || resultData == null) {
            Log.e(TAG, "Missing MediaProjection result.")
            stopSelf()
            return START_NOT_STICKY
        }

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
        startProjection()
        return START_STICKY
    }

    override fun onDestroy() {
        stopProjection()
        analysisExecutor.shutdownNow()
        if (captureThread.isAlive) {
            captureThread.quitSafely()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QA Capture")
            .setContentText("Capturing frames for analysis")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QA Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Foreground capture for QA analysis."
            channel.enableLights(false)
            channel.enableVibration(false)
            channel.setSound(null, null)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startProjection() {
        val displayMetrics = resources.displayMetrics
        val width = max(CAPTURE_WIDTH, displayMetrics.widthPixels / 2)
        val height = max(CAPTURE_HEIGHT, displayMetrics.heightPixels / 2)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val frameBitmap = imageToBitmap(image)
            image.close()

            synchronized(frameLock) {
                if (isProcessing) {
                    return@setOnImageAvailableListener
                }
                isProcessing = true
            }

            analysisExecutor.execute {
                try {
                    val result = analyzeFrame(frameBitmap)
                    val intent = Intent(ACTION_ANALYSIS_RESULT).apply {
                        putExtra(EXTRA_ANALYSIS_RESULT, result)
                    }
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failure during analysis", e)
                } finally {
                    synchronized(frameLock) {
                        isProcessing = false
                    }
                }
            }
        }, captureHandler)

        val virtualDisplayMetrics = resources.displayMetrics
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "QA-Capture",
            width,
            height,
            virtualDisplayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler
        )

        if (virtualDisplay == null) {
            Log.e(TAG, "VirtualDisplay creation failed.")
            stopSelf()
        }
    }

    private fun stopProjection() {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val planes = image.planes
        val plane = planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val rowBytes = ByteArray(rowStride)
        val pixels = IntArray(width * height)

        var offset = 0
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            buffer.get(rowBytes, 0, rowStride)

            var x = 0
            var byteIndex = 0
            while (x < width) {
                val r = rowBytes[byteIndex].toInt() and 0xFF
                val g = rowBytes[byteIndex + 1].toInt() and 0xFF
                val b = rowBytes[byteIndex + 2].toInt() and 0xFF
                val a = rowBytes[byteIndex + 3].toInt() and 0xFF
                pixels[offset + x] = Color.argb(a, r, g, b)
                byteIndex += pixelStride
                x += 1
            }
            offset += width
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun analyzeFrame(bitmap: Bitmap): AnalysisResult {
        val resultLogs = mutableListOf<String>()

        val boardDetector = BoardDetector()
        val pieceDetector = PieceDetector()
        val pocketDetector = PocketDetector()
        val physicsSolver = PhysicsSolver()

        val board = boardDetector.detectBoard(bitmap)
        if (board == null) {
            val rawBytes = bitmapToBytes(bitmap)
            resultLogs.add("Board detection failed; raw frame returned.")
            return AnalysisResult(
                frameBytes = rawBytes,
                pieces = emptyList(),
                pockets = emptyList(),
                shot = null,
                logs = resultLogs,
                boardWidth = bitmap.width,
                boardHeight = bitmap.height
            )
        }

        val pieces = pieceDetector.detectPieces(board)
        val pockets = pocketDetector.detectPockets(board)

        val cue = org.opencv.core.Point(board.cols() * 0.22, board.rows() * 0.58)
        val target = if (pieces.isNotEmpty()) pieces.first().let { org.opencv.core.Point(it.centerX, it.centerY) }
        else org.opencv.core.Point(board.cols() * 0.5, board.rows() * 0.5)
        val pocket = if (pockets.isNotEmpty()) pockets.first().let { org.opencv.core.Point(it.centerX, it.centerY) }
        else org.opencv.core.Point(board.cols() * 0.8, board.rows() * 0.8)

        val shot = physicsSolver.solve(
            cue = cue,
            target = target,
            pocket = pocket,
            restitution = 0.82,
            targetRadius = if (pieces.isNotEmpty()) pieces.first().radius else 18.0
        )

        resultLogs.add("Detected pieces: ${pieces.size}")
        resultLogs.add("Detected pockets: ${pockets.size}")
        resultLogs.add("Aim vector: (${shot.aimVectorX}, ${shot.aimVectorY})")
        resultLogs.add("Power: ${shot.power}")

        val processedBitmap = Bitmap.createBitmap(board.cols(), board.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(board, processedBitmap)

        return AnalysisResult(
            frameBytes = bitmapToBytes(processedBitmap),
            pieces = pieces,
            pockets = pockets,
            shot = shot,
            logs = resultLogs,
            boardWidth = board.cols(),
            boardHeight = board.rows()
        )
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
