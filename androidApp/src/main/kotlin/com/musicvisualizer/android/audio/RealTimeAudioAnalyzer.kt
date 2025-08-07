package com.musicvisualizer.android.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.*

/**
 * Configuration for frequency band analysis
 */
class RealTimeAudioAnalyzer : BaseAudioAnalyzer() {
    private var visualizer: Visualizer? = null
    private val captureSize = 1024
    
    // Improved frequency band ranges for better musical representation
    // Based on FFT bin calculation: bin_freq = (bin_index * sample_rate) / fft_size
    // Assuming 44.1kHz sample rate and 1024 FFT size, each bin ≈ 43Hz
    private val bandRanges = arrayOf(
        1..2,      // ~43-86 Hz (Sub-bass) - reduced range for cleaner sub
        3..6,      // ~86-258 Hz (Bass) - focused bass fundamentals
        7..14,     // ~258-602 Hz (Low-mid) - warmth and body
        15..30,    // ~602-1290 Hz (Mid) - main musical content
        31..62,    // ~1290-2666 Hz (Upper-mid) - presence and clarity
        63..127,   // ~2666-5462 Hz (High-mid) - brilliance
        128..255,  // ~5462-10968 Hz (Presence) - air and sparkle
        256..400   // ~10968-17200 Hz (Brilliance) - ultra-highs
    )

    // Strategy-specific state variables
    private val processedBands = FloatArray(8)
    
    private val recentHistory = Array(8) { mutableListOf<Float>() }
    private val maxRecentSamples = 100  // ~10 seconds at 10fps for better range
    
    // Recalibrated thresholds based on your log data analysis
    // These values are tuned for jazz/acoustic music with proper silence detection
    // Based on Oncle Jazz logs showing values like: [4963, 19266, 26692, 2943, 422, 129, 174, 208]
    private val absoluteThresholds = floatArrayOf(
        15000f,  // Sub-bass: Lower than bass to prevent constant maxing
        25000f,  // Bass: Based on ~20-40k peaks in logs
        20000f,  // Low-mid: Based on ~20-30k peaks
        8000f,   // Mid: Lower threshold for main content
        5000f,   // Upper-mid: Much lower based on logs
        1000f,   // High-mid: Very low based on logs showing ~100-200
        800f,    // Presence: Very low for air
        600f     // Brilliance: Lowest for ultra-highs
    )
    
    // Noise floor thresholds for silence detection
    // Based on quiet sections showing values like [276, 2007, ...] 
    private val noiseFloorThresholds = floatArrayOf(
        200f,    // Sub-bass noise floor
        500f,    // Bass noise floor  
        400f,    // Low-mid noise floor
        200f,    // Mid noise floor
        100f,    // Upper-mid noise floor
        50f,     // High-mid noise floor
        40f,     // Presence noise floor
        30f      // Brilliance noise floor
    )
    
    // Peak tracking for dynamic scaling
    private val recentPeaks = Array(8) { mutableListOf<Float>() }
    private val maxPeakSamples = 200  // ~20 seconds of peak history
    
    // Temporal smoothing for reducing jitter
    private val smoothedBands = FloatArray(8)
    private val smoothingFactor = 0.2f  // Slightly higher for more responsive feel
    
    // Adaptive scaling factors
    private val adaptiveScales = FloatArray(8) { 1.0f }
    private val scaleAdaptRate = 0.02f  // How quickly scales adapt
    
    // Silence detection
    private var silenceFrameCount = 0
    private val silenceThreshold = 0.01f  // Average band level below this = silence
    private val silenceFramesBeforeReset = 10  // Frames of silence before resetting

    private var lastPrintTime: Long? = null
    
    companion object {
        private const val TAG = "RealTimeAudioAnalyzer"
    }

    override fun start(vararg params: Any) {
        if (running) return
        val audioSessionId = if (params.isNotEmpty()) {
            params[0] as Int
        } else {
            0 // Default audio session ID
        }

        visualizer = Visualizer(audioSessionId).apply {
            captureSize = this@RealTimeAudioAnalyzer.captureSize
            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    fft?.let { analyze8BandFFT(it, samplingRate) }
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            enabled = true
        }

        running = true
    }

    override fun stop() {
        running = false
        visualizer?.apply { enabled = false; release() }
        visualizer = null
        resetState()
        Log.d(TAG, "Stopped")
    }

    private fun resetState() {
        processedBands.fill(0f)
        smoothedBands.fill(0f)
        recentHistory.forEach { it.clear() }
        recentPeaks.forEach { it.clear() }
        adaptiveScales.fill(1.0f)
        silenceFrameCount = 0
        lastPrintTime = null
    }

