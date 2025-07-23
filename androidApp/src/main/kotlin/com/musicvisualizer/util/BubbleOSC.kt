package com.musicvisualizer.util

import kotlin.math.*
import kotlin.random.Random
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BubbleOSC(
    private val host: String = "localhost",
    private val port: Int = 8000,
    private val numBubbles: Int = 6
) {
    private val socket = DatagramSocket()
    private val bubbles = List(numBubbles) {
        Bubble(
            color = floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()),
            radius = 0f,
            angle = Random.nextFloat() * (2f * PI).toFloat(),
            angularVelocity = (Random.nextFloat() - 0.5f) * 0.08f,
            seed = Random.nextFloat()
        )
    }
    private val startTime = System.nanoTime()
    private val resolution = floatArrayOf(800f, 600f)
    private var frameCounter = 0

    data class Bubble(
        var color: FloatArray,
        var radius: Float,
        var angle: Float,
        var angularVelocity: Float,
        var seed: Float,
        val clockwiseSpin: Boolean = Random.nextBoolean()
    ) {
        fun update(time: Float) {
            angularVelocity += (Random.nextFloat() - 1f) * 0.001f
            angularVelocity = angularVelocity.coerceIn(-0.02f..0.02f)
            angle += if (clockwiseSpin) angularVelocity else -angularVelocity
            radius = 0.07f + 0.02f * sin((time + seed * 1000f)) // increased base size and amplitude
        }
    }

    fun update() {
        val time = (System.nanoTime() - startTime) / 1_000_000_000.0f
        bubbles.forEach { it.update(time) }

        // Prepare arrays for uniforms (for logging only now)
        val bubbleColors = FloatArray(numBubbles * 3)
        val bubbleRadii = FloatArray(numBubbles)
        val bubblePositions = FloatArray(numBubbles * 2)
        val bubbleSeeds = FloatArray(numBubbles)
        for (i in 0 until numBubbles) {
            val bubble = bubbles[i]
            val x = 0.5f + 0.18f * cos(bubble.angle) // increased distance from center
            val y = 0.5f + 0.18f * sin(bubble.angle)
            bubbleColors[i * 3] = bubble.color[0]
            bubbleColors[i * 3 + 1] = bubble.color[1]
            bubbleColors[i * 3 + 2] = bubble.color[2]
            bubbleRadii[i] = bubble.radius
            bubblePositions[i * 2] = x
            bubblePositions[i * 2 + 1] = y
            bubbleSeeds[i] = bubble.seed
        }

        // Send only the basic uniforms and individual bubble data
        sendOSC("/u_numBubbles", numBubbles.toFloat())

        // Send each bubble's data as individual uniforms for glslViewer compatibility
        for (i in 0 until numBubbles) {
            sendOSC("/u_bubbleColors[$i]", floatArrayOf(bubbleColors[i*3], bubbleColors[i*3+1], bubbleColors[i*3+2]))
            sendOSC("/u_bubblePositions[$i]", floatArrayOf(bubblePositions[i*2], bubblePositions[i*2+1]))
            sendOSC("/u_bubbleRadii[$i]", bubbleRadii[i])
            sendOSC("/u_bubbleSeeds[$i]", bubbleSeeds[i])
        }

        frameCounter++
        if (frameCounter % 100 == 0) {
            println("\n--- OSC Frame #$frameCounter ---")
            println("u_time: $time (not sent, glslViewer sets it)")
            println("u_numBubbles: $numBubbles (sent as float)")
            println("\nPer-bubble uniforms sent to glslViewer:")
            for (i in 0 until numBubbles) {
                val c = bubbleColors.slice(i*3 until (i+1)*3)
                val x = bubblePositions[i*2]
                val y = bubblePositions[i*2+1]
                println("  /u_bubbleColors[$i]: [${"%.3f".format(c[0])}, ${"%.3f".format(c[1])}, ${"%.3f".format(c[2])}] (vec3)")
                println("  /u_bubblePositions[$i]: [${"%.4f".format(x)}, ${"%.4f".format(y)}] (vec2)")
                println("  /u_bubbleRadii[$i]: ${"%.4f".format(bubbleRadii[i])} (float)")
                println("  /u_bubbleSeeds[$i]: ${"%.4f".format(bubbleSeeds[i])} (float)")
            }
        }
    }

    private fun sendOSC(address: String, value: Float) {
        val message = createOSCMessage(address, value)
        val packet = DatagramPacket(message, message.size, InetAddress.getByName(host), port)
        socket.send(packet)
    }

    private fun sendOSC(address: String, values: FloatArray) {
        val message = createOSCMessage(address, values)
        val packet = DatagramPacket(message, message.size, InetAddress.getByName(host), port)
        socket.send(packet)
    }

    private fun createOSCMessage(address: String, value: Float): ByteArray {
        val addressBytes = address.toByteArray()
        val addressPadding = (4 - (addressBytes.size % 4)) % 4
        val typeTag = ",f".toByteArray()
        val typeTagPadding = (4 - (typeTag.size % 4)) % 4

        val buffer = ByteBuffer.allocate(addressBytes.size + addressPadding + typeTag.size + typeTagPadding + 4)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(addressBytes)
        repeat(addressPadding) { buffer.put(0) }
        buffer.put(typeTag)
        repeat(typeTagPadding) { buffer.put(0) }
        buffer.putFloat(value)
        return buffer.array()
    }

    private fun createOSCMessage(address: String, values: FloatArray): ByteArray {
        val addressBytes = address.toByteArray()
        val addressPadding = (4 - (addressBytes.size % 4)) % 4
        val typeTag = ",${"f".repeat(values.size)}".toByteArray()
        val typeTagPadding = (4 - (typeTag.size % 4)) % 4

        val buffer = ByteBuffer.allocate(addressBytes.size + addressPadding + typeTag.size + typeTagPadding + values.size * 4)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(addressBytes)
        repeat(addressPadding) { buffer.put(0) }
        buffer.put(typeTag)
        repeat(typeTagPadding) { buffer.put(0) }
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}

fun main() {
    val bubbleOSC = BubbleOSC()
    println("Starting BubbleOSC - sending to localhost:8000")
    println("Press Ctrl+C to stop")
    while (true) {
        bubbleOSC.update()
        Thread.sleep(32) // ~30 FPS
    }
}