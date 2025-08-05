package com.musicvisualizer.android.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.random.Random
import kotlin.math.*

interface AudioEventListener {
    fun onAudioEvent(event: AudioEvent)
}

/**
 * Audio analysis data extracted from real-time audio stream
 */
data class AudioEvent(
    val beatIntensity: Float = 0f,         // Beat strength (0.0-1.0)
    val bass: Float = 0f,                  // ~20-250 Hz (0.0-1.0)
    val mid: Float = 0f,                   // ~250-4000 Hz (0.0-1.0)
    val treble: Float = 0f,                // ~4000+ Hz (0.0-1.0)
    val volume: Float = 0f,                // Overall RMS volume (0.0-1.0)
    val spectralCentroid: Float = 0f,      // "Brightness" - where most energy is (0.0-1.0)
    val mfcc: FloatArray? = null,          // Mel-frequency cepstral coefficients (timbre)
    val harmonicity: Float = 0f            // How harmonic vs noisy (0.0-1.0)
)

/**
 * Interface that defines the contract for all audio analyzers
 */
interface AudioAnalyzer {
    fun addListener(listener: AudioEventListener)
    fun removeListener(listener: AudioEventListener)
    fun start(vararg params: Any)
    fun stop()
    fun isRunning(): Boolean
}

/**
 * Base class for audio analyzers that provides common functionality
 */
abstract class BaseAudioAnalyzer : AudioAnalyzer {
    protected val listeners = mutableListOf<AudioEventListener>()
    protected var running = false

    override fun addListener(listener: AudioEventListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AudioEventListener) {
        listeners.remove(listener)
    }

    override fun isRunning(): Boolean = running

    protected fun notifyListeners(event: AudioEvent) {
        listeners.forEach { it.onAudioEvent(event) }
    }
}

/**
 * Real-time audio analyzer using Android's Visualizer API.
 */
class RealTimeAudioAnalyzer : BaseAudioAnalyzer() {
    private var visualizer: Visualizer? = null

    // Audio capture parameters
    private val captureSize = 128 // Small size for just volume analysis

    /**
     * Start analyzing audio from the given MediaPlayer session
     * @param params expects audioSessionId: Int
     */
    override fun start(vararg params: Any) {
        if (running) {
            Log.w("RealTimeAudioAnalyzer", "Already running")
            return
        }
        if (params.isEmpty() || params[0] !is Int) {
            Log.e("RealTimeAudioAnalyzer", "audioSessionId (Int) required")
            return
        }
        val audioSessionId = params[0] as Int

        try {
            // Create visualizer for the MediaPlayer's audio session
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = this@RealTimeAudioAnalyzer.captureSize

                // Set up waveform capture listener for volume analysis
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveform?.let { analyzeWaveform(it) }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        // Not used for volume-only analysis
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false) // Only capture waveform

                enabled = true
            }

            running = true
            Log.d("RealTimeAudioAnalyzer", "Started volume analysis")

        } catch (e: Exception) {
            Log.e("RealTimeAudioAnalyzer", "Failed to start audio analyzer", e)
        }
    }

    override fun stop() {
        running = false
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
        Log.d("RealTimeAudioAnalyzer", "Stopped volume analysis")
    }

    private fun analyzeWaveform(waveform: ByteArray) {
        if (!running) return

        // Calculate RMS (Root Mean Square) for volume
        var sum = 0.0
        for (byte in waveform) {
            val sample = (byte.toInt() - 128) / 128.0 // Convert to -1.0 to 1.0 range
            sum += sample * sample
        }

        val rms = sqrt(sum / waveform.size)
        val volume = (rms * 2.0).coerceIn(0.0, 1.0).toFloat() // Scale and clamp to 0-1

        // Create simple audio event with only volume
        val audioEvent = AudioEvent(
            beatIntensity = 0f,
            bass = 0f,
            mid = 0f,
            treble = 0f,
            volume = volume,
            spectralCentroid = 0f,
            mfcc = null,
            harmonicity = 0f
        )

        // Notify all listeners
        notifyListeners(audioEvent)
    }
}

/**
 * Simplified mock audio analyzer that generates realistic beat intensity patterns
 * All other audio parameters are set to zero for simplicity
 */
class MockAudioAnalyzer : BaseAudioAnalyzer() {
    private val startTime = System.currentTimeMillis()

    // Beat simulation parameters
    private val bpm = 128f // Beats per minute (typical dance music)
    private val beatsPerMeasure = 4

    override fun start(vararg params: Any) {
        running = true
        simulateAudioStream()
    }

    override fun stop() {
        running = false
    }

    private fun simulateAudioStream() {
        Thread {
            while (running) {
                val event = generateMockAudioEvent()
                notifyListeners(event)
                Thread.sleep(32) // ~30 FPS audio analysis
            }
        }.start()
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
            bass = 0f,
            mid = 0f,
            treble = 0f,
            volume = 0f,
            spectralCentroid = 0f,
            mfcc = null,
            harmonicity = 0f
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

        // Add some musical variation over time (builds, drops, etc.)
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
}