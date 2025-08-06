package com.musicvisualizer.android.audio

import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * Mock audio analyzer generating realistic beat patterns.
 * Useful for testing and development without real audio input.
 */
class MockAudioAnalyzer : BaseAudioAnalyzer() {
    private val startTime = System.currentTimeMillis()
    private var thread: Thread? = null

    // Music simulation parameters
    private val bpm = 128f
    private val beatsPerMeasure = 4

    companion object {
        private const val TAG = "MockAudioAnalyzer"
        private const val FRAME_INTERVAL_MS = 32L // ~30 FPS
    }

    override fun start(vararg params: Any) {
        if (running) return
        
        running = true
        simulateAudioStream()
        Log.d(TAG, "Started")
    }

    override fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        Log.d(TAG, "Stopped")
    }

    private fun simulateAudioStream() {
        thread = Thread {
            try {
                while (running && !Thread.currentThread().isInterrupted) {
                    notifyListeners(generateMockAudioEvent())
                    Thread.sleep(FRAME_INTERVAL_MS)
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Thread interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Error in simulation thread", e)
            }
        }.apply { start() }
    }

    private fun generateMockAudioEvent(): AudioEvent {
        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000f
        val beatPosition = (elapsedSeconds * bpm / 60f) % beatsPerMeasure
        val beatIntensity = calculateBeatIntensity(beatPosition, elapsedSeconds)

        // Generate 8-band FFT data
        val fftTest = FloatArray(8) { i ->
            val baseLevel = when (i) {
                0 -> 0.8f  // Sub-bass
                1 -> 0.9f  // Bass
                2 -> 0.7f  // Low-mid
                3 -> 0.6f  // Mid
                4 -> 0.5f  // Upper-mid
                5 -> 0.4f  // Presence
                6 -> 0.3f  // Brilliance
                7 -> 0.2f  // Air
                else -> 0.5f
            }
            generateBandEnergy(beatIntensity, baseLevel)
        }

        // Calculate bass, mid, treble from FFT data
        val bass = (fftTest[0] + fftTest[1]) / 2f
        val mid = (fftTest[2] + fftTest[3] + fftTest[4]) / 3f
        val treble = (fftTest[5] + fftTest[6] + fftTest[7]) / 3f

        return AudioEvent(
            beatIntensity = beatIntensity,
            volume = beatIntensity * 0.8f,
            bass = bass,
            mid = mid,
            treble = treble,
            fftTest = fftTest
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


}