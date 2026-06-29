package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

enum class FloatVariant {
    SLOW, MEDIUM, FAST, NONE
}

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    glowColor: Color = NeonPink,
    glowIntensity: Float = 0.6f,
    floatVariant: FloatVariant = FloatVariant.SLOW,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "NeonCardGlow")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )
    
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = when(floatVariant) {
            FloatVariant.SLOW -> -12f
            FloatVariant.MEDIUM -> -8f
            FloatVariant.FAST -> -6f
            FloatVariant.NONE -> 0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when(floatVariant) {
                    FloatVariant.SLOW -> 7000
                    FloatVariant.MEDIUM -> 5000
                    FloatVariant.FAST -> 3000
                    FloatVariant.NONE -> 1000
                },
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatOffset"
    )
    
    val cardShape = RoundedCornerShape(20.dp)
    
    Box(
        modifier = modifier
            .scrollGestureSafe()
            .offset(y = floatOffset.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        CardSurface.copy(alpha = 0.80f),
                        CardSurface2.copy(alpha = 0.68f)
                    )
                ),
                shape = cardShape
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        glowColor.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f),
                        glowColor.copy(alpha = 0.15f)
                    )
                ),
                shape = cardShape
            )
            .shadow(
                elevation = (2.2f * glowIntensity).dp,
                shape = cardShape,
                ambientColor = glowColor.copy(alpha = glowAlpha * glowIntensity * 0.14f),
                spotColor = glowColor.copy(alpha = glowAlpha * glowIntensity * 0.14f)
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            content = content
        )
    }
}
