package com.musicvisualizer.android.visualizers

import android.graphics.Color
import com.musicvisualizer.android.audio.AudioEvent
import com.musicvisualizer.android.audio.AudioEventListener
import com.musicvisualizer.android.audio.AudioAnalyzer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val MAX_BUBBLES = 10
private const val NUM_BUBBLES = 6
private const val FRAME_TIME_NS = 16_000_000L // 16ms in nanoseconds

data class BubbleConfig(
    val saturation: Float = 0.3f,
    val baseRadius: Float = 0.07f,
    val radiusVariation: Float = 0.02f,
    val baseOrbitRadius: Float = 0.05f,
    val maxOrbitRadius: Float = 0.50f,
    val orbitSpeed: Float = 0.01f,
    val angularVelocityAmplitude: Float = 0.01f,
    val orbitDecayRate: Float = 0.025f, // Balanced between old and new
    val beatSmoothingRate: Float = 0.25f, // Reduced from 0.4f for less jitter
    val lightPosition: Pair<Float, Float> = Pair(0.2f, 0.8f),
    val instantReactionStrength: Float = 0.05f, // Reduced from 0.3f
    val beatThreshold: Float = 0.15f // New: higher threshold for instant reactions
)

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
    var lastBeatIntensity: Float = 0f // Track previous frame for instant reactions
) {    
    fun update(time: Float, audioEvent: AudioEvent?) {
        val angularVelocity = config.orbitSpeed + config.angularVelocityAmplitude * (sin(time + seed * 5.0f) * 0.5f + 0.5f)
        color = getColor(time, audioEvent)
        angle += if (clockwiseSpin) angularVelocity else -angularVelocity
        radius = config.baseRadius + config.radiusVariation * sin(time + seed * 5.0f)

        audioEvent?.let { audio ->
            // 1. Instant reaction only to significant beat spikes
            val beatDelta = audio.beatIntensity - lastBeatIntensity
            val instantReaction = if (beatDelta > config.beatThreshold) {
                beatDelta * config.instantReactionStrength
            } else 0f
            
            // 2. Moderate smoothing with clamping to prevent over-reaction
            val targetSmoothed = smoothedBeatIntensity * (1f - config.beatSmoothingRate) + 
                               audio.beatIntensity * config.beatSmoothingRate
            smoothedBeatIntensity = targetSmoothed.coerceIn(0f, 1f)
            
            // 3. Combine with limited instant response
            val combinedIntensity = (smoothedBeatIntensity + instantReaction).coerceIn(0f, 1f)
            
            val reactivity = 1f - (radius - config.baseRadius) / config.radiusVariation * 3f
            targetOrbitRadius = config.baseOrbitRadius + combinedIntensity * reactivity * 
                              (config.maxOrbitRadius - config.baseOrbitRadius)
            
            lastBeatIntensity = audio.beatIntensity
        }
        
        // Moderate orbit radius interpolation
        val lerpSpeed = config.orbitDecayRate * 1.5f // Reduced from 2.0f multiplier
        orbitRadius = orbitRadius * (1f - lerpSpeed) + targetOrbitRadius * lerpSpeed
        if (targetOrbitRadius <= config.baseOrbitRadius) targetOrbitRadius = config.baseOrbitRadius
        position = Pair(
            0.5f + orbitRadius * cos(angle),
            0.5f + orbitRadius * sin(angle)
        )
    }
    
    fun getColor(time: Float, audioEvent: AudioEvent?): FloatArray {
        // Gentle color brightness response to audio
        val baseBrightness = 1.0f
        val audioBrightness = audioEvent?.let { audio ->
            baseBrightness + (audio.beatIntensity * 0.1f) // Reduced from 0.2f for subtlety
        } ?: baseBrightness
        
        val hue = cos(time / 8.0f + seed * 5.0f) * 180f + 180f
        val saturation = (config.saturation + 0.3f * sin(time + seed * 5.0f)) % 1.0f
        val value = (audioBrightness).coerceIn(0f, 1f)

        val hsv = floatArrayOf(hue, saturation, value)
        val argbColor = Color.HSVToColor(hsv)
        val red = Color.red(argbColor) / 255f
        val green = Color.green(argbColor) / 255f
        val blue = Color.blue(argbColor) / 255f
        return floatArrayOf(red, green, blue)
    }
}

