package com.musicvisualizer.android.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.*

class RealTimeAudioAnalyzer : BaseAudioAnalyzer() {
    private var visualizer: Visualizer? = null
    private val captureSize = 1024

    private val bandRanges = arrayOf(
        0..3,      // Sub-bass: ~43-129 Hz
        4..7,      // Bass: ~172-301 Hz
        8..13,     // Low-mid: ~345-560 Hz
        14..29,    // Mid: ~603-1249 Hz
        30..40,    // Upper-mid: ~1292-1723 Hz
        41..60,    // High-mid: ~1766-2584 Hz
        61..90,    // Presence: ~2627-3876 Hz
        91..140    // Brilliance: ~3919-6029 Hz
    )

    private val processedBands = FloatArray(8)
    private val absoluteThresholds = floatArrayOf(
        4000f, 8000f, 2000f, 400f, 600f, 300f, 100f, 150f
    )
    private val smoothedBands = FloatArray(8)
    private val smoothingFactor = 0.15f
    private var lastPrintTime: Long? = null
    
    // Spike detection
    private val previousBandValues = FloatArray(8)
    private val baseSpikeThreshold = 0.2f // All bands use 0.2f base threshold
    private val minSpikeInterval = 100L // 100ms per band
    private val lastSpikeTime = LongArray(8) { 0L }
    
    // Dynamic threshold scaling based on recent activity
    private val recentSpikeHistory = Array(8) { mutableListOf<Long>() }
    private val activityWindowMs = 5000L // 5 second window for activity tracking
    private val targetSpikesPerWindow = floatArrayOf(8f, 4f, 6f, 5f, 8f, 7f, 4f, 3f) // Target spikes per 5-second window
    
    // Global rate limiting (max 16 spikes per 2 seconds)
    private val globalSpikeHistory = mutableListOf<Long>()
    private val maxGlobalSpikes = 16
    private val globalWindowMs = 500L
    
    // Debug: spike counting per channel
    private val totalSpikesPerChannel = LongArray(8) { 0L }

    companion object {
        private const val TAG = "MusicViz-Audio"
    }

