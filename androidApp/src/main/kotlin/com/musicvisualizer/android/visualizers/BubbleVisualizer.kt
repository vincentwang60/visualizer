package com.musicvisualizer.android.visualizers

import android.opengl.GLES30
import android.opengl.GLSurfaceView.Renderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.random.Random
import com.musicvisualizer.android.visualizers.BaseVisualizerRenderer

private data class AnimationConfig(
    val speedRange: ClosedRange<Float> = 0.003f..0.006f,
    val amplitude: Float = 0.1f,
    val angularVelocityRange: ClosedRange<Float> = -0.1f..0.1f,
    val biasStrength: Float = 1f,
    val aspectRatio: Float = 1f, // width / height
    val accelerationStrength: Float = 0.00115f,
    val radiusRange: ClosedRange<Float> = 100f..150f
)

class BubbleVisualizer : Visualizer {
    override fun createRenderer(): Renderer = BubbleRenderer()
    private class Bubble(
        val color: FloatArray,
        var radius: Float,
        var position: FloatArray, // [x, y]
        var angle: Float,
        var speed: Float,
        var acceleration: Float = 0f,
        var angularVelocity: Float = 0f,
        val clockwiseSpin: Boolean = Random.nextBoolean()
    ) {
        fun update(config: AnimationConfig) {
            // Randomly tweak angular velocity and acceleration
            angularVelocity += (Random.nextFloat() - 0.5f) * 0.01f
            acceleration += (Random.nextFloat() - 0.5f) * config.accelerationStrength
            angularVelocity = angularVelocity.coerceIn(config.angularVelocityRange)
            
            // Bias toward center scaling with distance
            val dx = (0.5f - position[0]) * config.aspectRatio
            val dy = 0.5f - position[1]
            val distFromCenter = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distFromCenter > config.amplitude) {
                val biasForce = config.biasStrength * (distFromCenter - config.amplitude)
                val angleDiff = kotlin.math.atan2(dy, dx) - angle
                angle += kotlin.math.atan2(kotlin.math.sin(angleDiff), kotlin.math.cos(angleDiff)) * biasForce
            }
            
            angle += angularVelocity
            speed = (speed + acceleration).coerceIn(config.speedRange)
            position[0] += speed * kotlin.math.cos(angle) / config.aspectRatio
            position[1] += speed * kotlin.math.sin(angle)
        }
    }

    private class BubbleRenderer : BaseVisualizerRenderer() {
        override val vertexShaderCode = """
            #version 300 es
            layout(location = 0) in vec4 vPosition;
            void main() {
                gl_Position = vPosition;
            }
        """
        override val fragmentShaderCode = """
            #version 300 es
        precision highp float;

        uniform vec2 u_resolution;
        uniform float u_time;
        uniform int u_numBubbles;
        uniform float u_k;
        uniform float u_edgeThickness;
        uniform vec3 u_bgColorA;
        uniform vec3 u_bgColorB;
        uniform float u_hashSeed;
        uniform vec3 u_bubbleColors[6];
        uniform float u_bubbleRadii[6];
        uniform vec2 u_bubblePositions[6];

        out vec4 fragColor;

        float hash(float n) {
            return fract(sin(n + u_hashSeed) * 43758.5453);
        }

        float sdfCircle(vec2 p, vec2 center, float r) {
            return length(p - center) - r;
        }

        float smoothMin(float a, float b, float k) {
            float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
            return mix(b, a, h) - k * h * (1.0 - h);
        }

        // Calculate metaball field for gradient computation
        float metaballField(vec2 uv) {
            float field = 0.0;
            for (int i = 0; i < u_numBubbles; i++) {
                float fi = float(i);
                float radius = u_bubbleRadii[i];
                vec2 offset = vec2(hash(fi + 123.0) - 0.5, hash(fi + 456.0) - 0.5) * 2.0;
                vec2 center = u_bubblePositions[i] * u_resolution;
                vec2 pos = center + offset;
                float dist = max(length(uv - pos), 2.0);
                float strength = radius / dist;
                field += strength;
            }
            return field;
        }

        void main() {
            vec2 uv = gl_FragCoord.xy;
            vec2 norm = uv / u_resolution;
            
            // Animated background colors
            vec3 colorA = u_bgColorA + 0.2 * sin(u_time + vec3(0.0, 2.0, 4.0));
            vec3 colorB = u_bgColorB + 0.2 * cos(u_time + vec3(1.0, 3.0, 5.0));
            colorA = clamp(colorA, 0.0, 1.0);
            colorB = clamp(colorB, 0.0, 1.0);
            
            float t = clamp((norm.x + norm.y) * 0.5, 0.0, 1.0);
            vec3 background = mix(colorA, colorB, t);
            
            float field = 0.0;
            vec3 accumColor = vec3(0.0);
            float d = 10000.0;
            
            // Main metaball calculation with color accumulation
            for (int i = 0; i < u_numBubbles; i++) {
                float fi = float(i);
                float radius = u_bubbleRadii[i];
                vec3 color = u_bubbleColors[i];
                
                vec2 offset = vec2(hash(fi + 123.0) - 0.5, hash(fi + 456.0) - 0.5) * 2.0;
                vec2 center = u_bubblePositions[i] * u_resolution;
                vec2 pos = center + offset;
                float dist = max(length(uv - pos), 2.0);
                float strength = radius / dist;
                
                // Accumulate color based on strength
                accumColor += color * strength;
                field += strength;
                
                float di = sdfCircle(uv, pos, radius);
                d = smoothMin(d, di, u_k);
            }
            
            // Calculate final fill color from accumulated colors
            vec3 fillColor = accumColor / max(field, 1e-5);
            
            // Calculate gradient for lighting effects
            float eps = 1.0;
            float fx = metaballField(uv + vec2(eps, 0.0)) - field;
            float fy = metaballField(uv + vec2(0.0, eps)) - field;
            vec2 grad = normalize(vec2(fx, fy));
            
            // Use fillColor (bubble colors) for specular highlights
            float specular = pow(max(dot(grad, normalize(vec2(0.5, 0.7))), 0.0), 20.0);
            float shadow = pow(max(dot(grad, normalize(vec2(-0.5, -0.7))), 0.0), 10.0) * 0.4;
            
            // Blend everything together - sharper edges and more vibrant colors
            float fillMask = smoothstep(1.0, 1.4, field); // Sharper edge transition
            float outline = smoothstep(0.0, u_edgeThickness * 0.5, abs(d)); // Thinner outline
            
            vec3 interior = background * 0.4 + fillColor * 0.6 + fillColor * specular * 0.8; // More vibrant
            vec3 color = mix(background, interior, fillMask);
            color = mix(color * (1.0 - shadow * 0.6), background, outline); // Reduced shadow
            
            fragColor = vec4(color, 1.0);
        }
        """
        private var resolutionHandle = 0
        private var timeHandle = 0
        private var numBubblesHandle = 0
        private var kHandle = 0
        private var edgeThicknessHandle = 0
        private var bgColorAHandle = 0
        private var bgColorBHandle = 0
        private var hashSeedHandle = 0
        private var bubbleColorsHandle = 0
        private var bubbleRadiiHandle = 0
        private var bubblePositionsHandle = 0
        private var startTime = System.nanoTime()
        private var lastDrawTimeNanos: Long = 0L
        private val numBubbles = 6
        private val k = 40f
        private val edgeThickness = 8f
        private val bgColorA = floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
        private val bgColorB = floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
        private val hashSeed = 42f
        private val bubbles: List<Bubble>
        init {
            val aspectRatio = 1f // placeholder, will be set per-frame
            val config = AnimationConfig(aspectRatio = aspectRatio)
            bubbles = List(numBubbles) {
                val angle = Random.nextFloat() * (2f * Math.PI).toFloat()
                val speed = 0.002f + Random.nextFloat() * 0.004f
                val angularVelocity = (Random.nextFloat() - 0.5f) * 0.08f
                val r = 0.1f * kotlin.math.sqrt(Random.nextFloat())
                val theta = Random.nextFloat() * (2f * Math.PI).toFloat()
                val posX = 0.5f + r * kotlin.math.cos(theta)
                val posY = 0.5f + r * kotlin.math.sin(theta)
                Bubble(
                    color = floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()),
                    radius = Random.nextFloat() * (config.radiusRange.endInclusive - config.radiusRange.start) + config.radiusRange.start,
                    position = floatArrayOf(posX, posY),
                    angle = angle,
                    speed = speed,
                    angularVelocity = angularVelocity
                )
            }
        }
        override fun onSurfaceCreatedExtras(gl: GL10?, config: EGLConfig?) {
            program = createProgram(vertexShaderCode, fragmentShaderCode)
            positionHandle = GLES30.glGetAttribLocation(program, "vPosition")
            resolutionHandle = GLES30.glGetUniformLocation(program, "u_resolution")
            timeHandle = GLES30.glGetUniformLocation(program, "u_time")
            numBubblesHandle = GLES30.glGetUniformLocation(program, "u_numBubbles")
            kHandle = GLES30.glGetUniformLocation(program, "u_k")
            edgeThicknessHandle = GLES30.glGetUniformLocation(program, "u_edgeThickness")
            bgColorAHandle = GLES30.glGetUniformLocation(program, "u_bgColorA")
            bgColorBHandle = GLES30.glGetUniformLocation(program, "u_bgColorB")
            hashSeedHandle = GLES30.glGetUniformLocation(program, "u_hashSeed")
            bubbleColorsHandle = GLES30.glGetUniformLocation(program, "u_bubbleColors")
            bubbleRadiiHandle = GLES30.glGetUniformLocation(program, "u_bubbleRadii")
            bubblePositionsHandle = GLES30.glGetUniformLocation(program, "u_bubblePositions")
        }
        override fun onDrawFrameExtras(gl: GL10?) {
            val nowNanos = System.nanoTime()
            if (lastDrawTimeNanos != 0L) {
                val elapsedMs = (nowNanos - lastDrawTimeNanos) / 1_000_000
                if (elapsedMs < 33) return
            }
            lastDrawTimeNanos = nowNanos
            GLES30.glUniform2f(resolutionHandle, width.toFloat(), height.toFloat())
            val time = (System.nanoTime() - startTime) / 1_000_000_000.0f
            GLES30.glUniform1f(timeHandle, time)
            GLES30.glUniform1i(numBubblesHandle, numBubbles)
            GLES30.glUniform1f(kHandle, k)
            GLES30.glUniform1f(edgeThicknessHandle, edgeThickness)
            GLES30.glUniform3fv(bgColorAHandle, 1, bgColorA, 0)
            GLES30.glUniform3fv(bgColorBHandle, 1, bgColorB, 0)
            GLES30.glUniform1f(hashSeedHandle, hashSeed)
            val aspectRatio = width.toFloat() / height.toFloat()
            val config = AnimationConfig(aspectRatio = aspectRatio)
            for (bubble in bubbles) bubble.update(config)
            val bubblePositions = FloatArray(numBubbles * 2) { i -> bubbles[i / 2].position[i % 2] }
            GLES30.glUniform2fv(bubblePositionsHandle, numBubbles, bubblePositions, 0)
            val bubbleColors = FloatArray(numBubbles * 3) { i -> bubbles[i / 3].color[i % 3] }
            val bubbleRadii = FloatArray(numBubbles) { i -> bubbles[i].radius }
            GLES30.glUniform3fv(bubbleColorsHandle, numBubbles, bubbleColors, 0)
            GLES30.glUniform1fv(bubbleRadiiHandle, numBubbles, bubbleRadii, 0)
        }
    }
} 