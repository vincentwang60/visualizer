package com.musicvisualizer.android.util

import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Performance monitoring utility for adaptive optimization
 */
class PerformanceMonitor {
    private var frameCount = 0L
    private var lastFpsCheckTime = 0L
    private var currentFps = 60f
    private var targetFps = 30f
    private var performanceMode = false
    private var thermalThrottling = false
    
    // Performance thresholds
    private val minFps = 20f
    private val maxFps = 60f
    private val lowPerformanceThreshold = 25f
    private val highPerformanceThreshold = 35f
    
    // Frame time tracking
    private val frameTimes = mutableListOf<Long>()
    private val maxFrameTimeHistory = 60
    
    companion object {
        private const val TAG = "PerformanceMonitor"
    }
    
    fun onFrameRendered() {
        val now = System.nanoTime()
        frameCount++
        
        // Track frame times for analysis
        if (frameTimes.isNotEmpty()) {
            val frameTime = now - frameTimes.last()
            frameTimes.add(frameTime)
            if (frameTimes.size > maxFrameTimeHistory) {
                frameTimes.removeAt(0)
            }
        } else {
            frameTimes.add(now)
        }
        
        // Update FPS every second
        if (now - lastFpsCheckTime > 1_000_000_000L) {
            currentFps = frameCount * 1_000_000_000f / (now - lastFpsCheckTime)
            frameCount = 0
            lastFpsCheckTime = now
            
            updatePerformanceMode()
            logPerformanceStats()
        }
    }
    
    private fun updatePerformanceMode() {
        val previousMode = performanceMode
        
        when {
            currentFps < lowPerformanceThreshold -> {
                performanceMode = true
                targetFps = min(targetFps * 0.9f, 25f)
            }
            currentFps > highPerformanceThreshold && !thermalThrottling -> {
                performanceMode = false
                targetFps = min(targetFps * 1.05f, maxFps)
            }
        }
        
        if (performanceMode != previousMode) {
            Log.d(TAG, "Performance mode changed: $performanceMode (FPS: $currentFps)")
        }
    }
    
    private fun logPerformanceStats() {
        if (currentFps < 25f || performanceMode) {
            val avgFrameTime = frameTimes.takeLast(30).average()
            Log.w(TAG, "Performance warning - FPS: ${String.format("%.1f", currentFps)}, " +
                      "Avg frame time: ${String.format("%.1f", avgFrameTime / 1_000_000)}ms, " +
                      "Performance mode: $performanceMode")
        }
    }
    
    fun getTargetFrameTime(): Long {
        return (1_000_000_000L / targetFps).toLong()
    }
    
    fun isPerformanceMode(): Boolean = performanceMode
    
    fun getCurrentFps(): Float = currentFps
    
    fun getAverageFrameTime(): Double {
        return if (frameTimes.isNotEmpty()) {
            frameTimes.takeLast(30).average() / 1_000_000 // Convert to milliseconds
        } else 0.0
    }
    
    fun setThermalThrottling(enabled: Boolean) {
        thermalThrottling = enabled
        if (enabled) {
            targetFps = min(targetFps, 25f)
            performanceMode = true
            Log.w(TAG, "Thermal throttling enabled")
        }
    }
    
    fun reset() {
        frameCount = 0L
        lastFpsCheckTime = 0L
        currentFps = 60f
        targetFps = 30f
        performanceMode = false
        frameTimes.clear()
    }
}