    override fun start(vararg params: Any) {
        if (running) return
        val audioSessionId = if (params.isNotEmpty()) params[0] as Int else 0

        visualizer = Visualizer(audioSessionId).apply {
            captureSize = this@RealTimeAudioAnalyzer.captureSize
            scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    fft?.let { analyze8BandFFT(it) }
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
    }

    private fun resetState() {
        processedBands.fill(0f)
        smoothedBands.fill(0f)
        previousBandValues.fill(0f)
        lastSpikeTime.fill(0L)
        globalSpikeHistory.clear()
        lastPrintTime = null
        totalSpikesPerChannel.fill(0L)
        recentSpikeHistory.forEach { it.clear() }
    }

    private fun analyze8BandFFT(fft: ByteArray) {
        if (!running) return

        val currentTime = System.currentTimeMillis()

        val magnitudes = convertFftToMagnitudes(fft)
        val rawBands = FloatArray(8) { i -> calculateBandEnergy(magnitudes, bandRanges[i]) }
        normalizeWithAbsoluteReference(rawBands)
        
        // Clean old entries from global history and recent spike history
        globalSpikeHistory.removeAll { currentTime - it > globalWindowMs }
        for (band in 0..7) {
            recentSpikeHistory[band].removeAll { currentTime - it > activityWindowMs }
        }
        
        val detectedSpikes = mutableListOf<FrequencySpike>()
        
        for (band in 0..7) {
            val currentValue = processedBands[band]
            val previousValue = previousBandValues[band]
            val timeSinceLastSpike = currentTime - lastSpikeTime[band]
            
            // Calculate dynamic threshold based on recent activity
            val recentSpikeCount = recentSpikeHistory[band].size
            val targetSpikes = targetSpikesPerWindow[band]
            val activityRatio = recentSpikeCount / targetSpikes
            
            // Much more aggressive scaling based on global spike activity
            val globalActivity = globalSpikeHistory.size / 16f
            val scaling = when {
                globalActivity < 0.4f -> 0.3f   // Quiet: drop to 40% threshold  
                globalActivity < 0.6f -> 0.5f   // Moderately quiet: drop to 60%
                globalActivity < 0.8f -> 0.7f   // Getting active: 80%
                globalActivity > 0.85f -> 1.3f  // Active: raise to 130%
                globalActivity > 0.95f -> 1.5f  // Very active: raise to 150%
                else -> 1.0f // Normal activity
            }
            
            val shouldSpike = (timeSinceLastSpike >= minSpikeInterval) && (
                (currentValue > baseSpikeThreshold &&
                 currentValue > previousValue + 0.05f * scaling) ||
                (currentValue > baseSpikeThreshold * 5.0f * scaling)
            )            
            if (shouldSpike) {                               
                // Check global rate limit before adding spike
                if (globalSpikeHistory.size < maxGlobalSpikes) {
                    detectedSpikes.add(FrequencySpike(
                        bandIndex = band,
                        intensity = currentValue,
                        timestamp = currentTime
                    ))
                    lastSpikeTime[band] = currentTime
                    globalSpikeHistory.add(currentTime)
                    totalSpikesPerChannel[band]++ // Increment spike count for this channel
                    recentSpikeHistory[band].add(currentTime) // Add to recent history for dynamic scaling
                    val spikeReason = if (currentValue > 0.6f) "HIGH" else "NORM"
                    //Log.d(TAG, "SPIKE: Band$band:${"%.2f".format(currentValue)} thresh:${"%.3f".format(dynamicThreshold)} ($spikeReason) (progress: ${globalSpikeHistory.size} of 16 limit)")
                } else {
                    // Rate limit hit - log but don't send spike
                    Log.d(TAG, "Global spike rate limit reached - dropping spike for band $band (intensity: ${"%.2f".format(currentValue)})")
                }
            }
            
            previousBandValues[band] = currentValue
        }
        
        val lowFreqEnergy = (processedBands[0] + processedBands[1] + processedBands[2] + processedBands[3]) / 4f

        // Debug logging every 2 seconds
        val now = System.currentTimeMillis()
        if (lastPrintTime == null || now - lastPrintTime!! > 2000) {
            lastPrintTime = now
            val rawArrayString = rawBands.joinToString(", ") { "%.3f".format(it) }
            val processedArrayString = processedBands.joinToString(", ") { "%.3f".format(it) }
            val spikeCountsString = totalSpikesPerChannel.joinToString(", ")
            // Log.d(TAG, "RawBands: [$rawArrayString]")
            Log.d(TAG, "8-Band: [$processedArrayString]")
            Log.d(TAG, "Total Spikes Per Channel: [$spikeCountsString]")
            Log.d(TAG, "Global Spike History: [${globalSpikeHistory.size} of 16 limit]")
        }

        notifyListeners(AudioEvent(
            fftTest = processedBands.copyOf(),
            volume = processedBands.average().toFloat(),
            spikes = detectedSpikes,
            lowFrequencyEnergy = lowFreqEnergy
        ))
    }

    private fun normalizeWithAbsoluteReference(rawBands: FloatArray) {
        for (i in 0..7) {
            if (rawBands[i] <= 0f) {
                processedBands[i] = 0f
                smoothedBands[i] = smoothedBands[i] * 0.9f
                continue
            }

            val ratio = rawBands[i] / absoluteThresholds[i]

            val poweredRatio = when (i) {
                6, 7 -> ratio.pow(0.7f)  // Compress high-frequency bands
                else -> ratio
            }

            val normalizedValue = poweredRatio.coerceIn(0f, 1f)
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
        var count = 0
        for (i in range) {
            if (i < magnitudes.size) {
                energy += magnitudes[i] * magnitudes[i]
                count++
            }
        }
        return if (count > 0) energy / count else 0f
    }
}