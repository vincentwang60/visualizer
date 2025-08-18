package com.musicvisualizer.android.visualizers

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView.Renderer
import com.musicvisualizer.android.audio.AudioEvent
import com.musicvisualizer.android.audio.AudioEventListener
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

interface Visualizer {
    fun createRenderer(context: Context, audioAnalyzer: com.musicvisualizer.android.audio.AudioAnalyzer): Renderer
}

abstract class BaseVisualizerRenderer(private val context: Context) : Renderer, AudioEventListener {

    protected var program: Int = 0
    protected var positionHandle: Int = 0
    protected var width: Int = 1
    protected var height: Int = 1
    protected var aspectRatio: Float = 1f

    private val squareCoords = floatArrayOf(-1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f)
    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

    protected val vertexBuffer: FloatBuffer = createFloatBuffer(squareCoords)
    protected val drawListBuffer: ShortBuffer = createShortBuffer(drawOrder)

    protected abstract val vertexShaderFile: String
    protected abstract val fragmentShaderFile: String

    // Audio analyzer integration
    protected var latestAudioEvent: AudioEvent? = null

    override fun onAudioEvent(event: AudioEvent) {
        latestAudioEvent = event
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertexSource = context.assets.open(vertexShaderFile).bufferedReader().use { it.readText() }
        val fragmentSource = context.assets.open(fragmentShaderFile).bufferedReader().use { it.readText() }

        program = createProgram(vertexSource, fragmentSource)
        positionHandle = GLES30.glGetAttribLocation(program, "vPosition")

        GLES30.glClearColor(0f, 0f, 0f, 1.0f)
        onSurfaceCreatedExtras(gl, config)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        this.width = width
        this.height = height
        this.aspectRatio = width.toFloat() / height.toFloat()
        onSurfaceChangedExtras(gl, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 8, vertexBuffer)

        onDrawFrameExtras(gl)

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, drawOrder.size, GLES30.GL_UNSIGNED_SHORT, drawListBuffer)
        GLES30.glDisableVertexAttribArray(positionHandle)
    }

    protected open fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {}
    protected open fun onSurfaceChangedExtras(gl: GL10?, width: Int, height: Int) {}
    protected open fun onDrawFrameExtras(gl: GL10?) {}

    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }

    private fun createShortBuffer(data: ShortArray): ShortBuffer {
        return ByteBuffer.allocateDirect(data.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(data); position(0) }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        return program
    }

    /**
     * Release OpenGL resources. Subclasses should override if they allocate additional resources.
     */
    open fun release() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }
}