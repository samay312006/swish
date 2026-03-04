package com.example.swish

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var results: HandLandmarkerResult? = null
    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 8f
    }
    private val pathPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var imageWidth = 1
    private var imageHeight = 1
    private var scaleFactor = 1f
    private var postScaleWidthOffset = 0f
    private var postScaleHeightOffset = 0f
    private var currentPath: List<Pair<Float, Float>> = emptyList()

    fun setResults(handResults: HandLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        results = handResults
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        // Calculate scale and offsets to match PreviewView's fill behavior (Center Crop)
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

    fun updatePath(points: List<Pair<Float, Float>>) {
        currentPath = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 1. Draw Gesture Trail
        if (currentPath.size > 1) {
            val gesturePath = Path()
            val startP = currentPath[0]
            val startX = (1.0f - startP.second) * imageHeight * scaleFactor - postScaleWidthOffset
            val startY = (1.0f - startP.first) * imageWidth * scaleFactor - postScaleHeightOffset
            gesturePath.moveTo(startX, startY)
            
            for (i in 1 until currentPath.size) {
                val p = currentPath[i]
                val x = (1.0f - p.second) * imageHeight * scaleFactor - postScaleWidthOffset
                val y = (1.0f - p.first) * imageWidth * scaleFactor - postScaleHeightOffset
                gesturePath.lineTo(x, y)
            }
            canvas.drawPath(gesturePath, pathPaint)
        }

        // 2. Draw Hand Landmarks
        results?.let { handResult ->
            for (landmarkList in handResult.landmarks()) {
                for (landmark in landmarkList) {
                    val x = (1.0f - landmark.y()) * imageHeight * scaleFactor - postScaleWidthOffset
                    val y = (1.0f - landmark.x()) * imageWidth * scaleFactor - postScaleHeightOffset
                    canvas.drawCircle(x, y, 10f, pointPaint)
                }
            }
        }
    }
}