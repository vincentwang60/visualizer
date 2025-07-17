package com.musicvisualizer.android.visualizers

import android.opengl.GLES30
import android.opengl.GLSurfaceView.Renderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random
import com.musicvisualizer.android.visualizers.BaseVisualizerRenderer
import kotlin.math.min

const val MAX_BUBBLES = 10

private data class AnimationConfig(
    val eccentricity: Float = 0.001f,
    val amplitude: Float = 0.1f,
    val angularVelocityRange: ClosedRange<Float> = -0.02f..0.02f,
    val aspectRatio: Float = 1f, // width / height
    val radiusRange: ClosedRange<Float> = 0.03f..0.05f
)

class BubbleVisualizer : Visualizer {
    override fun createRenderer(context: android.content.Context): Renderer = BubbleRenderer(context)
    private class Bubble(
        var color: FloatArray,
        var amplitude: Float,
        var amplitudeBias: Float,
        var radius: Float, // normalized to 0-1
        var angle: Float,
        var angularVelocity: Float,
        val clockwiseSpin: Boolean = Random.nextBoolean(),
        val seed: Float = Random.nextFloat()
    ) {
        fun update(config: AnimationConfig, time: Float) {
            angularVelocity += (Random.nextFloat() - 1f) * config.eccentricity
            angularVelocity = angularVelocity.coerceIn(config.angularVelocityRange)
            angle += if (clockwiseSpin) angularVelocity else -angularVelocity
            amplitudeBias += (kotlin.math.sin(time + angle) - 0.5f) * config.eccentricity
            amplitude = config.amplitude + amplitudeBias
            radius = (config.radiusRange.start + config.radiusRange.endInclusive) / 2f +
                (config.radiusRange.endInclusive - config.radiusRange.start) / 2f *
                kotlin.math.sin((time + seed * 1000f))
        }
    }

    private class BubbleRenderer(context: android.content.Context) : BaseVisualizerRenderer(context) {
        override val vertexShaderFile: String = "shaders/bubble-vertex.glsl"
        override val fragmentShaderFile: String = "shaders/bubble-fragment.glsl"
        private var resolutionHandle = 0
        private var timeHandle = 0
        private var numBubblesHandle = 0
        private var edgeThicknessHandle = 0
        private var bubbleColorsHandle = 0
        private var bubbleRadiiHandle = 0
        private var bubblePositionsHandle = 0
        
        private val edgeThickness = 8f
        private val numBubbles = 6
        private val bubbles: List<Bubble>
        private val startTime = System.nanoTime()
        private var lastDrawTimeNanos: Long = 0L // Added for frame limiting
        init {
            val config = AnimationConfig(aspectRatio = aspectRatio)
            bubbles = List(numBubbles) {
                val angle = Random.nextFloat() * (2f * Math.PI).toFloat()
                val angularVelocity = (Random.nextFloat() - 0.5f) * 0.08f
                Bubble(
                    color = floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()),
                    radius = 0f,
                    angle = angle,
                    angularVelocity = angularVelocity,
                    amplitude = config.amplitude,
                    amplitudeBias = 0f
                )
            }
        }
        override fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {
            // program and positionHandle are already set by the base class
            resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
            timeHandle = GLES30.glGetUniformLocation(program, "u_time")
            edgeThicknessHandle = GLES30.glGetUniformLocation(program, "u_edgeThickness")
            bubbleColorsHandle = GLES30.glGetUniformLocation(program, "u_bubbleColors")
            bubbleRadiiHandle = GLES30.glGetUniformLocation(program, "u_bubbleRadii")
            bubblePositionsHandle = GLES30.glGetUniformLocation(program, "u_bubblePositions")
            numBubblesHandle = GLES30.glGetUniformLocation(program, "u_numBubbles")
        }
        override fun onDrawFrameExtras(gl: GL10?) {
            val nowNanos = System.nanoTime()
            if (lastDrawTimeNanos != 0L) {
                val elapsedMs = (nowNanos - lastDrawTimeNanos) / 1_000_000
                if (elapsedMs < 15) return // FPS cap
            }
            lastDrawTimeNanos = nowNanos
            GLES30.glUniform2f(resolutionHandle, width.toFloat(), height.toFloat())
            val time = (System.nanoTime() - startTime) / 1_000_000_000.0f
            GLES30.glUniform1f(timeHandle, time)
            GLES30.glUniform1i(numBubblesHandle, numBubbles)
            GLES30.glUniform1f(edgeThicknessHandle, edgeThickness)
            val config = AnimationConfig(aspectRatio = aspectRatio)
            for (bubble in bubbles) bubble.update(config, time)
            val bubblePositions = bubbles.flatMap { b -> listOf(
                width / 2f + b.radius * min(width, height) * kotlin.math.cos(b.angle),
                height / 2f + b.radius * min(width, height) * kotlin.math.sin(b.angle)
            )}.toFloatArray()
            val bubbleColors = bubbles.flatMap { it.color.asList() }.toFloatArray()
            GLES30.glUniform2fv(bubblePositionsHandle, numBubbles, bubblePositions, 0)
            GLES30.glUniform3fv(bubbleColorsHandle, numBubbles, bubbleColors, 0)
            val bubbleRadii = FloatArray(numBubbles) { i -> bubbles[i].radius * width }
            GLES30.glUniform1fv(bubbleRadiiHandle, numBubbles, bubbleRadii, 0)
        }
    }
} 