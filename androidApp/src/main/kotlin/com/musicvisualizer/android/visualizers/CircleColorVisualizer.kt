package com.musicvisualizer.android.visualizers

import android.opengl.GLES30
import android.opengl.GLSurfaceView.Renderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random
import com.musicvisualizer.android.visualizers.Visualizer

/**
 * A placeholder visualizer that displays a blue background with a random color circle in the center using OpenGL ES 3.0.
 */
class CircleColorVisualizer : Visualizer {
    override fun createRenderer(context: android.content.Context): Renderer = CircleColorRenderer(context)

    private class CircleColorRenderer(context: android.content.Context) : BaseVisualizerRenderer(context) {
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
        override fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {
            colorHandle = GLES30.glGetUniformLocation(program, "u_color")
            resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
            centerHandle = GLES30.glGetUniformLocation(program, "u_center")
            radiusHandle = GLES30.glGetUniformLocation(program, "u_radius")
            GLES30.glClearColor(0.1f, 0.1f, 0.8f, 1.0f)
        }
        override fun onDrawFrameExtras(gl: GL10?) {
            GLES30.glUniform4fv(colorHandle, 1, circleColor, 0)
            GLES30.glUniform2f(resolutionHandle, width.toFloat(), height.toFloat())
            GLES30.glUniform2f(centerHandle, width / 2f, height / 2f)
            GLES30.glUniform1f(radiusHandle, 0.4f * minOf(width, height) / 2f)
        }
    }
} 