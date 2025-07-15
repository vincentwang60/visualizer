package com.musicvisualizer.android.visualizers

import android.opengl.GLES30
import android.opengl.GLSurfaceView.Renderer
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Visualizer interface for modular visualizer implementations.
 */
interface Visualizer {
    fun createRenderer(): Renderer
}

abstract class BaseVisualizerRenderer : Renderer {
    
    // OpenGL handles
    protected var program: Int = 0
    protected var positionHandle: Int = INVALID_HANDLE
    
    // Screen dimensions
    protected var width: Int = 1
    protected var height: Int = 1
    
    // Vertex data for full-screen quad
    private val squareCoords = floatArrayOf(
        -1f,  1f, // top left
        -1f, -1f, // bottom left
         1f, -1f, // bottom right
         1f,  1f  // top right
    )
    
    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)
    
    // Buffers (lazy initialization for better performance)
    protected val vertexBuffer: FloatBuffer by lazy {
        createFloatBuffer(squareCoords)
    }
    
    protected val drawListBuffer: ShortBuffer by lazy {
        createShortBuffer(drawOrder)
    }
    
    // Abstract shader code - must be implemented by subclasses
    protected abstract val vertexShaderCode: String
    protected abstract val fragmentShaderCode: String
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            program = createProgram(vertexShaderCode, fragmentShaderCode)
            positionHandle = GLES30.glGetAttribLocation(program, "vPosition")
            
            if (positionHandle == INVALID_HANDLE) {
                Log.w(TAG, "vPosition attribute not found in shader")
            }
            
            GLES30.glClearColor(0f, 0f, 0f, 1.0f)
            onSurfaceCreatedExtras(gl, config)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during surface creation", e)
        }
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        this.width = width
        this.height = height
        onSurfaceChangedExtras(gl, width, height)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        
        setupVertexAttributes()
        onDrawFrameExtras(gl)
        renderQuad()
        cleanupVertexAttributes()
    }
    
    // Extension points for subclasses
    protected open fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {}
    protected open fun onSurfaceChangedExtras(gl: GL10?, width: Int, height: Int) {}
    protected open fun onDrawFrameExtras(gl: GL10?) {}
    
    // Private helper methods
    private fun setupVertexAttributes() {
        if (positionHandle != INVALID_HANDLE) {
            GLES30.glEnableVertexAttribArray(positionHandle)
            GLES30.glVertexAttribPointer(
                positionHandle, 
                COORDS_PER_VERTEX, 
                GLES30.GL_FLOAT, 
                false, 
                VERTEX_STRIDE, 
                vertexBuffer
            )
        }
    }
    
    private fun renderQuad() {
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES, 
            drawOrder.size, 
            GLES30.GL_UNSIGNED_SHORT, 
            drawListBuffer
        )
    }
    
    private fun cleanupVertexAttributes() {
        if (positionHandle != INVALID_HANDLE) {
            GLES30.glDisableVertexAttribArray(positionHandle)
        }
    }
    
    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }
    
    private fun createShortBuffer(data: ShortArray): ShortBuffer {
        return ByteBuffer.allocateDirect(data.size * SHORT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(data)
                position(0)
            }
    }
    
    protected fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Error creating shader of type $type")
        }
        
        // Clean and validate shader code
        val cleanedShaderCode = cleanShaderCode(shaderCode)
        Log.d(TAG, "Loading ${if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"} shader:\n$cleanedShaderCode")
        
        GLES30.glShaderSource(shader, cleanedShaderCode)
        GLES30.glCompileShader(shader)
        
        // Check compilation status
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation failed:\n$error")
            Log.e(TAG, "Shader source was:\n$cleanedShaderCode")
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Error compiling shader: $error")
        }
        
        return shader
    }
    
    private fun cleanShaderCode(shaderCode: String): String {
        // Remove any leading/trailing whitespace and ensure proper line endings
        val lines = shaderCode.trim().split('\n').map { it.trim() }
        
        // Check if #version directive exists and is properly placed
        val versionLineIndex = lines.indexOfFirst { it.startsWith("#version") }
        
        if (versionLineIndex == -1) {
            // Add OpenGL ES 3.0 version directive if missing
            return "#version 300 es\n${lines.joinToString("\n")}"
        } else if (versionLineIndex > 0) {
            // Move #version to first line if it's not already there
            val versionLine = lines[versionLineIndex]
            val otherLines = lines.filterIndexed { index, _ -> index != versionLineIndex }
            return "$versionLine\n${otherLines.joinToString("\n")}"
        }
        
        // #version is already at the correct position
        return lines.joinToString("\n")
    }
    
    protected fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        
        val program = GLES30.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Error creating program")
        }
        
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        
        // Check linking status
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Error linking program: $error")
        }
        
        // Clean up shaders (they're now linked into the program)
        GLES30.glDetachShader(program, vertexShader)
        GLES30.glDetachShader(program, fragmentShader)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        
        return program
    }
    
    // Cleanup method for proper resource management
    protected fun cleanup() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }
    
    companion object {
        private const val TAG = "BaseVisualizerRenderer"
        private const val INVALID_HANDLE = -1
        private const val COORDS_PER_VERTEX = 2
        private const val FLOAT_SIZE_BYTES = 4
        private const val SHORT_SIZE_BYTES = 2
        private const val VERTEX_STRIDE = COORDS_PER_VERTEX * FLOAT_SIZE_BYTES
    }
}