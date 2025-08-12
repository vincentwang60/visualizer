package com.musicvisualizer.android.audio

import android.media.audiofx.Visualizer
import android.util.Log
import com.musicvisualizer.android.util.PerformanceConfig
import kotlin.math.*

/**
 * Optimized real-time audio analyzer with centralized performance configuration
 */
class RealTimeAudioAnalyzer : BaseAudioAnalyzer() {
    private var visualizer: Visualizer? = null

    // Use centralized performance configuration
    private val devicePerformance = PerformanceConfig.detectDevicePerformance()
    private val optimizedSettings = PerformanceConfig.getOptimizedSettings(devicePerformance)
    
    // Optimized audio capture parameters
    private var captureSize = optimizedSettings.fftCaptureSize
    private var processingInterval = optimizedSettings.audioProcessingInterval
    
    // Beat detection state with reduced history
    private val energyHistory = mutableListOf<Float>()
    private val maxHistorySize = PerformanceConfig.MAX_ENERGY_HISTORY_SIZE
    private var lastBeatTime = 0L
    private val minBeatInterval = 300L
    
    // Frequency band ranges (optimized for different FFT sizes)
    private val bassRange = when (captureSize) {
        256 -> 1..2
        512 -> 1..4
        else -> 1..8
    }
    private val midRange = when (captureSize) {
        256 -> 3..16
        512 -> 5..32
        else -> 9..64
    }
    private val trebleRange = when (captureSize) {
        256 -> 17..50
        512 -> 33..100
        else -> 65..200
    }

    // Performance optimizations
    private var lastProcessingTime = 0L
    private var cachedAudioEvent: AudioEvent? = null
    private var processingEnabled = true

