package com.musicvisualizer.android.visualizers

import android.graphics.Color
import android.util.Log
import com.musicvisualizer.android.audio.AudioEvent
import com.musicvisualizer.android.audio.AudioEventListener
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val MAX_BUBBLES = 20
private const val BUBBLE_LIFETIME_MS = 500L
private const val TAG = "MusicViz-Bubble"

data class BubbleConfig(
    val saturation: Float = 0.7f,
    val baseRadius: Float = 0.08f,
    val maxSpikeRadius: Float = 0.10f,
    val minSpikeRadius: Float = 0.04f,
    val lightPosition: Pair<Float, Float> = Pair(0.2f, 0.8f),
)

private data class Bubble(
    val config: BubbleConfig,
    val seed: Float = Random.nextFloat(),
    val baseRadius: Float = config.baseRadius,
    var radius: Float = baseRadius,
    var color: FloatArray = floatArrayOf(0.6f, 0.4f, 0.8f),
    val creationTime: Long = System.currentTimeMillis(),
    var position: Pair<Float, Float> = Pair(0.5f, 0.5f),
    val isCentral: Boolean = false,
    val bandIndex: Int = -1,
    val maxOrbitRadius: Float = 0f,
    val angularVelocity: Float = 0f, // Negative = clockwise, positive = counterclockwise
    var currentRadius: Float = 0f,
    var currentAngle: Float = Random.nextFloat() * 2f * PI.toFloat(),
    val centerPosition: Pair<Float, Float> = Pair(0.5f, 0.5f),
    val centralBubbleRadius: Float = 0f, // Store the central bubble's radius at spawn time
    val fftMagnitude: Float = 0f
) {    
    fun update(time: Float, lowFreqEnergy: Float = 0f, deltaTime: Float = 0.016f) {
        if (isCentral) {
            val motionRange = 0.05f
            val offsetX = motionRange * sin(time * 0.25f)
            val offsetY = motionRange * cos(time * 0.25f + 1.5f)
            position = Pair(0.5f + offsetX, 0.5f + offsetY)
            radius = baseRadius + lowFreqEnergy * 0.2f
        } else {
            val age = (System.currentTimeMillis() - creationTime) / 1000f
            val maxAge = BUBBLE_LIFETIME_MS / 1000f
            val normalizedAge = age / maxAge
            
            // Modified orbit pattern: start at halfway inside central bubble, go out to max, then back to halfway inside
            // Using a sine wave that goes from 0.5 to maxScale and back to 0.5
            val minRadiusScale = 0.5f // Halfway inside central bubble
            val maxRadiusScale = maxOrbitRadius / centralBubbleRadius
            val radiusRange = maxRadiusScale - minRadiusScale
            
            // Sine wave from 0 to π gives us 0 -> 1 -> 0, perfect for halfway -> max -> halfway
            val orbitProgress = sin(normalizedAge * PI.toFloat())
            currentRadius = centralBubbleRadius * (minRadiusScale + orbitProgress * radiusRange)
            
            currentAngle += angularVelocity * deltaTime
            
            val orbitalX = centerPosition.first + cos(currentAngle) * currentRadius
            val orbitalY = centerPosition.second + sin(currentAngle) * currentRadius
            position = Pair(orbitalX, orbitalY)
        }
        color = getColor(time, lowFreqEnergy)
    }
    
    fun isExpired(): Boolean {
        if (isCentral) return false
        return System.currentTimeMillis() - creationTime > BUBBLE_LIFETIME_MS
    }
    
    private fun getColor(time: Float, lowFreqEnergy: Float = 0f): FloatArray {
        val hue = if (isCentral) {
            // Central bubble: shift hue based on low frequency energy
            // Red/orange (0-60) for high bass, blue/purple (240-300) for low bass
            val bassHue = lowFreqEnergy * 360f // 0° to 360° (red to blue)
            (bassHue + time * 10f) % 360f // Slow color cycling
        } else {
            // Spike bubbles: original rainbow cycling
            (time * 30f + seed * 360f) % 360f
        }
        
        val saturation = if (isCentral) {
            // Central bubble: higher saturation with more bass
            (config.saturation + lowFreqEnergy * 0.3f).coerceIn(0f, 1f)
        } else {
            config.saturation * fftMagnitude
        }
        
        val hsv = floatArrayOf(hue, saturation, 0.9f)
        val argbColor = Color.HSVToColor(hsv)
        return floatArrayOf(
            Color.red(argbColor) / 255f,
            Color.green(argbColor) / 255f,
            Color.blue(argbColor) / 255f
        )
    }
}

