package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.isActive

data class Snowflake(
    var x: Float,
    var y: Float,
    val size: Float,
    val speedY: Float,
    val opacity: Float,
    val color: Color,
    var phase: Float,
    var twinkle: Float
)

@Composable
fun SnowfallCanvas(
    modifier: Modifier = Modifier,
    snowflakesCount: Int = 40
) {
    val colors = listOf(
        Color(0xFFFF9900), // Hex #FF9900 Yellow-Orange
        Color(0xFFFFD700), // Hex #FFD700 Neon Gold
        Color(0xFFFFB300), // Warm Orange-Yellow
        Color(0xFFFFEE58)  // Bright Yellow
    )

    val snowflakes = remember { mutableStateListOf<Snowflake>() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        if (snowflakes.isEmpty() && width > 0 && height > 0) {
            val initialList = List(snowflakesCount) {
                val isLarge = Random.nextBoolean()
                val sizeVal = if (isLarge) Random.nextFloat() * 6f + 8f else Random.nextFloat() * 3f + 2f
                Snowflake(
                    x = Random.nextFloat() * width,
                    y = Random.nextFloat() * height,
                    size = sizeVal,
                    speedY = Random.nextFloat() * 2.0f + 0.8f, // Slow variable gravity
                    opacity = Random.nextFloat() * 0.5f + 0.3f,
                    color = colors[Random.nextInt(colors.size)],
                    phase = Random.nextFloat() * Math.PI.toFloat() * 2f,
                    twinkle = Random.nextFloat() * Math.PI.toFloat() * 2f
                )
            }
            snowflakes.addAll(initialList)
        }

        snowflakes.forEach { s ->
            val currentOpacity = s.opacity * (0.6f + 0.4f * sin(s.twinkle))
            // Glowing blur mask layer
            drawCircle(
                color = s.color.copy(alpha = currentOpacity * 0.35f),
                radius = s.size * 2.4f,
                center = Offset(s.x, s.y)
            )
            // Core particle
            drawCircle(
                color = s.color.copy(alpha = currentOpacity),
                radius = s.size,
                center = Offset(s.x, s.y)
            )
        }
    }

    LaunchedEffect(Unit) {
        var frame = 0L
        while (isActive) {
            withFrameNanos { time ->
                frame++
                if (snowflakes.isNotEmpty()) {
                    for (i in snowflakes.indices) {
                        val s = snowflakes[i]
                        val nextY = s.y + s.speedY
                        val nextX = s.x + sin(frame * 0.02f + s.phase) * 0.4f
                        val nextTwinkle = s.twinkle + 0.05f

                        if (nextY > 2200f) {
                            snowflakes[i] = s.copy(
                                x = Random.nextFloat() * 1200f,
                                y = -50f,
                                twinkle = nextTwinkle
                            )
                        } else {
                            snowflakes[i] = s.copy(
                                x = nextX,
                                y = nextY,
                                twinkle = nextTwinkle
                            )
                        }
                    }
                }
            }
        }
    }
}
