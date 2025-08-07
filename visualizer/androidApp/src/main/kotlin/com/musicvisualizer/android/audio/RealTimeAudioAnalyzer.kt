package com.musicvisualizer.android.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.*

class RealTimeAudioAnalyzer : BaseAudioAnalyzer() {
    private var visualizer: Visualizer? = null
    private val captureSize = 1024

    // Psychoacoustically-optimized frequency ranges for better musical instrument separation
    // Based on 44.1kHz sampling rate, 1024 capture size (43.066 Hz per bin)
    private val bandRanges = arrayOf(
        1..3,      // ~43-129 Hz (Sub-bass) - Same coverage
        4..8,      // ~172-344 Hz (Bass) - More bins (was 4..6)
        9..15,     // ~388-646 Hz (Low-mid) - More bins (was 8..12)
        16..25,    // ~689-1076 Hz (Mid) - More bins (was 15..22)
        26..40,    // ~1120-1723 Hz (Upper-mid) - More bins (was 26..38)
        41..60,    // ~1766-2584 Hz (High-mid) - More bins (was 42..62)
        61..90,    // ~2627-3875 Hz (Presence) - Less bins (was 70..100)
        91..140    // ~3918-6026 Hz (Brilliance) - Much less bins (was 110..160)
    )

    private val processedBands = FloatArray(8)
    private val absoluteThresholds = floatArrayOf(
        8000f,   // Band 1: Sub-bass - KEEP AS IS (working perfectly)
        12000f,  // Band 2: Bass - Lower (was 15000f) for better response
        3200f,   // Band 3: Low-mid - Lower (was 4000f) for better response  
        1000f,   // Band 4: Mid - Lower (was 1200f) for better response
        600f,    // Band 5: Upper-mid - Lower (was 800f) for better response
        300f,    // Band 6: High-mid - Lower (was 400f) for better response
        200f,    // Band 7: Presence - Keep as is
        350f     // Band 8: Brilliance - Keep as is
    )
    private val smoothedBands = FloatArray(8)
    private val smoothingFactor = 0.15f
    private var lastPrintTime: Long? = null

    companion object {
        private const val TAG = "RealTimeAudioAnalyzer"
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
        lastPrintTime = null
    }

    private fun analyze8BandFFT(fft: ByteArray) {
        if (!running) return

        val magnitudes = convertFftToMagnitudes(fft)
        val rawBands = FloatArray(8) { i -> calculateBandEnergy(magnitudes, bandRanges[i]) }
        normalizeWithAbsoluteReference(rawBands)

        // Debug logging every 2 seconds
        val now = System.currentTimeMillis()
        if (lastPrintTime == null || now - lastPrintTime!! > 2000) {
            lastPrintTime = now
            val rawArrayString = rawBands.joinToString(", ") { "%.3f".format(it) }
            val processedArrayString = processedBands.joinToString(", ") { "%.3f".format(it) }
            Log.d(TAG, "RawBands: [$rawArrayString]")
            Log.d(TAG, "8-Band: [$processedArrayString]")
        }

        notifyListeners(AudioEvent(
            fftTest = processedBands.copyOf(),
            volume = processedBands.average().toFloat()
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

            // Apply power curve for better dynamics (especially useful for high-frequency bands)
            val poweredRatio = when (i) {
                6, 7 -> ratio.pow(0.7f)  // Compress bands 7-8 more gently
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