    companion object {
        private const val TAG = "RealTimeAudioAnalyzer"
    }

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
                    // Not used
                }

                override fun onFftDataCapture(
                    visualizer: Visualizer?,
                    fft: ByteArray?,
                    samplingRate: Int
                ) {
                    fft?.let { analyzeFftOptimized(it, samplingRate) }
                }
            }, Visualizer.getMaxCaptureRate() / 4, false, true) // Reduced capture rate
            
            enabled = true
        }

        running = true
        Log.d(TAG, "Started optimized audio analysis with ${devicePerformance.name} performance settings")
    }

    override fun stop() {
        running = false
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
        cachedAudioEvent = null
        Log.d(TAG, "Stopped audio analysis")
    }

    private fun analyzeFftOptimized(fft: ByteArray, samplingRate: Int) {
        if (!running || !processingEnabled) return

        val currentTime = System.currentTimeMillis()
        
        // Throttle processing to reduce CPU usage
        if (currentTime - lastProcessingTime < processingInterval) {
            // Return cached event if available
            cachedAudioEvent?.let { notifyListeners(it) }
            return
        }
        lastProcessingTime = currentTime

        val magnitudes = convertFftToMagnitudesOptimized(fft)
        
        // Calculate frequency band energies with simplified calculations
        val bassEnergy = calculateBandEnergyOptimized(magnitudes, bassRange) * 1.5f
        val midEnergy = calculateBandEnergyOptimized(magnitudes, midRange)
        val trebleEnergy = calculateBandEnergyOptimized(magnitudes, trebleRange)
        val totalEnergy = bassEnergy + midEnergy + trebleEnergy
        
        // Normalize band energies
        val (bass, mid, treble) = normalizeBandEnergiesOptimized(bassEnergy, midEnergy, trebleEnergy, totalEnergy)
        val volume = (totalEnergy / (captureSize * 2f)).coerceIn(0f, 1f) // Adjusted normalization
        
        // Simplified beat detection
        val beatEnergy = bassEnergy + midEnergy * 0.3f
        val normalizedBeatEnergy = beatEnergy / (captureSize * 1.5f)
        val beatIntensity = detectBeatOptimized(normalizedBeatEnergy)
        
        // Simplified spectral centroid calculation
        val spectralCentroid = calculateSpectralCentroidOptimized(magnitudes, samplingRate)
        
        val audioEvent = AudioEvent(
            beatIntensity = beatIntensity,
            bass = bass,
            mid = mid,
            treble = treble,
            volume = volume,
            spectralCentroid = spectralCentroid
        )

        cachedAudioEvent = audioEvent
        notifyListeners(audioEvent)
    }
    
    private fun convertFftToMagnitudesOptimized(fft: ByteArray): FloatArray {
        val fftSize = fft.size / 2
        val magnitudes = FloatArray(fftSize)
        
        // Optimized loop with reduced calculations
        for (i in 0 until fftSize) {
            val real = fft[i * 2].toFloat()
            val imag = fft[i * 2 + 1].toFloat()
            magnitudes[i] = sqrt(real * real + imag * imag)
        }
        
        return magnitudes
    }
    
    private fun calculateBandEnergyOptimized(magnitudes: FloatArray, range: IntRange): Float {
        var energy = 0f
        // Process every other sample in range for performance
        for (i in range step 2) {
            if (i < magnitudes.size) {
                energy += magnitudes[i] * magnitudes[i]
            }
        }
        return energy * 2f // Compensate for skipping samples
    }
    
    private fun normalizeBandEnergiesOptimized(
        bassEnergy: Float,
        midEnergy: Float, 
        trebleEnergy: Float,
        totalEnergy: Float
    ): Triple<Float, Float, Float> {
        val normalizer = totalEnergy + 0.001f
        return Triple(
            (bassEnergy / normalizer).coerceIn(0f, 1f),
            (midEnergy / normalizer).coerceIn(0f, 1f),
            (trebleEnergy / normalizer).coerceIn(0f, 1f)
        )
    }
    
    private fun detectBeatOptimized(currentEnergy: Float): Float {
        val currentTime = System.currentTimeMillis()
        
        // Add current energy to history
        energyHistory.add(currentEnergy)
        if (energyHistory.size > maxHistorySize) {
            energyHistory.removeAt(0)
        }
        
        // Early return for insufficient history
        if (energyHistory.size < 4) {
            return (currentEnergy / (captureSize * 1.5f)).coerceIn(0f, 1f)
        }
        
        // Simplified beat detection
        val localAverage = energyHistory.takeLast(6).average().toFloat()
        val threshold = localAverage * 1.2f
        val timeSinceLastBeat = currentTime - lastBeatTime
        
        val energyExceedsThreshold = currentEnergy > threshold
        val timingOk = timeSinceLastBeat > minBeatInterval
        val isFirstBeat = lastBeatTime == 0L
        
        val isBeat = energyExceedsThreshold && (timingOk || isFirstBeat)
        
        return if (isBeat) {
            lastBeatTime = currentTime
            val energyRatio = currentEnergy / (threshold + 1f)
            val intensity = (energyRatio - 1f).coerceIn(0.1f, 1f)
            intensity
        } else {
            // Simplified decay
            val timeFactor = if (lastBeatTime == 0L) 0f else {
                exp(-timeSinceLastBeat.toFloat() / 400f)
            }
            (timeFactor * 0.15f).coerceIn(0f, 0.25f)
        }
    }
    
    private fun calculateSpectralCentroidOptimized(magnitudes: FloatArray, samplingRate: Int): Float {
        var weightedSum = 0f
        var magnitudeSum = 0f
        
        // Sample every 4th frequency for performance
        for (i in magnitudes.indices step 4) {
            val frequency = (i * samplingRate) / (2f * magnitudes.size)
            val magnitude = magnitudes[i]
            weightedSum += frequency * magnitude
            magnitudeSum += magnitude
        }
        
        val centroid = if (magnitudeSum > 0) weightedSum / magnitudeSum else 0f
        return (centroid / 8000f).coerceIn(0f, 1f)
    }
    
    // Performance control methods
    fun setProcessingEnabled(enabled: Boolean) {
        processingEnabled = enabled
    }
    
    fun setProcessingInterval(intervalMs: Long) {
        processingInterval = intervalMs
    }
    
    fun getDevicePerformance() = devicePerformance
    fun getOptimizedSettings() = optimizedSettings
}