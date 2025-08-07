package com.musicvisualizer.android.visualizers

import android.graphics.Color
import android.util.Log
import com.musicvisualizer.android.audio.AudioEvent
import com.musicvisualizer.android.audio.AudioEventListener
import com.musicvisualizer.android.audio.AudioAnalyzer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val MAX_BUBBLES = 10
private const val NUM_BUBBLES = 8
private const val FRAME_TIME_NS = 16_000_000L // 16ms in nanoseconds

data class BubbleConfig(
    val saturation: Float = 0.3f,
    val baseRadius: Float = 0.07f,
    val radiusVariation: Float = 0.03f,
    val baseOrbitRadius: Float = 0.1f,
    val maxOrbitRadius: Float = 0.40f,
    val orbitSpeed: Float = 0.02f,
    val angularVelocityAmplitude: Float = 0.01f,
    val orbitDecayRate: Float = 0.05f,
    val lightPosition: Pair<Float, Float> = Pair(0.2f, 0.8f),
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
) {    
    fun update(time: Float, audioEvent: AudioEvent?, bubbleIndex: Int) {
        // Calculate base radius with sine variation only
        val sineVariation = config.radiusVariation * sin(time + seed * 5.0f)
        radius = config.baseRadius + sineVariation

        val minRadius = config.baseRadius - config.radiusVariation
        val inverseWeight = 1.0f - 0.5f * (radius - minRadius) / (2 * config.radiusVariation)
        val channelIndex = (inverseWeight * 7f).toInt().coerceIn(0, 7)
        // Log.d("BubbleSystem", "bubbleIndex=$bubbleIndex, channelIndex=$channelIndex, radius=%.4f".format(radius))

        val angularVelocity = inverseWeight * config.orbitSpeed + config.angularVelocityAmplitude * (sin(time + seed * 5.0f) * 0.5f + 0.5f)
        angle += if (clockwiseSpin) angularVelocity else -angularVelocity
        
        color = getColor(time, audioEvent)

        audioEvent?.let { audio ->
            val fftValue = audio.fftTest?.getOrNull(channelIndex) ?: 0f
            
            // Improved response curve for better dynamics like Monstercat
            // Lower threshold for more responsiveness, but with smart scaling
            val lowThreshold = 0.15f   // Start responding earlier
            val midThreshold = 0.4f    // Mid-level response
            val highThreshold = 0.7f   // High-energy response
            
            val adjustedFftValue = when {
                fftValue < lowThreshold -> {
                    // Very quiet: minimal movement, stay near center
                    fftValue * 0.3f
                }
                fftValue < midThreshold -> {
                    // Low-mid: linear response with slight boost
                    lowThreshold * 0.3f + (fftValue - lowThreshold) * 0.8f
                }
                fftValue < highThreshold -> {
                    // Mid-high: enhanced response for good dynamics
                    val baseResponse = lowThreshold * 0.3f + (midThreshold - lowThreshold) * 0.8f
                    val normalizedValue = (fftValue - midThreshold) / (highThreshold - midThreshold)
                    baseResponse + normalizedValue * 0.4f
                }
                else -> {
                    // Peak energy: exponential expansion for dramatic effect
                    val baseResponse = lowThreshold * 0.3f + (midThreshold - lowThreshold) * 0.8f + 0.4f
                    val normalizedValue = (fftValue - highThreshold) / (1f - highThreshold)
                    baseResponse + normalizedValue.pow(1.2f) * (1f - baseResponse)
                }
            }
            
            targetOrbitRadius = config.baseOrbitRadius + adjustedFftValue * (config.maxOrbitRadius - config.baseOrbitRadius)
        }
        
        // Increase decay rate to pull bubbles back faster
        val adaptiveDecayRate = if (targetOrbitRadius < orbitRadius) {
            config.orbitDecayRate * 2f // Pull back to center faster
        } else {
            config.orbitDecayRate // Normal expansion rate
        }
        
        orbitRadius = orbitRadius * (1f - adaptiveDecayRate) + targetOrbitRadius * adaptiveDecayRate
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
            bubble.update(cachedTime, currentAudioEvent, i)
            
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
            val fftStr = audio.fftTest?.joinToString(", ", prefix = "[", postfix = "]") { "%.2f".format(it) } ?: "null"
            println(
                "Audio: Beat:${"%.2f".format(audio.beatIntensity)} " +
                "Vol:${"%.2f".format(audio.volume)} " +
                "Bass:${"%.2f".format(audio.bass)} " +
                "Mid:${"%.2f".format(audio.mid)} " +
                "Treble:${"%.2f".format(audio.treble)}"
            )
            println("FFT8: $fftStr")
        }
        println("Light Position: (${String.format("%.3f", config.lightPosition.first)}, ${String.format("%.3f", config.lightPosition.second)})")
        println("Positions: ${formatBubbleData(bubblePositions, 2) { i ->
            "(%.3f, %.3f)".format(bubblePositions[i * 2], bubblePositions[i * 2 + 1])
        }}")
        println("Colors: ${formatBubbleData(bubbleColors, 3) { i ->
            "(%.3f, %.3f, %.3f)".format(bubbleColors[i * 3], bubbleColors[i * 3 + 1], bubbleColors[i * 3 + 2])
        }}")
        println("Radii: ${bubbleRadii.joinToString(" ") { "%.3f".format(it) }}")
        println("Seeds: ${bubbleSeeds.joinToString(" ") { "%.2f".format(it) }}")
        println("================================")
    }
}