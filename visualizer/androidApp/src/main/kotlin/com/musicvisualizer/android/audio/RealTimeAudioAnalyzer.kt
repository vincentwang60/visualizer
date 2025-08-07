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

    // 8-band frequency ranges
    private val bandRanges = arrayOf(
        1..4,      // ~20-86 Hz (Sub-bass)
        5..8,      // ~86-172 Hz (Bass)
        9..16,     // ~172-344 Hz (Low-mid)
        17..32,    // ~344-688 Hz (Mid)
        33..64,    // ~688-1376 Hz (Upper-mid)
        65..128,   // ~1376-2752 Hz (Presence)
        129..256,  // ~2752-5504 Hz (Brilliance)
        257..512   // ~5504-11008 Hz (Air)
    )

    // Strategy-specific state variables
    private val processedBands = FloatArray(8)
    
    private val recentHistory = Array(8) { mutableListOf<Float>() }
    private val maxRecentSamples = 50  // ~5 seconds at 10fps

    private var lastPrintTime: Long? = null
    
    companion object {
        private const val TAG = "RealTimeAudioAnalyzer"
    }

    override fun start(vararg params: Any) {
        if (running) return
        val audioSessionId = params[0] as Int

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
        recentHistory.forEach { it.clear() }
        lastPrintTime = null
    }

    private fun analyze8BandFFT(fft: ByteArray, samplingRate: Int) {
        if (!running) return

        val magnitudes = convertFftToMagnitudes(fft)
        val rawBands = FloatArray(8) { i -> calculateBandEnergy(magnitudes, bandRanges[i]) }
        normalizeWithPercentile(rawBands)

        // Only print debug output if at least 500ms have passed since last print
        val now = System.currentTimeMillis()
        if (lastPrintTime == null || now - lastPrintTime!! > 500) {
            lastPrintTime = now
            val arrayString = processedBands.joinToString(", ") { "%.3f".format(it) }
            Log.d(TAG, "8-Band: [$arrayString]")
        }
        if (lastPrintTime == null || now - lastPrintTime!! > 500) {
            lastPrintTime = now
            val arrayString = processedBands.joinToString(", ") { "%.3f".format(it) }
            Log.d(TAG, "8-Band: [$arrayString]")
        }
        
        val volume = processedBands.average().toFloat()
        
        notifyListeners(AudioEvent(
            fftTest = processedBands.copyOf(), 
            volume = volume,
        ))
    }

    private fun normalizeWithPercentile(rawBands: FloatArray) {
        for (i in 0..7) {
            // Maintain recent history
            recentHistory[i].add(rawBands[i])
            if (recentHistory[i].size > maxRecentSamples) {
                recentHistory[i].removeAt(0)
            }
            
            // Handle silence case first
            if (rawBands[i] <= 0f) {
                processedBands[i] = 0f
                continue
            }
            
            // Use 95th percentile as reference instead of max
            val referenceValue = if (recentHistory[i].size >= 10) {
                val sorted = recentHistory[i].sorted()
                val percentileIndex = (sorted.size * 0.95f).toInt().coerceIn(0, sorted.size - 1)
                val percentileValue = sorted[percentileIndex]
                // Ensure reference value is not zero to avoid division by zero
                if (percentileValue > 0f) percentileValue else 1f
            } else {
                rawBands[i].coerceAtLeast(1f)
            }
            
            // Normalize with slight compression
            val ratio = rawBands[i] / referenceValue
            processedBands[i] = (ratio.pow(0.7f)).coerceIn(0f, 1f)
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
        return energy
    }
}