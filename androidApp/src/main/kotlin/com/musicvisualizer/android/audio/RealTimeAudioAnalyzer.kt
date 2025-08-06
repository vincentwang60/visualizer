package com.musicvisualizer.android.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.*

/**
 * Real-time audio analyzer using Android's Visualizer API.
 * Captures audio output from MediaPlayer without requiring microphone permissions.
 */
class RealTimeAudioAnalyzer : BaseAudioAnalyzer() {
    private var visualizer: Visualizer? = null

    // Audio capture parameters
    private val captureSize = 1024
    
    // Beat detection state
    private val energyHistory = mutableListOf<Float>()
    private val maxHistorySize = 12 // ~400ms at 30fps
    private var lastBeatTime = 0L
    private val minBeatInterval = 300L // Minimum 300ms between beats
    
    // Frequency band ranges (for 1024 FFT at ~44kHz sample rate)
    private val bassRange = 1..8        // ~20-250 Hz
    private val midRange = 9..64        // ~250-4000 Hz  
    private val trebleRange = 65..200   // ~4000+ Hz

    companion object {
        private const val TAG = "RealTimeAudioAnalyzer"
    }

    /**
     * Start analyzing audio from the given MediaPlayer session
     * @param params expects audioSessionId: Int
     */
    override fun start(vararg params: Any) {
        if (running) return
        
        val audioSessionId = params[0] as Int
        
        visualizer = Visualizer(audioSessionId).apply {
            captureSize = this@RealTimeAudioAnalyzer.captureSize
            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                    visualizer: Visualizer?,
                    waveform: ByteArray?,
                    samplingRate: Int
                ) {
                    // Not used - we'll use FFT instead
                }

                override fun onFftDataCapture(
                    visualizer: Visualizer?,
                    fft: ByteArray?,
                    samplingRate: Int
                ) {
                    fft?.let { analyzeFft(it, samplingRate) }
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            
            enabled = true
        }

        running = true
        Log.d(TAG, "Started real-time audio analysis")
    }

    override fun stop() {
        running = false
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
        Log.d(TAG, "Stopped real-time audio analysis")
    }

    private fun analyzeFft(fft: ByteArray, samplingRate: Int) {
        if (!running) return

        val magnitudes = convertFftToMagnitudes(fft)
        
        // Calculate frequency band energies with bass weighting
        val bassEnergy = calculateBandEnergy(magnitudes, bassRange) * 2f // Weight bass more heavily
        val midEnergy = calculateBandEnergy(magnitudes, midRange)
        val trebleEnergy = calculateBandEnergy(magnitudes, trebleRange)
        val totalEnergy = bassEnergy + midEnergy + trebleEnergy
        
        // Normalize band energies (0.0-1.0)
        val (bass, mid, treble) = normalizeBandEnergies(bassEnergy, midEnergy, trebleEnergy, totalEnergy)
        val volume = (totalEnergy / 1500f).coerceIn(0f, 1f)
        
        // Beat detection using bass-weighted energy for better drum detection
        val beatEnergy = bassEnergy + midEnergy * 0.5f
        val normalizedBeatEnergy = beatEnergy / 1000f
        val beatIntensity = detectBeat(normalizedBeatEnergy)
        
        // Calculate spectral centroid (brightness)
        val spectralCentroid = calculateSpectralCentroid(magnitudes, samplingRate)
        
        val audioEvent = AudioEvent(
            beatIntensity = beatIntensity,
            bass = bass,
            mid = mid,
            treble = treble,
            volume = volume,
            spectralCentroid = spectralCentroid
        )

        notifyListeners(audioEvent)
    }
    
    private fun convertFftToMagnitudes(fft: ByteArray): FloatArray {
        val fftSize = fft.size / 2
        val magnitudes = FloatArray(fftSize)
        
        for (i in 0 until fftSize) {
            val real = fft[i * 2].toFloat()
            val imag = fft[i * 2 + 1].toFloat()
            magnitudes[i] = sqrt(real * real + imag * imag)
        }
        
        return magnitudes
    }
    
    private fun calculateBandEnergy(magnitudes: FloatArray, range: IntRange): Float {
        var energy = 0f
        for (i in range) {
            if (i < magnitudes.size) {
                energy += magnitudes[i] * magnitudes[i]
            }
        }
        return energy
    }
    
    private fun normalizeBandEnergies(
        bassEnergy: Float,
        midEnergy: Float, 
        trebleEnergy: Float,
        totalEnergy: Float
    ): Triple<Float, Float, Float> {
        val normalizer = totalEnergy + 0.001f // Avoid division by zero
        return Triple(
            (bassEnergy / normalizer).coerceIn(0f, 1f),
            (midEnergy / normalizer).coerceIn(0f, 1f),
            (trebleEnergy / normalizer).coerceIn(0f, 1f)
        )
    }
    
    private fun detectBeat(currentEnergy: Float): Float {
        val currentTime = System.currentTimeMillis()
        
        // Add current energy to history
        energyHistory.add(currentEnergy)
        if (energyHistory.size > maxHistorySize) {
            energyHistory.removeAt(0)
        }
        
        // Need sufficient history for beat detection
        if (energyHistory.size < 6) {
            val earlyBeat = (currentEnergy / 50000f).coerceIn(0f, 1f)
            return earlyBeat
        }
        
        // Calculate local energy average
        val localAverage = energyHistory.takeLast(8).average().toFloat()
        val threshold = localAverage * 1.3f
        val timeSinceLastBeat = currentTime - lastBeatTime
        
        // Check beat conditions
        val energyExceedsThreshold = currentEnergy > threshold
        val timingOk = timeSinceLastBeat > minBeatInterval
        val isFirstBeat = lastBeatTime == 0L
        
        val isBeat = energyExceedsThreshold && (timingOk || isFirstBeat)
        
        return if (isBeat) {
            lastBeatTime = currentTime
            val energyRatio = currentEnergy / (threshold + 1f)
            val intensity = (energyRatio - 1f).coerceIn(0.1f, 1f)
            Log.d(TAG, "BEAT DETECTED! Intensity: $intensity")
            intensity
        } else {
            // Gradual decay for non-beat frames
            val timeFactor = if (lastBeatTime == 0L) 0f else {
                exp(-timeSinceLastBeat.toFloat() / 300f)
            }
            (timeFactor * 0.2f).coerceIn(0f, 0.3f)
        }
    }
    
    private fun calculateSpectralCentroid(magnitudes: FloatArray, samplingRate: Int): Float {
        var weightedSum = 0f
        var magnitudeSum = 0f
        
        for (i in magnitudes.indices) {
            val frequency = (i * samplingRate) / (2f * magnitudes.size)
            val magnitude = magnitudes[i]
            weightedSum += frequency * magnitude
            magnitudeSum += magnitude
        }
        
        val centroid = if (magnitudeSum > 0) weightedSum / magnitudeSum else 0f
        
        // Normalize to 0-1 range (assuming max frequency of interest is ~8kHz)
        return (centroid / 8000f).coerceIn(0f, 1f)
    }
}