    private fun analyze8BandFFT(fft: ByteArray, samplingRate: Int) {
        if (!running) return

        val magnitudes = convertFftToMagnitudes(fft)
        val rawBands = FloatArray(8) { i -> calculateBandEnergy(magnitudes, bandRanges[i]) }
        
        // Check for silence first
        val isSilent = detectSilence(rawBands)
        
        if (isSilent) {
            // Decay values smoothly during silence
            for (i in 0..7) {
                smoothedBands[i] *= 0.85f  // Faster decay during silence
                processedBands[i] = smoothedBands[i]
            }
        } else {
            // Normal processing with improved normalization
            normalizeWithAdaptiveScaling(rawBands)
        }

        // Debug logging
        val now = System.currentTimeMillis()
        if (lastPrintTime == null || now - lastPrintTime!! > 500) {
            lastPrintTime = now
            val rawArrayString = rawBands.joinToString(", ") { "%.3f".format(it) }
            val processedArrayString = processedBands.joinToString(", ") { "%.3f".format(it) }
            Log.d(TAG, "RawBands: [$rawArrayString]")
            Log.d(TAG, "8-Band: [$processedArrayString]")
            if (isSilent) {
                Log.d(TAG, "Status: SILENT (frame $silenceFrameCount)")
            }
        }
        
        val volume = processedBands.average().toFloat()
        
        notifyListeners(AudioEvent(
            fftTest = processedBands.copyOf(), 
            volume = volume,
        ))
    }
    
    private fun detectSilence(rawBands: FloatArray): Boolean {
        // Count how many bands are above their noise floor
        var activeBands = 0
        var totalEnergyAboveFloor = 0f
        
        for (i in 0..7) {
            if (rawBands[i] > noiseFloorThresholds[i]) {
                activeBands++
                totalEnergyAboveFloor += rawBands[i] - noiseFloorThresholds[i]
            }
        }
        
        // Consider it silent if:
        // 1. Less than 3 bands are active, OR
        // 2. Total energy above floor is very low
        val isSilent = activeBands < 3 || totalEnergyAboveFloor < 500f
        
        if (isSilent) {
            silenceFrameCount++
            if (silenceFrameCount > silenceFramesBeforeReset) {
                // Reset adaptive scales during extended silence
                adaptiveScales.fill(1.0f)
            }
        } else {
            silenceFrameCount = 0
        }
        
        return isSilent
    }

    private fun normalizeWithAdaptiveScaling(rawBands: FloatArray) {
        for (i in 0..7) {
            // Maintain recent history
            recentHistory[i].add(rawBands[i])
            if (recentHistory[i].size > maxRecentSamples) {
                recentHistory[i].removeAt(0)
            }
            
            // Track peaks for adaptive scaling
            if (rawBands[i] > noiseFloorThresholds[i]) {
                recentPeaks[i].add(rawBands[i])
                if (recentPeaks[i].size > maxPeakSamples) {
                    recentPeaks[i].removeAt(0)
                }
            }
            
            // Subtract noise floor first
            val cleanedValue = max(0f, rawBands[i] - noiseFloorThresholds[i])
            
            if (cleanedValue <= 0f) {
                // Below noise floor - rapid decay
                smoothedBands[i] *= 0.9f
                processedBands[i] = smoothedBands[i]
                continue
            }
            
            // Calculate adaptive scale based on recent peaks
            if (recentPeaks[i].isNotEmpty()) {
                val recentMax = recentPeaks[i].maxOrNull() ?: absoluteThresholds[i]
                val targetScale = absoluteThresholds[i] / max(recentMax, absoluteThresholds[i] * 0.5f)
                adaptiveScales[i] = adaptiveScales[i] * (1f - scaleAdaptRate) + targetScale * scaleAdaptRate
            }
            
            // Apply normalization with adaptive scaling
            val effectiveThreshold = absoluteThresholds[i] * adaptiveScales[i]
            var normalizedValue = cleanedValue / effectiveThreshold
            
            // Apply compression curve for better dynamic range
            normalizedValue = when {
                normalizedValue < 0.1f -> normalizedValue * 2f  // Boost very quiet signals
                normalizedValue < 0.5f -> 0.2f + (normalizedValue - 0.1f) * 1.5f  // Gentle boost
                normalizedValue < 1.0f -> 0.8f + (normalizedValue - 0.5f) * 0.4f  // Compress peaks
                else -> 1.0f  // Hard limit
            }
            
            // Apply frequency-specific weighting for jazz music
            val frequencyWeight = when(i) {
                0 -> 0.7f   // Reduce sub-bass weight
                1 -> 0.85f  // Slightly reduce bass
                2 -> 1.0f   // Full weight for low-mids
                3 -> 1.1f   // Boost mids slightly
                4 -> 1.2f   // Boost upper-mids for clarity
                5 -> 1.0f   // Normal high-mids
                6 -> 0.9f   // Slightly reduce presence
                else -> 0.8f // Reduce ultra-highs
            }
            
            normalizedValue *= frequencyWeight
            normalizedValue = normalizedValue.coerceIn(0f, 1f)
            
            // Apply temporal smoothing
            smoothedBands[i] = smoothedBands[i] * (1f - smoothingFactor) + normalizedValue * smoothingFactor
            processedBands[i] = smoothedBands[i]
        }
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
        return energy  // Return raw energy, not RMS - RMS was reducing values too much
    }
}