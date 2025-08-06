package com.musicvisualizer.android.audio

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioEvent

        if (beatIntensity != other.beatIntensity) return false
        if (bass != other.bass) return false
        if (mid != other.mid) return false
        if (treble != other.treble) return false
        if (volume != other.volume) return false
        if (spectralCentroid != other.spectralCentroid) return false
        if (mfcc != null) {
            if (other.mfcc == null) return false
            if (!mfcc.contentEquals(other.mfcc)) return false
        } else if (other.mfcc != null) return false
        if (harmonicity != other.harmonicity) return false

        return true
    }

    override fun hashCode(): Int {
        var result = beatIntensity.hashCode()
        result = 31 * result + bass.hashCode()
        result = 31 * result + mid.hashCode()
        result = 31 * result + treble.hashCode()
        result = 31 * result + volume.hashCode()
        result = 31 * result + spectralCentroid.hashCode()
        result = 31 * result + (mfcc?.contentHashCode() ?: 0)
        result = 31 * result + harmonicity.hashCode()
        return result
    }
}

/**
 * Interface for receiving audio analysis events
 */
interface AudioEventListener {
    fun onAudioEvent(event: AudioEvent)
}

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