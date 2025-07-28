package com.musicvisualizer.android.visualizers

import android.opengl.GLES30
import android.opengl.GLSurfaceView.Renderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos

const val MAX_BUBBLES = 10

class BubbleVisualizer : Visualizer {
    override fun createRenderer(context: android.content.Context): Renderer = BubbleRenderer(context)

    private data class Bubble(
        var color: FloatArray,
        var radius: Float,
        var angle: Float,
        var angularVelocity: Float,
        var seed: Float,
        val clockwiseSpin: Boolean = Random.nextBoolean()
    ) {
        fun update(time: Float) {
            angularVelocity += (Random.nextFloat() - 1f) * 0.001f
            angularVelocity = angularVelocity.coerceIn(-0.02f..0.02f)
            angle += if (clockwiseSpin) angularVelocity else -angularVelocity
            radius = 0.07f + 0.02f * sin((time + seed * 1000f))
        }
    }

    private class BubbleRenderer(context: android.content.Context) : BaseVisualizerRenderer(context) {
        override val vertexShaderFile: String = "shaders/bubble-vertex.glsl"
        override val fragmentShaderFile: String = "shaders/bubble-fragment.glsl"

        private var resolutionHandle = 0
        private var timeHandle = 0
        private var numBubblesHandle = 0
        private var bubbleColorsHandle = 0
        private var bubbleRadiiHandle = 0
        private var bubblePositionsHandle = 0
        private var bubbleSeedsHandle = 0

        private val numBubbles = 6
        private val bubbles: List<Bubble>
        private val startTime = System.currentTimeMillis()
        private var lastDrawTimeMillis: Long = 0L
        private var cachedTime: Float = 0f
        private var lastTimeUpdate: Long = 0L

        private val bubbleColors = FloatArray(MAX_BUBBLES * 3)
        private val bubbleRadii = FloatArray(MAX_BUBBLES)
        private val bubblePositions = FloatArray(MAX_BUBBLES * 2)
        private val bubbleSeeds = FloatArray(MAX_BUBBLES)

        init {
            bubbles = List(numBubbles) {
                val angle = Random.nextFloat() * (2f * Math.PI).toFloat()
                val angularVelocity = (Random.nextFloat() - 0.5f) * 0.08f
                Bubble(
                    color = floatArrayOf(
                        .4f + Random.nextFloat() * (1f - .4f),
                        .4f + Random.nextFloat() * (1f - .4f),
                        .4f + Random.nextFloat() * (1f - .4f)
                    ),
                    radius = 0.07f,
                    angle = angle,
                    angularVelocity = angularVelocity,
                    seed = Random.nextFloat()
                )
            }
        }

        override fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {
            resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
            timeHandle = GLES30.glGetUniformLocation(program, "u_time")
            numBubblesHandle = GLES30.glGetUniformLocation(program, "u_numBubbles")
            bubbleColorsHandle = GLES30.glGetUniformLocation(program, "u_bubbleColors")
            bubbleRadiiHandle = GLES30.glGetUniformLocation(program, "u_bubbleRadii")
            bubblePositionsHandle = GLES30.glGetUniformLocation(program, "u_bubblePositions")
            bubbleSeedsHandle = GLES30.glGetUniformLocation(program, "u_bubbleSeeds")
        }

        override fun onDrawFrameExtras(gl: GL10?) {
            val nowMillis = System.currentTimeMillis()
            
            if (nowMillis - lastTimeUpdate > 16L) {
                cachedTime = (nowMillis - startTime) / 1000.0f
                lastTimeUpdate = nowMillis
            }
            
            if (lastDrawTimeMillis != 0L) {
                val elapsedMs = nowMillis - lastDrawTimeMillis
                if (elapsedMs < 16) return
            }
            lastDrawTimeMillis = nowMillis

            if (resolutionHandle >= 0) {
                GLES30.glUniform2f(resolutionHandle, width.toFloat(), height.toFloat())
            }

            if (timeHandle >= 0) {
                GLES30.glUniform1f(timeHandle, cachedTime)
            }

            if (numBubblesHandle >= 0) {
                GLES30.glUniform1f(numBubblesHandle, numBubbles.toFloat())
            }

            bubbles.forEach { it.update(cachedTime) }

            for (i in 0 until numBubbles) {
                val bubble = bubbles[i]
                val x = 0.5f + 0.18f * cos(bubble.angle)
                val y = 0.5f + 0.18f * sin(bubble.angle)

                bubbleColors[i * 3] = bubble.color[0]
                bubbleColors[i * 3 + 1] = bubble.color[1]
                bubbleColors[i * 3 + 2] = bubble.color[2]
                bubbleRadii[i] = bubble.radius
                bubblePositions[i * 2] = x
                bubblePositions[i * 2 + 1] = y
                bubbleSeeds[i] = bubble.seed
            }

            if (bubblePositionsHandle >= 0) {
                GLES30.glUniform2fv(bubblePositionsHandle, numBubbles, bubblePositions, 0)
            }
            if (bubbleColorsHandle >= 0) {
                GLES30.glUniform3fv(bubbleColorsHandle, numBubbles, bubbleColors, 0)
            }
            if (bubbleRadiiHandle >= 0) {
                GLES30.glUniform1fv(bubbleRadiiHandle, numBubbles, bubbleRadii, 0)
            }
            if (bubbleSeedsHandle >= 0) {
                GLES30.glUniform1fv(bubbleSeedsHandle, numBubbles, bubbleSeeds, 0)
            }

            val error = GLES30.glGetError()
            if (error != GLES30.GL_NO_ERROR) {
                println("OpenGL error in BubbleVisualizer: $error")
            }
        }
    }
}