package com.musicvisualizer.android

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.musicvisualizer.android.visualizers.Visualizer
import com.musicvisualizer.android.visualizers.BubbleVisualizer
import com.musicvisualizer.android.visualizers.CircleColorVisualizer
import com.musicvisualizer.android.VisualizerCarouselGestureListener
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager

/**
 * GLSurfaceView that uses a modular Visualizer for rendering.
 */
class VisualizerGLSurfaceView(activity: Activity, visualizer: Visualizer) : GLSurfaceView(activity) {
    init {
        // Set EGL context to OpenGL ES 3.0
        setEGLContextClientVersion(3)
        setRenderer(visualizer.createRenderer())
    }
}

/**
 * HomeActivity serves as the main home screen for the Music Visualizer app.
 * It will host modular visualizer components, popups, menus, and navigation.
 */
class Home : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var visualizer: Visualizer
    private lateinit var gestureDetector: GestureDetector
    private val visualizers = listOf(
        BubbleVisualizer(),
        CircleColorVisualizer()
    )
    private var currentVisualizerIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable drawing behind cutout (notch) for true fullscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        visualizer = visualizers[currentVisualizerIndex]
        glView = VisualizerGLSurfaceView(this, visualizer)
        setContentView(glView)

        hideSystemUI()

        gestureDetector = GestureDetector(this, VisualizerCarouselGestureListener(
            onSwipeLeft = { showNextVisualizer() },
            onSwipeRight = { showPreviousVisualizer() }
        ))

        glView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun showNextVisualizer() {
        currentVisualizerIndex = (currentVisualizerIndex + 1) % visualizers.size
        updateVisualizer()
    }

    private fun showPreviousVisualizer() {
        currentVisualizerIndex = if (currentVisualizerIndex - 1 < 0) visualizers.size - 1 else currentVisualizerIndex - 1
        updateVisualizer()
    }

    private fun updateVisualizer() {
        visualizer = visualizers[currentVisualizerIndex]
        val parent = glView.parent as? android.view.ViewGroup
        parent?.removeView(glView)
        glView = VisualizerGLSurfaceView(this, visualizer)
        setContentView(glView)
        hideSystemUI()
        glView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, glView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // Immersive sticky for true fullscreen
        if (Build.VERSION.SDK_INT < 30) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
} 