package com.example.swish

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.random.Random
import java.io.File
import java.io.FileWriter
import java.io.IOException
import android.widget.Toast
import android.util.Log

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val currentTrainingLabel = "M"

    // --- 1. PARTICLE SYSTEM SETUP ---
    // This tracks the individual sparks flying off the brush
    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float, // Velocity (speed) in X direction
        var vy: Float, // Velocity (speed) in Y direction
        var life: Float // Ranges from 1.0 (alive) down to 0.0 (dead)
    )

    private val particles = mutableListOf<Particle>()

    private val particlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        // Adds a bright halo around the sparks
        setShadowLayer(8f, 0f, 0f, Color.parseColor("#FFFFFF"))
    }
    // --------------------------------

    private var results: HandLandmarkerResult? = null

    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    // The glowing neon green base line
    private val neonGreenPaint = Paint().apply {
        color = Color.parseColor("#39FF14")
        isAntiAlias = true
        strokeWidth = 20f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(35f, 0f, 0f, Color.parseColor("#39FF14"))
    }

    private var imageWidth = 1
    private var imageHeight = 1
    private var scaleFactor = 1f
    private var postScaleWidthOffset = 0f
    private var postScaleHeightOffset = 0f
    private var currentPath: List<Pair<Float, Float>> = emptyList()

    init {
        // Allows the neon glowing shadows to render
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setResults(handResults: HandLandmarkerResult, imageWidth: Int, imageHeight: Int) {
        results = handResults
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        val viewAspectRatio = width.toFloat() / height
        val imageAspectRatio = imageHeight.toFloat() / imageWidth

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

        // --- 2. SPAWN PARTICLES AT THE TIP OF THE PEN ---
        if (points.isNotEmpty()) {
            val tip = points.last()
            val tipX = (1.0f - tip.second) * imageHeight * scaleFactor - postScaleWidthOffset
            val tipY = (1.0f - tip.first) * imageWidth * scaleFactor - postScaleHeightOffset

            // Spawn 5 sparks every time the finger moves
            for (i in 0..4) {
                val vx = (Random.nextFloat() - 0.5f) * 35f // Explosive X speed
                val vy = (Random.nextFloat() - 0.5f) * 35f // Explosive Y speed
                particles.add(Particle(tipX, tipY, vx, vy, 1.0f))
            }
        }
        // ------------------------------------------------

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Gesture Trail (Neon Green Base)
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

            canvas.drawPath(gesturePath, neonGreenPaint)
        }

        // --- 3. ANIMATE AND DRAW FLYING PARTICLES ---
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()

            // Move particle based on its speed
            p.x += p.vx
            p.y += p.vy

            // Add "gravity" so sparks slowly drag downward
            p.vy += 1.5f

            // Fade out
            p.life -= 0.05f

            if (p.life <= 0f) {
                iterator.remove() // Delete particle when it dies
            } else {
                // Draw particle (it shrinks and fades as life goes down)
                particlePaint.alpha = (p.life * 255).toInt()
                val radius = 2f + (p.life * 6f)
                canvas.drawCircle(p.x, p.y, radius, particlePaint)
            }
        }

        // CRUCIAL: If particles are still alive, tell Android to redraw immediately
        // This keeps the sparks flying even if your finger completely stops moving!
        if (particles.isNotEmpty()) {
            invalidate()
        }
        // ---------------------------------------------

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

    // Call this from your main service/activity when the user makes a FIST
    fun triggerSaveGesture() {
        if (currentPath.isNotEmpty()) {
            saveGestureToCSV(currentTrainingLabel, currentPath)
        }
    }

    private fun saveGestureToCSV(label: String, points: List<Pair<Float, Float>>) {
        if (points.size < 5) return // Ignore accidental micro-gestures

        // Saves to App-Specific storage (No permissions needed)
        val directory = context.getExternalFilesDir(null)
        val file = File(directory, "swish_training_data.csv")

        try {
            // true means we APPEND to the file instead of overwriting it
            val writer = FileWriter(file, true)

            val sb = StringBuilder()
            sb.append(label) // First column is the label (e.g., "circle")

            // Append all X and Y coordinates consecutively
            for (p in points) {
                sb.append(",${p.first},${p.second}")
            }
            sb.append("\n") // New line for the next gesture

            writer.append(sb.toString())
            writer.flush()
            writer.close()

            Log.d("SwishData", "Saved $label: ${points.size} points to ${file.absolutePath}")
            Toast.makeText(context, "Saved: $label", Toast.LENGTH_SHORT).show()

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save data", Toast.LENGTH_SHORT).show()
        }
    }
}