package com.musicvisualizer.android.visualizers

import android.opengl.GLES30
import android.opengl.GLSurfaceView.Renderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random
import com.musicvisualizer.android.visualizers.Visualizer
import com.musicvisualizer.android.audio.AudioAnalyzer
import android.util.Log

/**
 * A placeholder visualizer that displays a blue background with a random color circle in the center using OpenGL ES 3.0.
 */
class CircleColorVisualizer : Visualizer {
    override fun createRenderer(context: android.content.Context, audioAnalyzer: com.musicvisualizer.android.audio.AudioAnalyzer): Renderer = CircleColorRenderer(context, audioAnalyzer)

    private class CircleColorRenderer(
        context: android.content.Context,
        private val audioAnalyzer: AudioAnalyzer
    ) : BaseVisualizerRenderer(context) {
        override val vertexShaderFile: String = "shaders/circle-vertex.glsl"
        override val fragmentShaderFile: String = "shaders/circle-fragment.glsl"
        private val circleColor = floatArrayOf(
            Random.nextFloat(),
            Random.nextFloat(),
            Random.nextFloat(),
            1.0f
        )
        private var colorHandle = 0
        private var resolutionHandle = 0
        private var centerHandle = 0
        private var radiusHandle = 0
        private var fftTestHandle = 0
        private var timeHandle = 0
        
        init {
            audioAnalyzer.addListener(this)
            audioAnalyzer.start()
        }
        override fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {
            colorHandle = GLES30.glGetUniformLocation(program, "u_color")
            resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
            centerHandle = GLES30.glGetUniformLocation(program, "u_center")
            radiusHandle = GLES30.glGetUniformLocation(program, "u_radius")
            fftTestHandle = GLES30.glGetUniformLocation(program, "u_fftTest")
            timeHandle = GLES30.glGetUniformLocation(program, "u_time")
            GLES30.glClearColor(0.1f, 0.1f, 0.8f, 1.0f)
        }
        override fun onDrawFrameExtras(gl: GL10?) {
            val time = System.nanoTime() / 1_000_000_000f
            
            GLES30.glUniform4fv(colorHandle, 1, circleColor, 0)
            GLES30.glUniform2f(resolutionHandle, width.toFloat(), height.toFloat())
            GLES30.glUniform2f(centerHandle, width / 2f, height / 2f)
            GLES30.glUniform1f(radiusHandle, 0.4f * minOf(width, height) / 2f)
            GLES30.glUniform1f(timeHandle, time)
            
            // Pass FFT data to shader
            latestAudioEvent?.fftTest?.let { fftData ->
                GLES30.glUniform1fv(fftTestHandle, fftData.size, fftData, 0)
            } ?: run {
                // Pass zero array if no FFT data
                val zeroArray = FloatArray(8) { 0f }
                GLES30.glUniform1fv(fftTestHandle, zeroArray.size, zeroArray, 0)
            }
        }
        
        override fun release() {
            super.release()
            audioAnalyzer.removeListener(this)
            audioAnalyzer.stop()
        }
    }
} 