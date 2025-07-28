package com.musicvisualizer.android.audio

import kotlin.random.Random
import kotlin.math.*

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
        return beatIntensity == other.beatIntensity &&
                bass == other.bass &&
                mid == other.mid &&
                treble == other.treble &&
                volume == other.volume &&
                spectralCentroid == other.spectralCentroid &&
                mfcc.contentEquals(other.mfcc) &&
                harmonicity == other.harmonicity
    }

    override fun hashCode(): Int {
        return listOf(
            beatIntensity, bass, mid, treble, volume,
            spectralCentroid, mfcc.contentHashCode(), harmonicity
        ).hashCode()
    }
}

/**
 * Interface for audio event listeners
 */
interface AudioEventListener {
    fun onAudioEvent(event: AudioEvent)
}

/**
 * Mock audio analyzer that generates plausible real-time audio events
 * In production, this would analyze actual audio stream data
 */
class MockAudioAnalyzer {
    private val listeners = mutableListOf<AudioEventListener>()
    private var isRunning = false
    private var lastBeatTime = 0L
    private var currentBpm = 120f
    private var bassSmooth = 0f
    private var midSmooth = 0f
    private var trebleSmooth = 0f
    private var volumeSmooth = 0f
    
    // Simulation parameters
    private val smoothingFactor = 0.15f
    
    fun addListener(listener: AudioEventListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: AudioEventListener) {
        listeners.remove(listener)
    }
    
    fun start() {
        isRunning = true
        simulateAudioStream()
    }
    
    fun stop() {
        isRunning = false
    }
    
    private fun simulateAudioStream() {
        Thread {
            while (isRunning) {
                val event = generateMockAudioEvent()
                listeners.forEach { it.onAudioEvent(event) }
                Thread.sleep(16) // ~60 FPS audio analysis
            }
        }.start()
    }
    
    private fun generateMockAudioEvent(): AudioEvent {
        val time = System.currentTimeMillis()
        
        // Simulate varying music with some structure
        val musicTime = time / 1000f
        val musicPhase = (musicTime * currentBpm / 60f) % 4f // 4-beat measure
        
        // Generate raw values with musical structure
        val rawBass = (sin(musicTime * 0.5f) * 0.5f + 0.5f) * 
                     (0.3f + 0.7f * sin(musicPhase * PI.toFloat() / 2f).pow(2))
        val rawMid = (sin(musicTime * 0.8f + 1f) * 0.5f + 0.5f) *
                    (0.4f + 0.6f * cos(musicPhase * PI.toFloat() / 4f).pow(2))
        val rawTreble = (sin(musicTime * 1.2f + 2f) * 0.5f + 0.5f) *
                       (0.2f + 0.8f * sin(musicPhase * PI.toFloat()).pow(4))
        val rawVolume = (rawBass + rawMid + rawTreble) / 3f
        
        // Apply smoothing (simulates real audio analysis)
        bassSmooth = bassSmooth * (1f - smoothingFactor) + rawBass * smoothingFactor
        midSmooth = midSmooth * (1f - smoothingFactor) + rawMid * smoothingFactor
        trebleSmooth = trebleSmooth * (1f - smoothingFactor) + rawTreble * smoothingFactor
        volumeSmooth = volumeSmooth * (1f - smoothingFactor) + rawVolume * smoothingFactor
        
        // Beat detection simulation
        val beatPattern = sin(musicPhase * PI.toFloat() / 2f).pow(6) // Strong on beats 1 and 3
        val beatIntensity = (beatPattern * volumeSmooth).coerceIn(0f, 1f)
        
        // Additional features
        val spectralCentroid = (midSmooth * 0.4f + trebleSmooth * 0.6f).coerceIn(0f, 1f)
        val harmonicity = ((bassSmooth + midSmooth) / 2f * (1f - trebleSmooth * 0.5f)).coerceIn(0f, 1f)
        
        // Simple MFCC simulation (12 coefficients)
        val mfcc = FloatArray(12) { i ->
            (sin(musicTime * (i + 1) * 0.3f) * 0.5f + 0.5f) * volumeSmooth
        }
        
        return AudioEvent(
            beatIntensity = beatIntensity,
            bass = bassSmooth.coerceIn(0f, 1f),
            mid = midSmooth.coerceIn(0f, 1f),
            treble = trebleSmooth.coerceIn(0f, 1f),
            volume = volumeSmooth.coerceIn(0f, 1f),
            spectralCentroid = spectralCentroid,
            mfcc = mfcc,
            harmonicity = harmonicity
        )
    }
}