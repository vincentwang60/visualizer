package com.musicvisualizer.android.util

/**
 * Centralized performance configuration for the music visualizer
 */
object PerformanceConfig {
    // Frame rate settings
    const val TARGET_FPS = 30f
    const val MIN_FPS = 20f
    const val MAX_FPS = 60f
    const val LOW_PERFORMANCE_THRESHOLD = 25f
    const val HIGH_PERFORMANCE_THRESHOLD = 35f
    
    // Audio processing settings
    const val FFT_CAPTURE_SIZE = 512
    const val AUDIO_PROCESSING_INTERVAL_MS = 50L
    const val MAX_ENERGY_HISTORY_SIZE = 8
    
    // Rendering settings
    const val MAX_BUBBLES = 8
    const val NUM_BUBBLES = 5
    const val SHADER_ITERATIONS_PERFORMANCE = 3
    const val SHADER_ITERATIONS_NORMAL = 4
    
    // Thermal management
    const val THERMAL_THROTTLE_FPS = 25f
    const val THERMAL_THROTTLE_INTERVAL_MS = 100L
    
    // Memory management
    const val MAX_FRAME_TIME_HISTORY = 60
    const val COLOR_CACHE_SIZE = 100
    
    // Debug settings
    const val ENABLE_PERFORMANCE_LOGGING = false
    const val LOG_INTERVAL_SECONDS = 5
    
    /**
     * Get optimized settings based on device performance
     */
    fun getOptimizedSettings(devicePerformance: DevicePerformance): OptimizedSettings {
        return when (devicePerformance) {
            DevicePerformance.LOW -> OptimizedSettings(
                targetFps = 25f,
                maxBubbles = 6,
                numBubbles = 4,
                shaderIterations = 2,
                audioProcessingInterval = 75L,
                fftCaptureSize = 256
            )
            DevicePerformance.MEDIUM -> OptimizedSettings(
                targetFps = 30f,
                maxBubbles = 8,
                numBubbles = 5,
                shaderIterations = 3,
                audioProcessingInterval = 50L,
                fftCaptureSize = 512
            )
            DevicePerformance.HIGH -> OptimizedSettings(
                targetFps = 45f,
                maxBubbles = 10,
                numBubbles = 6,
                shaderIterations = 4,
                audioProcessingInterval = 30L,
                fftCaptureSize = 1024
            )
        }
    }
    
    /**
     * Detect device performance based on available information
     */
    fun detectDevicePerformance(): DevicePerformance {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024) // MB
        
        return when {
            cores <= 4 && maxMemory <= 2048 -> DevicePerformance.LOW
            cores <= 6 && maxMemory <= 4096 -> DevicePerformance.MEDIUM
            else -> DevicePerformance.HIGH
        }
    }
}

enum class DevicePerformance {
    LOW, MEDIUM, HIGH
}

data class OptimizedSettings(
    val targetFps: Float,
    val maxBubbles: Int,
    val numBubbles: Int,
    val shaderIterations: Int,
    val audioProcessingInterval: Long,
    val fftCaptureSize: Int
)