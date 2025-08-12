package com.musicvisualizer.android.visualizers

import android.graphics.Color
import com.musicvisualizer.android.audio.AudioEvent
import com.musicvisualizer.android.audio.AudioEventListener
import com.musicvisualizer.android.audio.AudioAnalyzer
import com.musicvisualizer.android.util.PerformanceConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Use centralized performance configuration
private val devicePerformance = PerformanceConfig.detectDevicePerformance()
private val optimizedSettings = PerformanceConfig.getOptimizedSettings(devicePerformance)

data class BubbleConfig(
    val saturation: Float = 0.3f,
    val baseRadius: Float = 0.07f,
    val radiusVariation: Float = 0.02f,
    val baseOrbitRadius: Float = 0.05f,
    val maxOrbitRadius: Float = 0.50f,
    val orbitSpeed: Float = 0.01f,
    val angularVelocityAmplitude: Float = 0.01f,
    val orbitDecayRate: Float = 0.025f,
    val beatSmoothingRate: Float = 0.25f,
    val lightPosition: Pair<Float, Float> = Pair(0.2f, 0.8f),
    val instantReactionStrength: Float = 0.05f,
    val beatThreshold: Float = 0.15f
)

// Optimized bubble class with reduced calculations
private data class Bubble(
    val config: BubbleConfig,
    var color: FloatArray = FloatArray(3) { config.saturation + Random.nextFloat() * (1.0f - config.saturation) },
    var radius: Float = config.baseRadius,
    var angle: Float = Random.nextFloat() * 2f * PI.toFloat(),
    val seed: Float = Random.nextFloat(),
    private val clockwiseSpin: Boolean = Random.nextBoolean(),
    var position: Pair<Float, Float> = Pair(0f, 0f),
    var orbitRadius: Float = config.maxOrbitRadius / 4f,
    var targetOrbitRadius: Float = config.maxOrbitRadius / 4f,
    var smoothedBeatIntensity: Float = 0f,
    var lastBeatIntensity: Float = 0f,
    // Cached values for performance
    private var lastUpdateTime: Float = 0f,
    private var cachedColor: FloatArray = FloatArray(3)
) {    
    fun update(time: Float, audioEvent: AudioEvent?, performanceMode: Boolean) {
        // Skip updates in performance mode if not enough time has passed
        val updateInterval = if (performanceMode) 0.05f else 0.033f // 20fps vs 30fps
        if (performanceMode && time - lastUpdateTime < updateInterval) return
        lastUpdateTime = time
        
        val angularVelocity = config.orbitSpeed + config.angularVelocityAmplitude * (sin(time + seed * 5.0f) * 0.5f + 0.5f)
        
        // Only update color if needed (reduced frequency in performance mode)
        val colorUpdateInterval = if (performanceMode) 0.15f else 0.1f
        if (!performanceMode || time % colorUpdateInterval < updateInterval) {
            color = getColor(time, audioEvent)
        }
        
        angle += if (clockwiseSpin) angularVelocity else -angularVelocity
        radius = config.baseRadius + config.radiusVariation * sin(time + seed * 5.0f)

        audioEvent?.let { audio ->
            // Simplified beat detection for performance
            val beatDelta = audio.beatIntensity - lastBeatIntensity
            val instantReaction = if (beatDelta > config.beatThreshold) {
                beatDelta * config.instantReactionStrength
            } else 0f
            
            val targetSmoothed = smoothedBeatIntensity * (1f - config.beatSmoothingRate) + 
                               audio.beatIntensity * config.beatSmoothingRate
            smoothedBeatIntensity = targetSmoothed.coerceIn(0f, 1f)
            
            val combinedIntensity = (smoothedBeatIntensity + instantReaction).coerceIn(0f, 1f)
            
            val reactivity = 1f - (radius - config.baseRadius) / config.radiusVariation * 3f
            targetOrbitRadius = config.baseOrbitRadius + combinedIntensity * reactivity * 
                              (config.maxOrbitRadius - config.baseOrbitRadius)
            
            lastBeatIntensity = audio.beatIntensity
        }
        
        // Simplified orbit radius interpolation
        val lerpSpeed = config.orbitDecayRate * (if (performanceMode) 1.0f else 1.5f)
        orbitRadius = orbitRadius * (1f - lerpSpeed) + targetOrbitRadius * lerpSpeed
        if (targetOrbitRadius <= config.baseOrbitRadius) targetOrbitRadius = config.baseOrbitRadius
        
        position = Pair(
            0.5f + orbitRadius * cos(angle),
            0.5f + orbitRadius * sin(angle)
        )
    }
    
    fun getColor(time: Float, audioEvent: AudioEvent?): FloatArray {
        // Cache color calculations to avoid repeated HSV conversions
        val cacheKey = ((time * 8).toInt() + (audioEvent?.beatIntensity?.let { (it * 8).toInt() } ?: 0)) % PerformanceConfig.COLOR_CACHE_SIZE
        if (cachedColor[0] != 0f && cacheKey == lastCacheKey) {
            return cachedColor
        }
        lastCacheKey = cacheKey
        
        val baseBrightness = 1.0f
        val audioBrightness = audioEvent?.let { audio ->
            baseBrightness + (audio.beatIntensity * 0.1f)
        } ?: baseBrightness
        
        val hue = cos(time / 8.0f + seed * 5.0f) * 180f + 180f
        val saturation = (config.saturation + 0.3f * sin(time + seed * 5.0f)) % 1.0f
        val value = (audioBrightness).coerceIn(0f, 1f)

        val hsv = floatArrayOf(hue, saturation, value)
        val argbColor = Color.HSVToColor(hsv)
        cachedColor[0] = Color.red(argbColor) / 255f
        cachedColor[1] = Color.green(argbColor) / 255f
        cachedColor[2] = Color.blue(argbColor) / 255f
        return cachedColor
    }
    
    private var lastCacheKey = -1
}

