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
        20000f,  // Sub-bass: Higher to prevent maxing
        35000f,  // Bass: Much higher - was hitting ceiling at 25000
        30000f,  // Low-mid: Higher for better range
        10000f,  // Mid: Slightly higher
        6000f,   // Upper-mid: Slightly higher
        1500f,   // High-mid: Higher for better range
        1200f,   // Presence: Higher to prevent clamping
        1000f    // Brilliance: Higher to prevent clamping
    )
    
    // Noise floor thresholds for silence detection
    // Based on quiet sections showing values like [276, 2007, ...] 
    private val noiseFloorThresholds = floatArrayOf(
        150f,    // Sub-bass noise floor
        400f,    // Bass noise floor  
        300f,    // Low-mid noise floor
        150f,    // Mid noise floor
        80f,     // Upper-mid noise floor
        40f,     // High-mid noise floor
        30f,     // Presence noise floor
        25f      // Brilliance noise floor
    )

    // Peak tracking for dynamic scaling
    private val recentPeaks = Array(8) { mutableListOf<Float>() }
    private val maxPeakSamples = 200  // ~20 seconds of peak history
    
    // Temporal smoothing for reducing jitter
    private val smoothedBands = FloatArray(8)
    private val smoothingFactor = 0.25f  // Balanced for responsiveness and smoothness
    
    // Adaptive scaling factors
    private val adaptiveScales = FloatArray(8) { 1.0f }
    private val scaleAdaptRate = 0.03f  // Slightly faster adaptation
    
    // Dynamic range expansion
    private val minOutputLevel = 0.02f  // Minimum visible level
    private val dynamicRangeExpansion = 1.2f  // Expansion factor for better contrast

    // Volume detection from waveform
    private var currentWaveformVolume = 0f
    private var waveformVolumeHistory = mutableListOf<Float>()
    private val maxVolumeHistorySamples = 50
    
    // Improved silence detection
    private var silenceFrameCount = 0
    private val silenceThreshold = 0.02f  // Volume below this = silence
    private val silenceFramesBeforeReset = 10  // Frames of silence before resetting
    private var fadeOutDetected = false
    private val fadeOutThreshold = 0.1f  // Volume threshold for fade detection

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
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    waveform?.let { analyzeWaveform(it) }
                }
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    fft?.let { analyze8BandFFT(it, samplingRate) }
                }
            }, Visualizer.getMaxCaptureRate(), true, true)  // Enable both waveform and FFT
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
        currentWaveformVolume = 0f
        waveformVolumeHistory.clear()
        fadeOutDetected = false
        lastPrintTime = null
    }
    
    private fun analyzeWaveform(waveform: ByteArray) {
        if (!running) return
        
        // Calculate RMS volume from waveform
        var sum = 0.0
        for (byte in waveform) {
            // Convert from signed byte to normalized float (-1 to 1)
            val sample = byte / 128.0
            sum += sample * sample
        }
        val rms = sqrt(sum / waveform.size).toFloat()
        
        // Update volume history
        currentWaveformVolume = rms
        waveformVolumeHistory.add(rms)
        if (waveformVolumeHistory.size > maxVolumeHistorySamples) {
            waveformVolumeHistory.removeAt(0)
        }
        
        // Detect fade-out
        if (waveformVolumeHistory.size >= 10) {
            val recentAvg = waveformVolumeHistory.takeLast(10).average().toFloat()
            val olderAvg = waveformVolumeHistory.take(10).average().toFloat()
            
            // If recent volume is much lower than older volume, we're fading out
            if (recentAvg < fadeOutThreshold && olderAvg > fadeOutThreshold * 2) {
                fadeOutDetected = true
            }
        }
    }

    private fun analyze8BandFFT(fft: ByteArray, samplingRate: Int) {
        if (!running) return

        val magnitudes = convertFftToMagnitudes(fft)
        val rawBands = FloatArray(8) { i -> calculateBandEnergy(magnitudes, bandRanges[i]) }
        
        // Enhanced silence detection using waveform volume
        val isSilent = detectSilenceWithVolume(rawBands)
        
        if (isSilent || fadeOutDetected) {
            // Rapid decay during silence or fade-out
            for (i in 0..7) {
                smoothedBands[i] *= if (fadeOutDetected) 0.7f else 0.85f
                processedBands[i] = if (smoothedBands[i] < minOutputLevel) 0f else smoothedBands[i]
            }
            
            // If we've been silent for a while, ensure everything goes to zero
            if (silenceFrameCount > 20) {
                processedBands.fill(0f)
                smoothedBands.fill(0f)
            }
        } else {
            // Normal processing with improved normalization
            normalizeWithAdaptiveScaling(rawBands)
        }

        // Apply volume-based scaling
        if (currentWaveformVolume < fadeOutThreshold) {
            // Scale down all bands based on actual volume
            val volumeScale = (currentWaveformVolume / fadeOutThreshold).coerceIn(0f, 1f)
            for (i in 0..7) {
                processedBands[i] *= volumeScale
            }
        }

        // Debug logging
        val now = System.currentTimeMillis()
        if (lastPrintTime == null || now - lastPrintTime!! > 500) {
            lastPrintTime = now
            val rawArrayString = rawBands.joinToString(", ") { "%.3f".format(it) }
            val processedArrayString = processedBands.joinToString(", ") { "%.3f".format(it) }
            Log.d(TAG, "RawBands: [$rawArrayString]")
            Log.d(TAG, "8-Band: [$processedArrayString]")
            if (currentWaveformVolume < 0.1f) {
                Log.d(TAG, "WaveformVolume: %.4f (LOW)".format(currentWaveformVolume))
            }
            if (isSilent) {
                Log.d(TAG, "Status: SILENT (frame $silenceFrameCount)")
            }
            if (fadeOutDetected) {
                Log.d(TAG, "Status: FADE-OUT DETECTED")
            }
        }
        
        val volume = processedBands.average().toFloat()
        
        notifyListeners(AudioEvent(
            fftTest = processedBands.copyOf(), 
            volume = currentWaveformVolume,  // Use actual waveform volume
        ))
    }
    
    private fun detectSilenceWithVolume(rawBands: FloatArray): Boolean {
        // First check waveform volume
        if (currentWaveformVolume < silenceThreshold) {
            silenceFrameCount++
            return true
        }
        
        // Then check FFT-based detection
        var activeBands = 0
        var totalEnergyAboveFloor = 0f
        
        for (i in 0..7) {
            if (rawBands[i] > noiseFloorThresholds[i]) {
                activeBands++
                totalEnergyAboveFloor += rawBands[i] - noiseFloorThresholds[i]
            }
        }
        
        // Consider it silent if:
        // 1. Waveform volume is very low, OR
        // 2. Less than 3 bands are active, OR
        // 3. Total energy above floor is very low
        val isSilent = currentWaveformVolume < 0.05f || 
                      activeBands < 3 || 
                      totalEnergyAboveFloor < 500f
        
        if (isSilent) {
            silenceFrameCount++
            if (silenceFrameCount > silenceFramesBeforeReset) {
                // Reset adaptive scales during extended silence
                adaptiveScales.fill(1.0f)
            }
        } else {
            silenceFrameCount = 0
            fadeOutDetected = false  // Reset fade-out detection when sound returns
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
                smoothedBands[i] *= 0.85f
                processedBands[i] = if (smoothedBands[i] < minOutputLevel) 0f else smoothedBands[i]
                continue
            }
            
            // Calculate adaptive scale based on recent peaks
            if (recentPeaks[i].isNotEmpty()) {
                val recentMax = recentPeaks[i].maxOrNull() ?: absoluteThresholds[i]
                val recentAvg = recentPeaks[i].average().toFloat()
                // Use a combination of max and average for better scaling
                val effectiveMax = recentMax * 0.7f + recentAvg * 0.3f
                val targetScale = absoluteThresholds[i] / max(effectiveMax, absoluteThresholds[i] * 0.3f)
                adaptiveScales[i] = adaptiveScales[i] * (1f - scaleAdaptRate) + targetScale * scaleAdaptRate
            }
            
            // Apply normalization with adaptive scaling
            val effectiveThreshold = absoluteThresholds[i] * adaptiveScales[i]
            var normalizedValue = cleanedValue / effectiveThreshold
            
            // Apply improved compression curve for better dynamic range
            normalizedValue = when {
                normalizedValue < 0.05f -> normalizedValue * 3f     // Boost very quiet signals more
                normalizedValue < 0.2f -> 0.15f + (normalizedValue - 0.05f) * 2f  // Moderate boost
                normalizedValue < 0.5f -> 0.45f + (normalizedValue - 0.2f) * 1.2f  // Gentle expansion
                normalizedValue < 0.8f -> 0.81f + (normalizedValue - 0.5f) * 0.5f  // Mild compression
                normalizedValue < 1.2f -> 0.96f + (normalizedValue - 0.8f) * 0.1f  // Strong compression
                else -> 1.0f  // Hard limit
            }
            
            // Apply frequency-specific weighting for jazz music
            val frequencyWeight = when(i) {
                0 -> 0.8f    // Reduce sub-bass weight
                1 -> 0.9f    // Reduce bass slightly to prevent ceiling
                2 -> 1.0f    // Full weight for low-mids
                3 -> 1.1f    // Boost mids slightly
                4 -> 1.15f   // Boost upper-mids for clarity
                5 -> 1.0f    // Normal high-mids
                6 -> 0.85f   // Reduce presence to prevent clamping
                else -> 0.8f // Reduce ultra-highs
            }
            
            normalizedValue *= frequencyWeight
            
            // Apply dynamic range expansion for better contrast
            if (normalizedValue > 0.1f && normalizedValue < 0.9f) {
                // Expand the middle range for better visual distinction
                val midPoint = 0.5f
                val distance = normalizedValue - midPoint
                normalizedValue = midPoint + distance * dynamicRangeExpansion
            }
            
            normalizedValue = normalizedValue.coerceIn(0f, 1f)
            
            // Apply temporal smoothing with adaptive rate
            // Less smoothing for rapid changes, more for gradual ones
            val changeDelta = abs(normalizedValue - smoothedBands[i])
            val adaptiveSmoothingFactor = if (changeDelta > 0.3f) {
                smoothingFactor * 1.5f  // Faster response to big changes
            } else {
                smoothingFactor
            }
            
            smoothedBands[i] = smoothedBands[i] * (1f - adaptiveSmoothingFactor) + normalizedValue * adaptiveSmoothingFactor
            processedBands[i] = smoothedBands[i]
        }
        
        // Post-processing: Prevent bands from getting stuck at similar values
        preventBandClamping()
    }
    
    private fun preventBandClamping() {
        // Check if multiple bands are stuck at similar high values
        val highBands = processedBands.count { it > 0.85f }
        if (highBands >= 3) {
            // Apply slight variation to prevent visual monotony
            for (i in 0..7) {
                if (processedBands[i] > 0.85f) {
                    // Add slight variation based on band index
                    val variation = (i * 0.02f) - 0.08f
                    processedBands[i] = (processedBands[i] + variation).coerceIn(0.7f, 1f)
                }
            }
        }
        
        // Ensure high-frequency bands don't get stuck
        for (i in 5..7) {
            if (processedBands[i] > 0.88f && processedBands[i] < 0.92f) {
                // Add some variation to prevent clamping around 0.9
                val variation = sin(System.currentTimeMillis() * 0.001f + i) * 0.05f
                processedBands[i] = (processedBands[i] + variation).coerceIn(0f, 1f)
            }
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