package com.example.swish

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Log
import android.util.Range
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
class MainActivity : AppCompatActivity() {

    private var smoothedX = 0f
    private var smoothedY = 0f
    private val emaAlpha = 0.4f

    private lateinit var gestureClassifier: GestureClassifier

    private var pointingFrameCount = 0
    private val pointingStartThreshold = 5
    private var notPointingFrameCount = 0
    private val pointingStopThreshold = 10
    
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handTracker: HandTracker
    private lateinit var overlayView: OverlayView
    private var currentImageWidth = 1
    private var currentImageHeight = 1
    
    private val pathManager = GesturePathManager()
    private var isDrawing = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required.", Toast.LENGTH_LONG).show()
        }
    }

    fun startFloatingService() {
        // 1. Check if the user has granted permission to draw over other apps
        if (!Settings.canDrawOverlays(this)) {
            // If not, send them to the settings screen to turn it on
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
            Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
        } else {
            // 2. If permission is granted, start the service as a Foreground Service!
            val serviceIntent = Intent(this, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Overlay Started! Go to your Home Screen.", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
        } else {
            // Permission granted! Start your overlay service.
        }
    }

    private fun openApp(packageName: String) {
        // Asks the Android OS if the app is installed and gets the intent to launch it
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "App not installed on this device!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeGestureAction(prediction: String) {
        when (prediction) {
            "circle" -> {
                // Opens Google in the browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                startActivity(intent)
            }
            "heart" -> {
                // Opens the dialer with the user's heart contact pre-filled.
                // Note: We use ACTION_DIAL instead of ACTION_CALL so you don't
                // have to deal with dangerous call permissions. All you do is hit send!
                val number = HeartContact.get(this)
                if (number == null) {
                    // First heart ever: ask who to call, then open the dialer.
                    val intent = Intent(this, HeartContactActivity::class.java)
                    intent.putExtra(HeartContactActivity.EXTRA_DIAL_AFTER_SAVE, true)
                    startActivity(intent)
                } else {
                    val intent = Intent(Intent.ACTION_DIAL)
                    intent.data = Uri.parse("tel:${Uri.encode(number)}")
                    startActivity(intent)
                }
            }
            "N" -> {
                openApp("com.netflix.mediaclient")
            }
            "M" -> {
                openApp("com.spotify.music")
            }
            "W" -> {
                openApp("com.whatsapp")
            }
            "Unknown" -> {
                // Do nothing if the AI is confused
            }
            else -> {
                Toast.makeText(this, "No action assigned for: $prediction", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        gestureClassifier = GestureClassifier(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayView = findViewById(R.id.overlayView)

        handTracker = HandTracker(this, object : HandTracker.HandTrackerListener {
            override fun onHandResults(result: HandLandmarkerResult) {
                val landmarksList = result.landmarks()

                if (landmarksList.isNotEmpty()) {
                    var activeHand = landmarksList[0]
                    var detectedState = "IDLE" // Can be IDLE, POINTING, or FIST

                    // 1. ROBUST FOLD DETECTION (Distance-based)
                    for (hand in landmarksList) {
                        val wrist = hand[0]

                        // A finger is extended if the tip is further from the wrist than the PIP joint
                        val isIndexExtended = getDistance(wrist, hand[8]) > getDistance(wrist, hand[6])
                        val isMiddleFolded = getDistance(wrist, hand[12]) < getDistance(wrist, hand[10])
                        val isRingFolded = getDistance(wrist, hand[16]) < getDistance(wrist, hand[14])
                        val isPinkyFolded = getDistance(wrist, hand[20]) < getDistance(wrist, hand[18])

                        if (isIndexExtended && isMiddleFolded && isRingFolded && isPinkyFolded) {
                            detectedState = "POINTING"
                            activeHand = hand
                            break
                        } else if (!isIndexExtended && isMiddleFolded && isRingFolded && isPinkyFolded) {
                            detectedState = "FIST"
                            activeHand = hand
                            break
                        }
                    }

                    val indexTip = activeHand[8]

                    // 2. Apply EMA Smoothing (Only needed when pointing)
                    if (detectedState == "POINTING") {
                        if (smoothedX == 0f && smoothedY == 0f) {
                            smoothedX = indexTip.x()
                            smoothedY = indexTip.y()
                        } else {
                            smoothedX = (emaAlpha * indexTip.x()) + ((1 - emaAlpha) * smoothedX)
                            smoothedY = (emaAlpha * indexTip.y()) + ((1 - emaAlpha) * smoothedY)
                        }
                    }

                    // 3. EXPLICIT STATE MACHINE
                    when (detectedState) {
                        "POINTING" -> {
                            pointingFrameCount++
                            if (pointingFrameCount >= pointingStartThreshold) {
                                if (!isDrawing) {
                                    isDrawing = true
                                    pathManager.clear()
                                }
                                val lastPoint = pathManager.getLastPoint()
                                val distanceMoved = if (lastPoint != null) {
                                    hypot((smoothedX - lastPoint.first), (smoothedY - lastPoint.second))
                                } else { 1.0f }

                                if (distanceMoved > 0.005) {
                                    pathManager.addPoint(smoothedX, smoothedY)
                                }
                            }
                        }
                        "FIST" -> {
                            // ONLY capture when the user explicitly makes a fist
                            if (isDrawing) {
                                isDrawing = false
                                processFinishedGesture()

                                // Reset variables for the next drawing
                                smoothedX = 0f
                                smoothedY = 0f
                                pointingFrameCount = 0
                            }
                        }
                        "IDLE" -> {
                            // Open palm or weird gesture. Just pause, don't capture, don't draw.
                            pointingFrameCount = 0
                            // We no longer use notPointingFrameCount because FIST handles the stop!
                        }
                    }

                    // 4. Update UI
                    runOnUiThread {
                        overlayView.setResults(result, currentImageWidth, currentImageHeight)
                        overlayView.updatePath(pathManager.getPoints())
                    }
                } else {
                    // No hands detected in frame
                    runOnUiThread {
                        overlayView.setResults(result, currentImageWidth, currentImageHeight)
                        overlayView.updatePath(emptyList()) // Hide trail if hand leaves screen
                    }
                }
            }
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        startFloatingService()
    }

    private fun processFinishedGesture() {
        if (pathManager.isBufferReady()) {

            // 1. Get the normalized path (This MUST return a List or FloatArray based on your classifier)
            val normalizedData = pathManager.getNormalizedPath()

            // 2. Pass it to your AI classifier!
            val prediction = gestureClassifier.classify(normalizedData)

            Log.d("Gesture", "Gesture Captured! AI Prediction: $prediction")

            // 3. Show the prediction and FIRE THE ACTION!
            runOnUiThread {
                Toast.makeText(this, "Recognized: $prediction", Toast.LENGTH_SHORT).show()

                // This triggers the intent to open WhatsApp, Spotify, etc.
                executeGestureAction(prediction)
            }

        } else {
            runOnUiThread {
                Toast.makeText(this, "Gesture too short to recognize", Toast.LENGTH_SHORT).show()
            }
        }

        // Clear the canvas
        pathManager.clear()
        runOnUiThread {
            overlayView.updatePath(emptyList())
        }

        // Close the whiteboard overlay so the user can see the app they just opened!
        finish()
    }



    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
            }

            val analyzerBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

            val extender = Camera2Interop.Extender(analyzerBuilder)
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(30, 60)
            )

            val imageAnalyzer = analyzerBuilder.build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalyzer)
            } catch(exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        currentImageWidth = imageProxy.width
        currentImageHeight = imageProxy.height
        handTracker.detectAsync(imageProxy)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        gestureClassifier.close()
    }

    // Helper function to calculate the distance between two landmarks
    private fun getDistance(lm1: NormalizedLandmark, lm2: NormalizedLandmark): Float {
        return hypot((lm1.x() - lm2.x()), (lm1.y() - lm2.y()))
    }
}