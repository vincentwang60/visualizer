package com.musicvisualizer.android.visualizers

import android.opengl.GLSurfaceView.Renderer
import android.opengl.GLES30
import com.musicvisualizer.android.audio.AudioAnalyzer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class BubbleVisualizer : Visualizer {
    override fun createRenderer(context: android.content.Context, audioAnalyzer: AudioAnalyzer): Renderer = BubbleRenderer(context, audioAnalyzer)
}

private const val FRAME_TIME_NS = 16_000_000L // 16ms in nanoseconds

/**
 * Wrapper/connector class that handles OpenGL uniforms and boilerplate
 */
class BubbleRenderer(
    private val context: android.content.Context,
    private val audioAnalyzer: AudioAnalyzer
) : BaseVisualizerRenderer(context) {
    override val vertexShaderFile = "shaders/bubble-vertex.glsl"
    override val fragmentShaderFile = "shaders/bubble-fragment.glsl"

    private val bubbleSystem = BubbleSystem(audioAnalyzer = audioAnalyzer)
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

    override fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {
        resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
        timeHandle = GLES30.glGetUniformLocation(program, "u_time")
        numBubblesHandle = GLES30.glGetUniformLocation(program, "u_numBubbles")
        bubbleColorsHandle = GLES30.glGetUniformLocation(program, "u_bubbleColors")
        bubbleRadiiHandle = GLES30.glGetUniformLocation(program, "u_bubbleRadii")
        bubblePositionsHandle = GLES30.glGetUniformLocation(program, "u_bubblePositions")
        bubbleSeedsHandle = GLES30.glGetUniformLocation(program, "u_bubbleSeeds")
        lightPositionHandle = GLES30.glGetUniformLocation(program, "u_lightPosition")
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
        setUniform(lightPositionHandle) { GLES30.glUniform2f(it, config.lightPosition.first, config.lightPosition.second) }

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
        if (now - lastPrintTime > 30_000_000_000L) {
            bubbleSystem.printDebugInfo(currentTime, width.toFloat(), height.toFloat())
            lastPrintTime = now
        }
        if (now - lastPrintTime > 30_000_000L) {
            bubbleSystem.printTemp(currentTime, width.toFloat(), height.toFloat())
            lastPrintTime = now
        }
    }

    fun cleanup() {
        bubbleSystem.cleanup()
    }

    fun getCurrentAudioEvent() = bubbleSystem.getCurrentAudioEvent()

    private inline fun setUniform(handle: Int, setter: (Int) -> Unit) {
        if (handle >= 0) setter(handle)
    }

    private fun checkGLError() {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            println("OpenGL error in BubbleVisualizer: $error")
        }
    }
} 