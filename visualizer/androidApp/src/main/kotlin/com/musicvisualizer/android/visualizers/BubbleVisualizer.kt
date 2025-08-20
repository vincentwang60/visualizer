package com.musicvisualizer.android.visualizers

import android.opengl.GLSurfaceView.Renderer
import android.opengl.GLES30
import android.util.Log
import com.musicvisualizer.android.audio.AudioAnalyzer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class BubbleVisualizer : Visualizer {
    override fun createRenderer(context: android.content.Context, audioAnalyzer: AudioAnalyzer): Renderer = BubbleRenderer(context, audioAnalyzer)
}

private const val FRAME_TIME_NS = 16_000_000L // 16ms in nanoseconds
private const val TAG = "MusicViz-BubbleRender"

/**
 * Wrapper/connector class that handles OpenGL uniforms and boilerplate
 */
class BubbleRenderer(
    private val context: android.content.Context,
    private val audioAnalyzer: AudioAnalyzer
) : BaseVisualizerRenderer(context) {
    override val vertexShaderFile = "shaders/bubble-vertex.glsl"
    override val fragmentShaderFile = "shaders/bubble-fragment.glsl"

    private val bubbleSystem = BubbleSystem()
    private var lastDrawTime = 0L
    private var lastPrintTime = 0L

    // Uniform handles
    private var resolutionHandle = 0
    private var timeHandle = 0
    private var numBubblesHandle = 0
    private var bubbleColorsHandle = 0
    private var bubbleRadiiHandle = 0
    private var bubblePositionsHandle = 0
    private var bubbleSeedsHandle = 0
    private var lightPositionHandle = 0
    private var tiltHandle = 0
    private var chromaticAberrationHandle = 0
    private var strobeHandle = 0
    private var fftHandle = 0
    private var smoothEnergyHandle = 0
    init {
        audioAnalyzer.addListener(bubbleSystem)
    }

    override fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {
        resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
        timeHandle = GLES30.glGetUniformLocation(program, "u_time")
        numBubblesHandle = GLES30.glGetUniformLocation(program, "u_numBubbles")
        bubbleColorsHandle = GLES30.glGetUniformLocation(program, "u_bubbleColors")
        bubbleRadiiHandle = GLES30.glGetUniformLocation(program, "u_bubbleRadii")
        bubblePositionsHandle = GLES30.glGetUniformLocation(program, "u_bubblePositions")
        bubbleSeedsHandle = GLES30.glGetUniformLocation(program, "u_bubbleSeeds")
        lightPositionHandle = GLES30.glGetUniformLocation(program, "u_lightPosition")
        tiltHandle = GLES30.glGetUniformLocation(program, "u_tilt")
        chromaticAberrationHandle = GLES30.glGetUniformLocation(program, "u_chromaticAberration")
        strobeHandle = GLES30.glGetUniformLocation(program, "u_strobe")
        fftHandle = GLES30.glGetUniformLocation(program, "u_fft")
        smoothEnergyHandle = GLES30.glGetUniformLocation(program, "u_smoothEnergy")
    }

    override fun onDrawFrameExtras(gl: GL10?) {
        val now = System.nanoTime()
        
        // Frame rate limiting
        if (lastDrawTime != 0L && now - lastDrawTime < FRAME_TIME_NS) return
        lastDrawTime = now

        // Update bubble system
        val currentTime = bubbleSystem.update()
        val config = bubbleSystem.getConfig()

        // Set basic uniforms
        setUniform(resolutionHandle) { GLES30.glUniform2f(it, width.toFloat(), height.toFloat()) }
        setUniform(timeHandle) { GLES30.glUniform1f(it, currentTime) }
        setUniform(numBubblesHandle) { GLES30.glUniform1f(it, bubbleSystem.getBubbleCount().toFloat()) }
        setUniform(lightPositionHandle) { GLES30.glUniform2f(it, bubbleSystem.getLightPosition().first, bubbleSystem.getLightPosition().second) }
        setUniform(tiltHandle) { GLES30.glUniform1f(it, bubbleSystem.getTilt()) } // Reduced tilt intensity to 0.05 radians
        setUniform(chromaticAberrationHandle) { GLES30.glUniform1f(it, bubbleSystem.getChromaticAberration()) }
        setUniform(strobeHandle) { GLES30.glUniform1f(it, bubbleSystem.getStrobe()) }
        setUniform(fftHandle) { GLES30.glUniform1fv(it, 8, bubbleSystem.getFft(), 0) }
        setUniform(smoothEnergyHandle) { GLES30.glUniform1f(it, bubbleSystem.getSmoothEnergy()) }
        // Set array uniforms using bubble system data
        setUniform(bubblePositionsHandle) { 
            GLES30.glUniform2fv(it, bubbleSystem.getBubbleCount(), bubbleSystem.bubblePositions, 0) 
        }
        setUniform(bubbleColorsHandle) { 
            GLES30.glUniform3fv(it, bubbleSystem.getBubbleCount(), bubbleSystem.bubbleColors, 0) 
        }
        setUniform(bubbleRadiiHandle) { 
            GLES30.glUniform1fv(it, bubbleSystem.getBubbleCount(), bubbleSystem.bubbleRadii, 0) 
        }
        setUniform(bubbleSeedsHandle) { 
            GLES30.glUniform1fv(it, bubbleSystem.getBubbleCount(), bubbleSystem.bubbleSeeds, 0) 
        }

        checkGLError()
        
        // Print debug info every 30 seconds
        if (now - lastPrintTime > 10_000_000_000L) {
            //bubbleSystem.printDebugInfo(currentTime, width.toFloat(), height.toFloat())
            lastPrintTime = now
        }
    }

    override fun release() {
        super.release()
        audioAnalyzer.removeListener(bubbleSystem)
    }

    private inline fun setUniform(handle: Int, setter: (Int) -> Unit) {
        if (handle >= 0) setter(handle)
    }

    private fun checkGLError() {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "OpenGL error in BubbleVisualizer: $error")
        }
    }
} 