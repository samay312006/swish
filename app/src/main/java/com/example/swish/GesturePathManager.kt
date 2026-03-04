package com.example.swish

class GesturePathManager {
    // Stores the (x, y) coordinates of the index finger tip over time
    private val frameBuffer: MutableList<Pair<Float, Float>> = mutableListOf()
    private val bUFFERLIMIT = 60 // Roughly 2 seconds of movement at 30fps

    // For smoothing (Moving Average)
    private var lastX = 0f
    private var lastY = 0f
    private val sMOOTHINGFACTOR = 0.2f // 0.0 to 1.0 (Lower = smoother, but more lag)

    fun addPoint(x: Float, y: Float) {
        // Simple Low-Pass Filter (EMA) to reduce jitter
        val smoothedX = if (frameBuffer.isEmpty()) x else lastX + sMOOTHINGFACTOR * (x - lastX)
        val smoothedY = if (frameBuffer.isEmpty()) y else lastY + sMOOTHINGFACTOR * (y - lastY)

        lastX = smoothedX
        lastY = smoothedY

        if (frameBuffer.size >= bUFFERLIMIT) {
            frameBuffer.removeAt(0)
        }
        frameBuffer.add(Pair(smoothedX, smoothedY))
    }

    fun getLastPoint(): Pair<Float, Float>? {
        return frameBuffer.lastOrNull()
    }

    fun isBufferReady(): Boolean {
        // Only classify if we have enough movement data
        return frameBuffer.size > 15
    }

    fun getPoints(): List<Pair<Float, Float>> {
        return frameBuffer.toList()
    }

    fun getNormalizedPath(): List<Pair<Float, Float>> {
        if (frameBuffer.isEmpty()) return emptyList()

        // IMPORTANT: Normalize coordinates so the size of the drawing 
        // doesn't matter (Small 'A' vs Large 'A' should look the same to AI)
        val minX = frameBuffer.minOf { it.first }
        val maxX = frameBuffer.maxOf { it.first }
        val minY = frameBuffer.minOf { it.second }
        val maxY = frameBuffer.maxOf { it.second }

        val rangeX = if (maxX > minX) maxX - minX else 1f
        val rangeY = if (maxY > minY) maxY - minY else 1f

        return frameBuffer.map {
            Pair((it.first - minX) / rangeX, (it.second - minY) / rangeY)
        }
    }

    fun clear() {
        frameBuffer.clear()
        lastX = 0f
        lastY = 0f
    }
}