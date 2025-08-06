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
import com.musicvisualizer.android.visualizers.CircleColorVisualizer
import com.musicvisualizer.android.VisualizerCarouselGestureListener
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
        setRenderer(visualizer.createRenderer(activity, audioAnalyzer))
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
    private lateinit var renderer: Renderer
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioAnalyzer: AudioAnalyzer
    
    private val visualizers: List<Visualizer> = listOf(
        BubbleVisualizer(),
        CircleColorVisualizer()
    )
    private var currentVisualizerIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize real-time audio analyzer
        audioAnalyzer = RealTimeAudioAnalyzer()
        
        visualizer = visualizers[currentVisualizerIndex]
        renderer = visualizer.createRenderer(this, audioAnalyzer)
        glView = VisualizerGLSurfaceView(this, visualizer, renderer, audioAnalyzer)
        setContentView(glView)
        hideSystemUI()
        
        // Start playing the MP3
        startAudioPlayback()
        
        gestureDetector = GestureDetector(this, VisualizerCarouselGestureListener(
            onSwipeLeft = { showNextVisualizer() },
            onSwipeRight = { showPreviousVisualizer() }
        ))

        glView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun startAudioPlayback() {
        mediaPlayer = MediaPlayer.create(this, R.raw.raven)
        mediaPlayer?.let { player ->
            player.isLooping = true
            player.setOnPreparedListener {
                player.start()
                audioAnalyzer.start(player.audioSessionId)
            }
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
        // Release previous renderer
        (renderer as? com.musicvisualizer.android.visualizers.BaseVisualizerRenderer)?.release()
        
        // Create new visualizer and renderer
        visualizer = visualizers[currentVisualizerIndex]
        renderer = visualizer.createRenderer(this, audioAnalyzer)
        
        // Remove old view and create new one
        val parent = glView.parent as? android.view.ViewGroup
        parent?.removeView(glView)
        glView = VisualizerGLSurfaceView(this, visualizer, renderer, audioAnalyzer)
        setContentView(glView)
        
        // Re-attach touch listener
        glView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

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