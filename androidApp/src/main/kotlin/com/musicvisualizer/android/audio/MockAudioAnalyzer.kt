package com.musicvisualizer.android.audio

import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * Mock audio analyzer that generates realistic beat intensity patterns.
 * Useful for testing and development when real audio input is not available.
 */
class MockAudioAnalyzer : BaseAudioAnalyzer() {
    private val startTime = System.currentTimeMillis()
    private var thread: Thread? = null

    // Beat simulation parameters
    private val bpm = 128f // Beats per minute (typical dance music)
    private val beatsPerMeasure = 4

    companion object {
        private const val TAG = "MockAudioAnalyzer"
        private const val FRAME_RATE_MS = 32L // ~30 FPS audio analysis
    }

    override fun start(vararg params: Any) {
        if (running) {
            Log.w(TAG, "Already running")
            return
        }
        
        running = true
        simulateAudioStream()
        Log.d(TAG, "Started mock audio analysis")
    }

    override fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        Log.d(TAG, "Stopped mock audio analysis")
    }

    private fun simulateAudioStream() {
        thread = Thread {
            try {
                while (running && !Thread.currentThread().isInterrupted) {
                    val event = generateMockAudioEvent()
                    notifyListeners(event)
                    Thread.sleep(FRAME_RATE_MS)
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Mock audio thread interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Error in mock audio thread", e)
            }
        }.apply { start() }
    }

    private fun generateMockAudioEvent(): AudioEvent {
        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - startTime) / 1000f

        // Calculate beat position within a measure (0.0 to 4.0)
        val beatPosition = (elapsedSeconds * bpm / 60f) % beatsPerMeasure

        // Generate realistic beat intensity pattern
        val beatIntensity = calculateBeatIntensity(beatPosition, elapsedSeconds)

        return AudioEvent(
            beatIntensity = beatIntensity,
            bass = generateBandEnergy(beatIntensity, 0.8f),
            mid = generateBandEnergy(beatIntensity, 0.5f),
            treble = generateBandEnergy(beatIntensity, 0.3f),
            volume = beatIntensity * 0.8f,
            spectralCentroid = generateSpectralCentroid(elapsedSeconds)
        )
    }

    private fun calculateBeatIntensity(beatPosition: Float, elapsedSeconds: Float): Float {
        // Create different intensity patterns for different beats in a measure
        val beatInMeasure = (beatPosition % beatsPerMeasure).toInt()
        val fractionalBeat = beatPosition % 1f

        // Strong beats on 1 and 3, weaker on 2 and 4 (typical dance pattern)
        val baseBeatStrength = when (beatInMeasure) {
            0 -> 1.0f      // Beat 1 - strongest (kick drum)
            1 -> 0.4f      // Beat 2 - weaker 
            2 -> 0.8f      // Beat 3 - strong (kick drum)
            3 -> 0.4f      // Beat 4 - weaker
            else -> 0.3f
        }

        // Create sharp attack and quick decay (like real drum hits)
        val beatPulse = when {
            fractionalBeat < 0.1f -> {
                // Sharp attack in first 10% of beat
                val attack = fractionalBeat / 0.1f
                attack * attack // Quadratic attack
            }
            fractionalBeat < 0.3f -> {
                // Quick decay for next 20%
                val decay = (fractionalBeat - 0.1f) / 0.2f
                1f - (decay * decay * 0.8f) // Leave some sustain
            }
            else -> {
                // Low sustain for rest of beat
                0.2f
            }
        }

        // Add musical variation over time (builds, drops, etc.)
        val musicVariation = getMusicVariation(elapsedSeconds)

        // Add subtle randomness for realism (±10%)
        val randomVariation = 1f + (Random.nextFloat() - 0.5f) * 0.2f

        val finalIntensity = baseBeatStrength * beatPulse * musicVariation * randomVariation

        return finalIntensity.coerceIn(0f, 1f)
    }

    private fun getMusicVariation(elapsedSeconds: Float): Float {
        // Simulate musical structure with builds and drops every ~32 seconds
        val musicCycle = elapsedSeconds / 32f
        val cyclePosition = musicCycle % 1f

        return when {
            cyclePosition < 0.6f -> {
                // Normal energy for first 60% of cycle
                0.7f + 0.3f * sin(cyclePosition * PI.toFloat() * 2f)
            }
            cyclePosition < 0.8f -> {
                // Build up energy
                val buildPosition = (cyclePosition - 0.6f) / 0.2f
                0.7f + buildPosition * 0.4f
            }
            cyclePosition < 0.9f -> {
                // Peak energy (drop)
                1.1f
            }
            else -> {
                // Quick fade back to normal
                val fadePosition = (cyclePosition - 0.9f) / 0.1f
                1.1f - fadePosition * 0.4f
            }
        }
    }

    private fun generateBandEnergy(beatIntensity: Float, baseLevel: Float): Float {
        // Generate band energy based on beat intensity with some variation
        val variation = Random.nextFloat() * 0.3f - 0.15f // ±15% variation
        return (beatIntensity * baseLevel + variation).coerceIn(0f, 1f)
    }

    private fun generateSpectralCentroid(elapsedSeconds: Float): Float {
        // Simulate spectral centroid changing over time with some randomness
        val cyclicComponent = 0.5f + 0.3f * sin(elapsedSeconds * 0.1f)
        val randomComponent = Random.nextFloat() * 0.2f - 0.1f
        return (cyclicComponent + randomComponent).coerceIn(0f, 1f)
    }
}