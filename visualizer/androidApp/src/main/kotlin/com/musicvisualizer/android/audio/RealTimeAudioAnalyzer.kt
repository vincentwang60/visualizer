package com.musicvisualizer.android.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.*

class RealTimeAudioAnalyzer : BaseAudioAnalyzer() {
    private var visualizer: Visualizer? = null
    private val captureSize = 1024
    private val BAND_COUNT = 8
    
    companion object {
        private const val TAG = "MusicViz-Audio"
    }
    
    private val minFreq = 20f
    private val maxFreq = 20000f
    private val sampleRate = 44100f
    private val bandMaxValues = floatArrayOf(20000f, 12000f, 8000f, 4000f, 1500f, 500f, 100f, 30f)
    
    private val bandRanges = calculateLogBands(minFreq, maxFreq, BAND_COUNT, sampleRate, captureSize)
    private val processedBands = FloatArray(BAND_COUNT)
    private val previousBandValues = FloatArray(BAND_COUNT)
    private var lastPrintTime: Long? = null
    
    private val spikeThreshold = 0.4f
    private val loudThreshold = 0.8f
    private val minSpikeInterval = 100L
    private val maxGlobalSpikes = 16
    private val globalWindowMs = 500L

    private val lastSpikeTime = LongArray(BAND_COUNT) { 0L }
    private val globalSpikeHistory = mutableListOf<Long>()
    private val totalSpikesPerChannel = LongArray(BAND_COUNT) { 0L }
    private var smoothedLowFreqEnergy = 0f
    private val lowFreqSmoothingFactor = 0.75f
    private var smoothedHighFreqEnergy = 0f
    private val highFreqSmoothingFactor = 0.75f
    private val bandSums = FloatArray(BAND_COUNT) { 0f }
    private val bandCounts = IntArray(BAND_COUNT) { 0 }
    private val bandAverages = FloatArray(BAND_COUNT) { 0f }

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
        previousBandValues.fill(0f)
        lastSpikeTime.fill(0L)
        globalSpikeHistory.clear()
        totalSpikesPerChannel.fill(0L)
        smoothedLowFreqEnergy = 0f
        smoothedHighFreqEnergy = 0f
        bandSums.fill(0f)
        bandCounts.fill(0)
        bandAverages.fill(0f)
        lastPrintTime = null
    }

    private fun analyze8BandFFT(fft: ByteArray) {
        if (!running) return
        val currentTime = System.currentTimeMillis()
        val rawBands = calculateBandEnergies(fft)
        for (i in 0..7) {
            val normalizedValue = if (rawBands[i] <= 0f) 0f else (rawBands[i] / bandMaxValues[i]).coerceIn(0f, 1f)
            processedBands[i] = normalizedValue
            
            // Track running averages
            bandSums[i] += normalizedValue
            bandCounts[i]++
            bandAverages[i] = bandSums[i] / bandCounts[i]
        }
        val detectedSpikes = detectSpikes(currentTime)
        val currentLowFreqEnergy = (processedBands[0] + processedBands[1] + processedBands[2] + processedBands[3]) / 4f
        smoothedLowFreqEnergy = smoothedLowFreqEnergy * lowFreqSmoothingFactor + currentLowFreqEnergy * (1f - lowFreqSmoothingFactor)
        val currentHighFreqEnergy = (processedBands[4] + processedBands[5] + processedBands[6] + processedBands[7]) / 4f
        smoothedHighFreqEnergy = smoothedHighFreqEnergy * highFreqSmoothingFactor + currentHighFreqEnergy * (1f - highFreqSmoothingFactor)
        debugLog(rawBands, currentTime)
        notifyListeners(AudioEvent(
            fftTest = processedBands.copyOf(),
            volume = processedBands.average().toFloat(),
            spikes = detectedSpikes,
            lowFrequencyEnergy = smoothedLowFreqEnergy,
            highFrequencyEnergy = smoothedHighFreqEnergy
        ))
    }



    private fun detectSpikes(currentTime: Long): MutableList<FrequencySpike> {
        globalSpikeHistory.removeAll { currentTime - it > globalWindowMs }
        val detectedSpikes = mutableListOf<FrequencySpike>()
        for (band in 0..BAND_COUNT - 1) {
            val currentValue = processedBands[band]
            val previousValue = previousBandValues[band]
            val timeSinceLastSpike = currentTime - lastSpikeTime[band]
            val shouldSpike = (timeSinceLastSpike >= minSpikeInterval) && (
                (currentValue > previousValue + spikeThreshold) ||
                (currentValue > loudThreshold)
            )
            if (shouldSpike && globalSpikeHistory.size < maxGlobalSpikes) {
                detectedSpikes.add(FrequencySpike(band, max(currentValue - previousValue, spikeThreshold), currentTime))
                lastSpikeTime[band] = currentTime
                globalSpikeHistory.add(currentTime)
                totalSpikesPerChannel[band]++
            }
            previousBandValues[band] = currentValue
        }
        return detectedSpikes
    }

    private fun debugLog(rawBands: FloatArray, currentTime: Long) {
        if (lastPrintTime == null || currentTime - lastPrintTime!! > 2000) {
            lastPrintTime = currentTime
            Log.d(TAG, "********************************************************")
            //Log.d(TAG, "Raw: [${rawBands.joinToString(", ") { "%.0f".format(it) }}]")
            Log.d(TAG, "Processed: [${processedBands.joinToString(", ") { "%.2f".format(it) }}]")
            Log.d(TAG, "Spikes: [${totalSpikesPerChannel.joinToString(", ") { it.toString() }}]")
            Log.d(TAG, "Global Spike History: [${globalSpikeHistory.size} of 16 limit]")
            Log.d(TAG, "Band Averages: [${bandAverages.joinToString(", ") { "%.2f".format(it) }}]")
            Log.d(TAG, "Smoothed High Freq Energy: [${smoothedHighFreqEnergy}]")
        }
    }

    private fun calculateLogBands(minFreq: Float, maxFreq: Float, numBands: Int, sampleRate: Float, fftSize: Int): Array<IntRange> {
        val binSize = (sampleRate / 2f) / (fftSize / 2)
        return Array(numBands) { i ->
            val logMin = ln(minFreq)
            val logMax = ln(maxFreq)
            val logStep = (ln(maxFreq) - ln(minFreq)) / numBands
            val freqStart = exp(logMin + i * logStep)
            val freqEnd = exp(logMin + (i + 1) * logStep)
            val binStart = (freqStart / binSize).roundToInt().coerceAtLeast(1)
            val binEnd = (freqEnd / binSize).roundToInt().coerceAtMost(fftSize / 2 - 1)
            binStart..binEnd
        }
    }

    private fun calculateBandEnergies(fft: ByteArray): FloatArray {
        val fftSize = fft.size / 2
        val rawBands = FloatArray(8)
        
        for (band in 0..7) {
            var energy = 0f
            var count = 0
            val range = bandRanges[band]
            
            for (i in range) {
                if (i < fftSize) {
                    val real = fft[i * 2].toFloat()
                    val imag = fft[i * 2 + 1].toFloat()
                    val magnitude = sqrt(real * real + imag * imag)
                    energy += magnitude * magnitude
                    count++
                }
            }
            rawBands[band] = if (count > 0) energy / count else 0f
        }
        return rawBands
    }
}