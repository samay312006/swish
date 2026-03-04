package com.example.swish

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var results: HandLandmarkerResult? = null
    private val pointPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; strokeWidth = 8f }

    private var imageWidth = 1
    private var imageHeight = 1
    private var scaleFactor = 1f
    private var postScaleWidthOffset = 0f
    private var postScaleHeightOffset = 0f

    fun setResults(handResults: HandLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        results = handResults
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        // Calculate scale and offsets to match PreviewView's fill behavior (Center Crop)
        // Since we're in portrait, the "imageWidth" from CameraX is actually the vertical dimension
        // and "imageHeight" is horizontal due to 270 deg rotation.
        
        val viewAspectRatio = width.toFloat() / height
        val imageAspectRatio = imageHeight.toFloat() / imageWidth // Swap due to 270 deg rotation

        if (viewAspectRatio > imageAspectRatio) {
            scaleFactor = width.toFloat() / imageHeight
            postScaleHeightOffset = (scaleFactor * imageWidth - height) / 2
            postScaleWidthOffset = 0f
        } else {
            scaleFactor = height.toFloat() / imageWidth
            postScaleWidthOffset = (scaleFactor * imageHeight - width) / 2
            postScaleHeightOffset = 0f
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        results?.let { handResult ->
            for (landmarkList in handResult.landmarks()) {
                for (landmark in landmarkList) {
                    // Map and scale coordinates
                    // x maps to sensor Y, y maps to sensor X (for 270 deg rotation)
                    // We also apply the scaleFactor and subtract the offset for Center Crop
                    
                    val x = (1.0f - landmark.y()) * imageHeight * scaleFactor - postScaleWidthOffset
                    val y = (1.0f - landmark.x()) * imageWidth * scaleFactor - postScaleHeightOffset

                    canvas.drawCircle(x, y, 10f, pointPaint)
                }
            }
        }
    }
}