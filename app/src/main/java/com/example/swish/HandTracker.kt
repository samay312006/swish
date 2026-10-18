package com.example.swish

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandTracker(context: Context, private val listener: HandTrackerListener) {
    private var handLandmarker: HandLandmarker? = null

    init {
        val baseOptions = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinHandDetectionConfidence(0.5f) // Slightly lower for faster initial lock-on
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(1) // 🔥 OPTIMIZATION 1: Only track 1 hand to double the speed
            .setResultListener { result, _ ->
                listener.onHandResults(result)
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detectAsync(imageProxy: ImageProxy) {
        val bitmapBuffer = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        
        imageProxy.use { 
            val buffer = it.planes[0].buffer
            buffer.rewind()
            bitmapBuffer.copyPixelsFromBuffer(buffer)
        }

        val mpImage = BitmapImageBuilder(bitmapBuffer).build()
        val frameTime = SystemClock.uptimeMillis()

        // Provide rotation information to MediaPipe
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDegrees)
            .build()
            
        handLandmarker?.detectAsync(mpImage, imageProcessingOptions, frameTime)
    }

    interface HandTrackerListener {
        fun onHandResults(result: HandLandmarkerResult)
    }
}