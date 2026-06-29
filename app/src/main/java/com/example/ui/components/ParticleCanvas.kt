package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    val size: Float,
    val speedX: Float,
    val speedY: Float,
    val opacity: Float,
    val color: Color,
    var pulse: Float,
    val pulseSpeed: Float
)

@Composable
fun ParticleCanvas(
    modifier: Modifier = Modifier,
    particleCount: Int = 40 // Optimized for performance
) {
    val colors = listOf(
        Color(0xFFFFD700), // electric gold
        Color(0xFFFF6B00), // vibrant orange
        Color(0xFFFF1493), // hyper-pink
        NeonPink,
        NeonPurple
    )
    
    val particles = remember { mutableStateListOf<Particle>() }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        if (particles.isEmpty() && width > 0 && height > 0) {
            val initialList = List(particleCount) {
                val sizeVal = if (Random.nextBoolean()) Random.nextFloat() * 4f + 1f else Random.nextFloat() * 8f + 4f
                Particle(
                    x = Random.nextFloat() * width,
                    y = Random.nextFloat() * height,
                    size = sizeVal,
                    speedX = (Random.nextFloat() - 0.5f) * 1.5f,
                    speedY = (Random.nextFloat() - 0.5f) * 1.5f,
                    opacity = Random.nextFloat() * 0.4f + 0.1f,
                    color = colors[Random.nextInt(colors.size)],
                    pulse = Random.nextFloat() * Math.PI.toFloat() * 2f,
                    pulseSpeed = Random.nextFloat() * 0.02f + 0.005f
                )
            }
            particles.addAll(initialList)
        }
        
        // Draw connection lines
        val limitDist = 120f
        for (i in particles.indices) {
            val p1 = particles[i]
            for (j in i + 1 until particles.size) {
                val p2 = particles[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < limitDist) {
                    val lineOpacity = (1f - dist / limitDist) * 0.12f
                    drawLine(
                        color = Color.White.copy(alpha = lineOpacity),
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = 1f
                    )
                }
            }
        }
        
        // Draw particles
        particles.forEach { p ->
            val currentOpacity = p.opacity * (0.7f + 0.3f * sin(p.pulse))
            drawCircle(
                color = p.color.copy(alpha = currentOpacity),
                radius = p.size,
                center = Offset(p.x, p.y)
            )
        }
    }
    
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { time ->
                if (particles.isNotEmpty()) {
                    for (i in particles.indices) {
                        val p = particles[i]
                        var nextX = p.x + p.speedX
                        var nextY = p.y + p.speedY
                        val nextPulse = p.pulse + p.pulseSpeed
                        
                        if (nextX < -20f) nextX = 1200f
                        if (nextX > 1200f) nextX = -20f
                        if (nextY < -20f) nextY = 2000f
                        if (nextY > 2000f) nextY = -20f
                        
                        particles[i] = p.copy(x = nextX, y = nextY, pulse = nextPulse)
                    }
                }
            }
        }
    }
}