class BubbleSystem(
    private val config: BubbleConfig = BubbleConfig()
) : AudioEventListener {
    private val bubbles = mutableListOf<Bubble>()
    private val startTime = System.nanoTime()
    private var currentLowFreqEnergy = 0f
    private var cachedTime = 0f
    private var lastTimeUpdate = 0L
    private var lastFrameTime = 0L
    private val FRAME_TIME_NS = 16_000_000L
    private var centralBubblePosition = Pair(0.5f, 0.5f)
    private var centralBubbleRadius = config.baseRadius * 2.0f
    private var globalAngleOffset = 0f

    val bubbleColors = FloatArray(MAX_BUBBLES * 3)
    val bubbleRadii = FloatArray(MAX_BUBBLES)
    val bubblePositions = FloatArray(MAX_BUBBLES * 2)
    val bubbleSeeds = FloatArray(MAX_BUBBLES)

    init {
        val centralBubble = Bubble(
            config = config,
            baseRadius = config.baseRadius * 1.2f,
            position = Pair(0.5f, 0.5f),
            isCentral = true
        )
        bubbles.add(centralBubble)
        centralBubbleRadius = centralBubble.baseRadius
    }

    override fun onAudioEvent(event: AudioEvent) {
        currentLowFreqEnergy = event.lowFrequencyEnergy
        
        for (spike in event.spikes) {
            synchronized(bubbles) {
                if (bubbles.size < MAX_BUBBLES) {
                    val normalizedIndex = spike.bandIndex.toFloat() / 7.0f
                    val spikeRadius = config.maxSpikeRadius - (normalizedIndex * (config.maxSpikeRadius - config.minSpikeRadius))
                    val fftMagnitude = if (spike.bandIndex < event.fftTest.size) event.fftTest[spike.bandIndex] else 0f
                    
                    // Calculate max orbit radius from center (not from edge of central bubble)
                    val maxOrbitRadius = centralBubbleRadius + (fftMagnitude * fftMagnitude * 0.25f)
                    val baseAngularVel = Random.nextFloat() * 1.5f + fftMagnitude * 2.0f // Reduced speed
                    
                    // Random direction: 50/50 clockwise vs counterclockwise
                    val angularVelocity = if (Random.nextBoolean()) baseAngularVel else -baseAngularVel
                    // Band-based angle offset + global rotation
                    val bandAngleOffset = (spike.bandIndex.toFloat() / 8f) * 2f * PI.toFloat() // Evenly space 8 bands around circle
                    val startingAngle = globalAngleOffset + bandAngleOffset + Random.nextFloat()
                    
                    // Calculate initial position at halfway inside the central bubble
                    val halfwayRadius = centralBubbleRadius * 0.5f
                    val initialX = centralBubblePosition.first + cos(startingAngle) * halfwayRadius
                    val initialY = centralBubblePosition.second + sin(startingAngle) * halfwayRadius
                    
                    bubbles.add(Bubble(
                        config = config,
                        baseRadius = spikeRadius,
                        bandIndex = spike.bandIndex,
                        position = Pair(initialX, initialY),
                        maxOrbitRadius = maxOrbitRadius,
                        angularVelocity = angularVelocity,
                        currentRadius = halfwayRadius, // Start halfway inside
                        currentAngle = startingAngle,
                        centerPosition = centralBubblePosition,
                        centralBubbleRadius = centralBubbleRadius,
                        fftMagnitude = fftMagnitude
                    ))
                }
            }
        }
    }

    fun update(): Float {
        val now = System.nanoTime()
        val deltaTime = if (lastFrameTime != 0L) {
            (now - lastFrameTime) / 1_000_000_000f
        } else {
            0.016f
        }
        globalAngleOffset += deltaTime * 0.2f // Smooth continuous rotation
        lastFrameTime = now
        
        if (now - lastTimeUpdate > FRAME_TIME_NS / 2) {
            cachedTime = ((now - startTime) / 1_000_000_000f) % (4f * PI.toFloat())
            lastTimeUpdate = now
        }
        
        synchronized(bubbles) {
            bubbles.removeAll { it.isExpired() }
            
            for (i in 0 until bubbles.size) {
                val bubble = bubbles[i]
                bubble.update(cachedTime, currentLowFreqEnergy, deltaTime)
                
                if (bubble.isCentral) {
                    centralBubblePosition = bubble.position
                    centralBubbleRadius = bubble.radius // Update radius for orbital calculations
                }
                
                System.arraycopy(bubble.color, 0, bubbleColors, i * 3, 3)
                bubbleRadii[i] = bubble.radius
                bubblePositions[i * 2] = bubble.position.first
                bubblePositions[i * 2 + 1] = bubble.position.second
                bubbleSeeds[i] = bubble.seed
            }
        }

        return cachedTime
    }

    fun getConfig() = config
    fun getBubbleCount() = bubbles.size
}