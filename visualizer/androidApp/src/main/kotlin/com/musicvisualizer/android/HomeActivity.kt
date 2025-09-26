package com.musicvisualizer.android

import android.app.Activity
import android.media.MediaPlayer
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
// Removed alternate visualizer and swipe carousel
import com.musicvisualizer.android.R
import com.musicvisualizer.android.audio.AudioAnalyzer
import com.musicvisualizer.android.audio.RealTimeAudioAnalyzer
import android.os.Build
import android.view.WindowInsets
import android.opengl.GLSurfaceView.Renderer
import android.view.WindowManager
import android.util.Log

/**
 * GLSurfaceView that uses a modular Visualizer for rendering.
 */
class VisualizerGLSurfaceView(
    activity: Activity, 
    visualizer: Visualizer, 
    renderer: Renderer,
    audioAnalyzer: AudioAnalyzer
) : GLSurfaceView(activity) {
    init {
        // Set EGL context to OpenGL ES 3.0
        setEGLContextClientVersion(3)
        setRenderer(renderer)  // Use the passed renderer instead of creating a new one
    }
}

/**
 * HomeActivity serves as the main home screen for the Music Visualizer app.
 * It will host modular visualizer components, popups, menus, and navigation.
 */
class Home : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var visualizer: Visualizer
    // Swipe gesture removed
    private lateinit var renderer: Renderer
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioAnalyzer: AudioAnalyzer
    
    // Single visualizer
    private val bubbleVisualizer: Visualizer = BubbleVisualizer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize real-time audio analyzer
        audioAnalyzer = RealTimeAudioAnalyzer()
        
        visualizer = bubbleVisualizer
        renderer = visualizer.createRenderer(this, audioAnalyzer)
        glView = VisualizerGLSurfaceView(this, visualizer, renderer, audioAnalyzer)
        setContentView(glView)
        hideSystemUI()
        
        audioAnalyzer.start(0)
    }

    // No carousel or switching; bubble visualizer is fixed

    private fun hideSystemUI() {
        // Enable drawing behind cutout (notch) for true fullscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, glView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        audioAnalyzer.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}