/**
 * Optimized bubble system with centralized performance configuration
 */
class BubbleSystem(
    private val config: BubbleConfig = BubbleConfig(),
    private val audioAnalyzer: AudioAnalyzer
) : AudioEventListener {
    private val bubbles = List(optimizedSettings.numBubbles) { Bubble(config) }
    private val startTime = System.nanoTime()
    private var cachedTime = 0f
    private var lastTimeUpdate = 0L
    private var currentAudioEvent: AudioEvent? = null
    private var performanceMode = false

    // Pre-allocated arrays for performance - use optimized settings
    val bubbleColors = FloatArray(optimizedSettings.maxBubbles * 3)
    val bubbleRadii = FloatArray(optimizedSettings.maxBubbles)
    val bubblePositions = FloatArray(optimizedSettings.maxBubbles * 2)
    val bubbleSeeds = FloatArray(optimizedSettings.maxBubbles)

    init {
        audioAnalyzer.addListener(this)
    }

    override fun onAudioEvent(event: AudioEvent) {
        currentAudioEvent = event
    }

    fun update(performanceMode: Boolean = false): Float {
        this.performanceMode = performanceMode
        val now = System.nanoTime()
        
        // Reduced time caching frequency in performance mode
        val updateInterval = if (performanceMode) 50_000_000L else 33_333_333L // 20fps vs 30fps
        if (now - lastTimeUpdate > updateInterval) {
            cachedTime = ((now - startTime) / 1_000_000_000f) % (16f * PI.toFloat())
            lastTimeUpdate = now
        }

        // Update bubbles with performance mode flag
        bubbles.forEachIndexed { i, bubble ->
            bubble.update(cachedTime, currentAudioEvent, performanceMode)
            
            // Efficient array copying
            System.arraycopy(bubble.color, 0, bubbleColors, i * 3, 3)
            bubbleRadii[i] = bubble.radius
            bubblePositions[i * 2] = bubble.position.first
            bubblePositions[i * 2 + 1] = bubble.position.second
            bubbleSeeds[i] = bubble.seed
        }

        return cachedTime
    }

    fun cleanup() {
        audioAnalyzer.removeListener(this)
        audioAnalyzer.stop()
    }

    fun getConfig() = config
    fun getBubbleCount() = optimizedSettings.numBubbles
    fun getCurrentAudioEvent(): AudioEvent? = currentAudioEvent
    fun getOptimizedSettings() = optimizedSettings
}