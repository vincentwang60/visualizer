package com.musicvisualizer.util

import kotlin.math.*
import kotlin.random.Random
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BubbleOSC(private val host: String = "localhost", private val port: Int = 8000) {
    private val socket = DatagramSocket()
    private var log_counter = 0
    private val bubbles = List(6) {
        Bubble(
            color = floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()),
            radius = 0f,
            angle = Random.nextFloat() * (2f * PI).toFloat(),
            angularVelocity = (Random.nextFloat() - 0.5f) * 0.08f
        )
    }
    private val startTime = System.nanoTime()
    
    data class Bubble(
        var color: FloatArray,
        var radius: Float,
        var angle: Float,
        var angularVelocity: Float,
        val clockwiseSpin: Boolean = Random.nextBoolean(),
        val seed: Float = Random.nextFloat()
    ) {
        fun update(time: Float) {
            angularVelocity += (Random.nextFloat() - 1f) * 0.001f
            angularVelocity = angularVelocity.coerceIn(-0.02f..0.02f)
            angle += if (clockwiseSpin) angularVelocity else -angularVelocity
            radius = 0.04f + 0.01f * sin((time + seed * 1000f))
        }
    }
    
    fun update() {
        log_counter += 1
        if (log_counter > 500) {
            print(bubbles[0].x, bubbles[0].y)
        }

        val time = (System.nanoTime() - startTime) / 1_000_000_000.0f
        bubbles.forEach { it.update(time) }
        
        sendOSC("/u_time", time)
        
        // Send each bubble's data individually
        bubbles.forEachIndexed { i, bubble ->
            val x = 400f + bubble.radius * 400f * cos(bubble.angle)
            val y = 300f + bubble.radius * 400f * sin(bubble.angle)
            val radius = bubble.radius * 800f
            
            sendOSC("/u_bubblePositions[$i]", x)
            sendOSC("/u_bubblePositions[$i]", y)
            sendOSC("/u_bubbleColors[$i]", bubble.color[0])
            sendOSC("/u_bubbleColors[$i]", bubble.color[1])
            sendOSC("/u_bubbleColors[$i]", bubble.color[2])
            sendOSC("/u_bubbleRadii[$i]", radius)
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