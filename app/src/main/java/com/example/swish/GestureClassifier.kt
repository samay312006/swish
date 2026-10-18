package com.example.swish

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class GestureClassifier(context: Context) {
    private var interpreter: Interpreter? = null

    private val MAX_POINTS = 64
    private val labels = listOf("heart", "M", "N", "W", "circle") // EXACTLY MATCH COLAB

    init {
        try {
            val modelBuffer = loadModelFile(context, "gesture_model.tflite")
            val options = Interpreter.Options().apply { numThreads = 2 }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            Log.e("GestureClassifier", "Model not found", e)
        }
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // --- NEW: Spatial Interpolation Algorithm ---
    // Stretches a variable-length gesture to exactly 64 evenly spaced points
    private fun resamplePath(path: List<Pair<Float, Float>>, targetPoints: Int): List<Pair<Float, Float>> {
        if (path.isEmpty()) return emptyList()
        if (path.size == 1) return List(targetPoints) { path.first() }

        var totalLength = 0f
        val distances = FloatArray(path.size)
        distances[0] = 0f

        // Calculate the total distance of the drawn line
        for (i in 1 until path.size) {
            val dx = path[i].first - path[i - 1].first
            val dy = path[i].second - path[i - 1].second
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            totalLength += dist
            distances[i] = totalLength
        }

        val resampled = mutableListOf<Pair<Float, Float>>()
        resampled.add(path.first())

        val interval = totalLength / (targetPoints - 1)
        var currentDist = interval
        var j = 1

        // Interpolate exactly 62 points in the middle
        for (i in 1 until targetPoints - 1) {
            while (j < path.size && distances[j] < currentDist) { j++ }
            if (j >= path.size) {
                resampled.add(path.last())
                continue
            }

            val prevDist = distances[j - 1]
            val nextDist = distances[j]
            val fraction = if (nextDist == prevDist) 0f else (currentDist - prevDist) / (nextDist - prevDist)

            val newX = path[j - 1].first + fraction * (path[j].first - path[j - 1].first)
            val newY = path[j - 1].second + fraction * (path[j].second - path[j - 1].second)
            resampled.add(Pair(newX, newY))

            currentDist += interval
        }

        resampled.add(path.last())
        return resampled
    }

    fun classify(path: List<Pair<Float, Float>>): String {
        if (path.isEmpty() || interpreter == null) return "Unknown"

        // 1. Resample to exactly 64 points
        val resampledPath = resamplePath(path, MAX_POINTS)

        // 2. Flatten into 128 floats
        val inputArray = FloatArray(MAX_POINTS * 2)
        for (i in 0 until MAX_POINTS) {
            inputArray[i * 2] = resampledPath[i].first
            inputArray[(i * 2) + 1] = resampledPath[i].second
        }

        // 3. Prepare Output
        val outputArray = Array(1) { FloatArray(labels.size) }

        // 4. Run AI
        try {
            interpreter?.run(inputArray, outputArray)
        } catch (e: Exception) {
            Log.e("GestureClassifier", "Crash during inference", e)
            return "Unknown"
        }

        // 5. Read Probabilities
        val probabilities = outputArray[0]
        var maxIndex = 0
        var maxConfidence = probabilities[0]

        // DEBUG: Print exactly what the AI thinks for EVERY shape
        val debugLog = StringBuilder("AI Guesses: ")
        for (i in probabilities.indices) {
            debugLog.append("${labels[i]}=${(probabilities[i] * 100).toInt()}%  ")
            if (probabilities[i] > maxConfidence) {
                maxConfidence = probabilities[i]
                maxIndex = i
            }
        }
        Log.d("GestureClassifier", debugLog.toString())
        Log.d("GestureClassifier", "WINNER: ${labels[maxIndex]} (${(maxConfidence * 100).toInt()}%)")

        // Lowered threshold to 60% temporarily for easier testing
        return if (maxConfidence > 0.60f) labels[maxIndex] else "Unknown"
    }

    fun close() {
        interpreter?.close()
    }
}