package com.musicvisualizer.android.visualizers

import android.opengl.GLSurfaceView.Renderer
import android.opengl.GLES30
import com.musicvisualizer.android.audio.AudioAnalyzer
import com.musicvisualizer.android.util.PerformanceMonitor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class BubbleVisualizer : Visualizer {
    override fun createRenderer(context: android.content.Context, audioAnalyzer: AudioAnalyzer): Renderer = BubbleRenderer(context, audioAnalyzer)
}

/**
 * Optimized renderer with integrated performance monitoring and adaptive frame rate control
 */
class BubbleRenderer(
    private val context: android.content.Context,
    private val audioAnalyzer: AudioAnalyzer
) : BaseVisualizerRenderer(context) {
    override val vertexShaderFile = "shaders/bubble-vertex.glsl"
    override val fragmentShaderFile = "shaders/bubble-fragment.glsl"

    private val bubbleSystem = BubbleSystem(audioAnalyzer = audioAnalyzer)
    private val performanceMonitor = PerformanceMonitor()
    private var lastDrawTime = 0L

    // Uniform handles - cached to avoid repeated lookups
    private var resolutionHandle = 0
    private var timeHandle = 0
    private var numBubblesHandle = 0
    private var bubbleColorsHandle = 0
    private var bubbleRadiiHandle = 0
    private var bubblePositionsHandle = 0
    private var bubbleSeedsHandle = 0
    private var lightPositionHandle = 0

    override fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {
        // Cache uniform handles once during initialization
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
        
        // Adaptive frame rate limiting based on performance monitor
        val targetFrameTime = performanceMonitor.getTargetFrameTime()
        if (lastDrawTime != 0L && now - lastDrawTime < targetFrameTime) return
        lastDrawTime = now

        // Update bubble system with performance mode from monitor
        val currentTime = bubbleSystem.update(performanceMonitor.isPerformanceMode())
        val config = bubbleSystem.getConfig()

        // Set uniforms efficiently - only when handles are valid
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
        
        // Update performance monitor
        performanceMonitor.onFrameRendered()
    }

    fun cleanup() {
        bubbleSystem.cleanup()
        performanceMonitor.reset()
    }

    fun getCurrentAudioEvent() = bubbleSystem.getCurrentAudioEvent()
    
    fun getPerformanceStats() = Triple(
        performanceMonitor.getCurrentFps(),
        performanceMonitor.getAverageFrameTime(),
        performanceMonitor.isPerformanceMode()
    )

    private inline fun setUniform(handle: Int, setter: (Int) -> Unit) {
        if (handle >= 0) setter(handle)
    }

    private fun checkGLError() {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            android.util.Log.e("BubbleVisualizer", "OpenGL error: $error")
        }
    }
} 