/**
 * Functional class that handles bubble updating, calculations and state management
 */
class BubbleSystem(
    private val config: BubbleConfig = BubbleConfig(),
    private val audioAnalyzer: AudioAnalyzer
) : AudioEventListener {
    private val bubbles = List(NUM_BUBBLES) { Bubble(config) }
    private val startTime = System.nanoTime()
    private var cachedTime = 0f
    private var lastTimeUpdate = 0L
    private var currentAudioEvent: AudioEvent? = null

    // Pre-allocated arrays for performance
    val bubbleColors = FloatArray(MAX_BUBBLES * 3)
    val bubbleRadii = FloatArray(MAX_BUBBLES)
    val bubblePositions = FloatArray(MAX_BUBBLES * 2)
    val bubbleSeeds = FloatArray(MAX_BUBBLES)

    init {
        audioAnalyzer.addListener(this)
    }

    override fun onAudioEvent(event: AudioEvent) {
        currentAudioEvent = event
    }

    fun update(): Float {
        val now = System.nanoTime()
        
        // Reduce time caching delay - update every 8ms instead of 16ms for smoother response
        if (now - lastTimeUpdate > FRAME_TIME_NS / 2) {
            cachedTime = ((now - startTime) / 1_000_000_000f) % (16f * PI.toFloat())
            lastTimeUpdate = now
        }

        // Update bubbles with audio data and populate arrays
        bubbles.forEachIndexed { i, bubble ->
            bubble.update(cachedTime, currentAudioEvent)
            
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

    fun getBubbleCount() = NUM_BUBBLES

    fun getCurrentAudioEvent(): AudioEvent? = currentAudioEvent

    fun printDebugInfo(time: Float, width: Float, height: Float) {
        fun formatBubbleData(array: FloatArray, components: Int, formatter: (Int) -> String): String {
            return (0 until NUM_BUBBLES).joinToString(" ", transform = formatter)
        }
        
        println("=== BubbleVisualizer Uniforms ===")
        println("Resolution: $width, $height | Time: $time | Num Bubbles: $NUM_BUBBLES")
        currentAudioEvent?.let { audio ->
            println("Audio: Beat:${String.format("%.2f", audio.beatIntensity)} Bass:${String.format("%.2f", audio.bass)} Mid:${String.format("%.2f", audio.mid)} Treble:${String.format("%.2f", audio.treble)} Vol:${String.format("%.2f", audio.volume)}")
        }
        println("Light Position: (${String.format("%.3f", config.lightPosition.first)}, ${String.format("%.3f", config.lightPosition.second)})")
        println("Positions: ${formatBubbleData(bubblePositions, 2) { i -> 
            "(%.3f, %.3f)".format(bubblePositions[i * 2], bubblePositions[i * 2 + 1]) 
        }}")
        println("Colors: ${formatBubbleData(bubbleColors, 3) { i -> 
            "(%.3f, %.3f, %.3f)".format(bubbleColors[i * 3], bubbleColors[i * 3 + 1], bubbleColors[i * 3 + 2]) 
        }}")        
        println("Radii: ${bubbleRadii.joinToString(" ") { "%.3f".format(it) }}")
        println("Seeds: ${bubbleSeeds.joinToString(" ") { "%.3f".format(it) }}")
        println("================================")
    }

    fun printTemp(time: Float, width: Float, height: Float) {
        currentAudioEvent?.let { audio ->
            println("Audio: Beat:${String.format("%.2f", audio.beatIntensity)} Bass:${String.format("%.2f", audio.bass)} Mid:${String.format("%.2f", audio.mid)} Treble:${String.format("%.2f", audio.treble)} Vol:${String.format("%.2f", audio.volume)}")
        }
    }
}