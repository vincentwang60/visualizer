package com.musicvisualizer.android.visualizers

import android.graphics.Color
import android.util.Log
import com.musicvisualizer.android.audio.AudioEvent
import com.musicvisualizer.android.audio.AudioEventListener
import kotlin.math.*
import kotlin.random.Random

private const val MAX_BUBBLES = 16
private const val FRAME_TIME_NS = 16_000_000L
private const val TAG = "MusicViz-Bubble"

data class BubbleConfig(
    val saturation: Float = 0.7f,
    val baseRadius: Float = 0.08f,
    val maxSpikeRadius: Float = 0.08f,
    val minSpikeRadius: Float = 0.03f,
    val bubbleLifetime: Long = 500L
)

private data class Bubble(
    val config: BubbleConfig,
    val seed: Float = Random.nextFloat(),
    val baseRadius: Float = config.baseRadius,
    val creationTime: Long = System.currentTimeMillis(),
    var position: Pair<Float, Float> = Pair(0.5f, 0.5f),
    val isCentral: Boolean = false,
    val bandIndex: Int = -1,
    val maxOrbitRadius: Float = 0f,
    val angularVelocity: Float = 0f,
    var currentRadius: Float = 0f,
    var currentAngle: Float = Random.nextFloat() * 2f * PI.toFloat(),
    val centerPosition: Pair<Float, Float> = Pair(0.5f, 0.5f),
    val centralBubbleRadius: Float = 0f,
    val fftMagnitude: Float = 0f
) {
    var radius: Float = baseRadius
    var color: FloatArray = floatArrayOf(0.6f, 0.4f, 0.8f)

    fun update(time: Float, lowFreqEnergy: Float = 0f, deltaTime: Float = 0.016f) {
        if (isCentral) {
            radius = baseRadius + lowFreqEnergy * 0.12f
        } else {
            val age = (System.currentTimeMillis() - creationTime) / 1000f
            val orbitProgress = sin(age / (config.bubbleLifetime / 1000f) * PI.toFloat())
            currentRadius = centralBubbleRadius * (0.5f + orbitProgress * (maxOrbitRadius / centralBubbleRadius - 0.5f))
            currentAngle += angularVelocity * deltaTime
            
            val orbitalX = centerPosition.first + cos(currentAngle) * currentRadius
            val orbitalY = centerPosition.second + sin(currentAngle) * currentRadius
            position = Pair(orbitalX, orbitalY)
        }
        color = getColor(time, lowFreqEnergy)
    }
    
    fun isExpired() = !isCentral && System.currentTimeMillis() - creationTime > config.bubbleLifetime
    
    private fun getColor(time: Float, lowFreqEnergy: Float = 0f): FloatArray {
        val hue = if (isCentral) {
            (time * 30f + lowFreqEnergy * 300f) % 360f
        } else {
            (time * 30f + seed * 360f) % 360f
        }
        val saturation = if (isCentral) {
            (config.saturation / 2.0f + lowFreqEnergy).coerceIn(0f, 1f)
        } else {
            1.0f
        }
        
        val hsv = floatArrayOf(hue, saturation, 1.0f)
        val argbColor = Color.HSVToColor(hsv)
        return floatArrayOf(
            Color.red(argbColor) / 255f,
            Color.green(argbColor) / 255f,
            Color.blue(argbColor) / 255f
        )
    }
}

class BubbleSystem(private val config: BubbleConfig = BubbleConfig()) : AudioEventListener {
    private val bubbles = mutableListOf<Bubble>()
    private val startTime = System.nanoTime()
    private var currentLowFreqEnergy = 0f
    private var currentHighFreqEnergy = 0f
    private var cachedTime = 0f
    private var lastTimeUpdate = 0L
    private var lastFrameTime = 0L
    private var centralBubblePosition = Pair(0.5f, 0.5f)
    private var centralBubbleRadius = config.baseRadius * 2.0f
    private var globalAngleOffset = 0f
    private var lightAngle = 0f
    private var tilt = 0f
    private var chromaticAberration = 0f
    private var strobeThreshold = 0.8f
    private var strobe = 0f
    private var fft = FloatArray(8)
    private var smoothEnergy = 0f

