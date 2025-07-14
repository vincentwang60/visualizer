package com.musicvisualizer.android

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLSurfaceView.Renderer

class MainActivity : Activity() {
    private lateinit var glView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glView = HelloGLSurfaceView(this)
        setContentView(glView)
    }
}

class HelloGLSurfaceView(activity: Activity) : GLSurfaceView(activity) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(HelloGLRenderer())
    }
}

class HelloGLRenderer : Renderer {
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        gl.glClearColor(0.1f, 0.6f, 0.9f, 1.0f) // Light blue
    }

    override fun onDrawFrame(gl: GL10) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        gl.glViewport(0, 0, width, height)
    }
} 