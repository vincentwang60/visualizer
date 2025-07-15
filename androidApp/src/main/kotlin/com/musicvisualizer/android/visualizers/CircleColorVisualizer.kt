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
    override fun createRenderer(): Renderer = CircleColorRenderer()

    private class CircleColorRenderer : Renderer {
        private val circleColor = floatArrayOf(
            Random.nextFloat(),
            Random.nextFloat(),
            Random.nextFloat(),
            1.0f
        )
        private var program = 0
        private var positionHandle = 0
        private var colorHandle = 0
        private var resolutionHandle = 0
        private var centerHandle = 0
        private var radiusHandle = 0
        private var width = 0
        private var height = 0

        private val vertexShaderCode = """
            #version 300 es
            layout(location = 0) in vec4 vPosition;
            void main() {
                gl_Position = vPosition;
            }
        """

        private val fragmentShaderCode = """
            #version 300 es
            precision mediump float;
            uniform vec2 u_resolution;
            uniform vec2 u_center;
            uniform float u_radius;
            uniform vec4 u_color;
            out vec4 fragColor;
            void main() {
                vec2 st = (gl_FragCoord.xy / u_resolution);
                vec2 center = u_center / u_resolution;
                float dist = distance(st, center);
                if (dist < u_radius / min(u_resolution.x, u_resolution.y)) {
                    fragColor = u_color;
                } else {
                    fragColor = vec4(0.1, 0.1, 0.8, 1.0); // blue background
                }
            }
        """

        private val squareCoords = floatArrayOf(
            -1f,  1f, // top left
            -1f, -1f, // bottom left
             1f, -1f, // bottom right
             1f,  1f  // top right
        )
        private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)
        private val vertexBuffer = java.nio.ByteBuffer.allocateDirect(squareCoords.size * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(squareCoords)
                position(0)
            }
        private val drawListBuffer = java.nio.ByteBuffer.allocateDirect(drawOrder.size * 2)
            .order(java.nio.ByteOrder.nativeOrder()).asShortBuffer().apply {
                put(drawOrder)
                position(0)
            }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            program = createProgram(vertexShaderCode, fragmentShaderCode)
            positionHandle = GLES30.glGetAttribLocation(program, "vPosition")
            colorHandle = GLES30.glGetUniformLocation(program, "u_color")
            resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
            centerHandle = GLES30.glGetUniformLocation(program, "u_center")
            radiusHandle = GLES30.glGetUniformLocation(program, "u_radius")
            GLES30.glClearColor(0.1f, 0.1f, 0.8f, 1.0f)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glUseProgram(program)

            GLES30.glEnableVertexAttribArray(positionHandle)
            GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 2 * 4, vertexBuffer)

            GLES30.glUniform4fv(colorHandle, 1, circleColor, 0)
            GLES30.glUniform2f(resolutionHandle, width.toFloat(), height.toFloat())
            GLES30.glUniform2f(centerHandle, width / 2f, height / 2f)
            GLES30.glUniform1f(radiusHandle, 0.4f * minOf(width, height) / 2f)

            GLES30.glDrawElements(GLES30.GL_TRIANGLES, drawOrder.size, GLES30.GL_UNSIGNED_SHORT, drawListBuffer)
            GLES30.glDisableVertexAttribArray(positionHandle)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width
            this.height = height
            GLES30.glViewport(0, 0, width, height)
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            return GLES30.glCreateShader(type).also { shader ->
                GLES30.glShaderSource(shader, shaderCode)
                GLES30.glCompileShader(shader)
            }
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            return GLES30.glCreateProgram().also { program ->
                GLES30.glAttachShader(program, vertexShader)
                GLES30.glAttachShader(program, fragmentShader)
                GLES30.glLinkProgram(program)
            }
        }
    }
} 