    val bubbleColors = FloatArray(MAX_BUBBLES * 3)
    val bubbleRadii = FloatArray(MAX_BUBBLES)
    val bubblePositions = FloatArray(MAX_BUBBLES * 2)
    val bubbleSeeds = FloatArray(MAX_BUBBLES)

    init {
        val centralBubble = Bubble(
            config = config,
            baseRadius = config.baseRadius * 1.8f,
            position = Pair(0.5f, 0.5f),
            isCentral = true,
            seed = 0f
        )
        bubbles.add(centralBubble)
        centralBubbleRadius = centralBubble.baseRadius
    }

    override fun onAudioEvent(event: AudioEvent) {
        currentLowFreqEnergy = event.lowFrequencyEnergy
        currentHighFreqEnergy = event.highFrequencyEnergy
        smoothEnergy = event.smoothEnergy
        for (i in fft.indices) {
            fft[i] = fft[i] * 0.9f + event.fftTest[i] * 0.1f
        }
        
        val strobeValue = event.fftTest.map { max(it - 0.5f, 0f) }.sum() * 1.5f
        if (strobeValue > strobeThreshold) {
            strobeThreshold = min(2.0f, (strobeValue + strobeThreshold) / 2f + 0.05f)
            strobe += (strobeValue - strobe) * 0.5f
        } else {
            strobeThreshold = max(1.0f, strobeThreshold - 0.02f)
            strobe += (1f - strobe) * 0.5f
        }
        
        for (spike in event.spikes) {
            synchronized(bubbles) {
                if (bubbles.size < MAX_BUBBLES) {
                    val normalizedIndex = spike.bandIndex.toFloat() / 7.0f
                    val spikeRadius = config.maxSpikeRadius - (normalizedIndex * (config.maxSpikeRadius - config.minSpikeRadius))
                    val fftMagnitude = if (spike.bandIndex < event.fftTest.size) event.fftTest[spike.bandIndex] else 0f
                    
                    val amplitude = (max(fftMagnitude - 0.4f, 0f) / 0.7f).let { it * it } * 0.2f
                    val maxOrbitRadius = centralBubbleRadius + amplitude
                    val baseAngularVel = 2.0f + amplitude * 20.0f
                    val angularVelocity = if (Random.nextBoolean()) baseAngularVel else -baseAngularVel
                    
                    val bandAngleOffset = (spike.bandIndex.toFloat() / 8f) * 2f * PI.toFloat()
                    val startingAngle = globalAngleOffset + bandAngleOffset + Random.nextFloat()
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
                        currentRadius = halfwayRadius,
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
        val deltaTime = if (lastFrameTime != 0L) (now - lastFrameTime) / 1_000_000_000f else 0.016f
        globalAngleOffset += deltaTime * 0.2f
        lastFrameTime = now
        
        if (now - lastTimeUpdate > FRAME_TIME_NS / 2) {
            cachedTime = ((now - startTime) / 10_000_000_000.0 % (2 * PI)).toFloat()
            lastTimeUpdate = now
        }
        
        synchronized(bubbles) {
            bubbles.removeAll { it.isExpired() }
            
            for (i in 0 until bubbles.size) {
                val bubble = bubbles[i]
                bubble.update(cachedTime, currentLowFreqEnergy, deltaTime)
                
                if (bubble.isCentral) {
                    centralBubblePosition = bubble.position
                    centralBubbleRadius = bubble.radius
                }
                
                System.arraycopy(bubble.color, 0, bubbleColors, i * 3, 3)
                bubbleRadii[i] = bubble.radius
                bubblePositions[i * 2] = bubble.position.first
                bubblePositions[i * 2 + 1] = bubble.position.second
                bubbleSeeds[i] = bubble.seed
            }
        }

        lightAngle += 0.01f
        tilt = currentHighFreqEnergy * 3.0f + 2.0f * sin(cachedTime * 0.5f) + 1.5f * cos(cachedTime * 0.75f) + 0.8f * sin(cachedTime * 1.25f)
        return cachedTime
    }

    fun getConfig() = config
    fun getBubbleCount() = bubbles.size
    fun getLightPosition() = Pair(0.5f + 2.0f * cos(lightAngle), 0.5f + 2.0f * sin(lightAngle))
    fun getTilt() = tilt
    fun getChromaticAberration() = (strobe - 1.0f) * 0.05f
    fun getStrobe() = strobe
    fun getFft() = fft
    fun getSmoothEnergy() = smoothEnergy
}