package com.musicvisualizer.android.audio

/**
 * Audio analysis data extracted from real-time audio stream.
 * All values are normalized to 0.0-1.0 range.
 */
data class AudioEvent(
    val beatIntensity: Float = 0f,         // Beat strength
    val volume: Float = 0f,                // Overall RMS volume
    val bass: Float = 0f,                  // Bass frequency energy
    val mid: Float = 0f,                   // Mid frequency energy
    val treble: Float = 0f,                // Treble frequency energy
    val fftTest: FloatArray? = null        // 8-band FFT test data
)

/**
 * Receives real-time audio analysis events.
 * Critical for decoupling audio analysis from visualization/processing components.
 */
interface AudioEventListener {
    fun onAudioEvent(event: AudioEvent)
}

/**
 * Contract for all audio analyzers - real and mock implementations
 */
interface AudioAnalyzer {
    fun addListener(listener: AudioEventListener)
    fun removeListener(listener: AudioEventListener)
    fun start(vararg params: Any)
    fun stop()
    fun isRunning(): Boolean
}

/**
 * Base implementation providing common listener management and thread safety
 */
abstract class BaseAudioAnalyzer : AudioAnalyzer {
    private val listeners = mutableListOf<AudioEventListener>()
    @Volatile
    protected var running = false

    override fun addListener(listener: AudioEventListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    override fun removeListener(listener: AudioEventListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    override fun isRunning(): Boolean = running

    protected fun notifyListeners(event: AudioEvent) {
        synchronized(listeners) {
            listeners.forEach { it.onAudioEvent(event) }
        }
    }
}