package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

@Composable
fun NeonProgressBar(
    progress: Float, // 0.0 to 1.0
    color: Color = NeonPink,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    label: String = "${(progress * 100).toInt()}%",
    animationDuration: Int = 800
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(animationDuration, easing = EaseOutCubic),
        label = "ProgressBarAnim"
    )
    
    Column(modifier = modifier) {
        if (showLabel) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = label,
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
        ) {
            val trackHeight = size.height
            val trackWidth = size.width
            
            // Draw track background
            drawRoundRect(
                color = CardSurface2,
                cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2),
                size = Size(trackWidth, trackHeight)
            )
            
            val fillWidth = trackWidth * animatedProgress
            if (fillWidth > 0f) {
                // Glow behind fill
                drawRoundRect(
                    color = color.copy(alpha = 0.2f),
                    cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2),
                    size = Size(fillWidth, trackHeight)
                )
                
                // Active fill gradient
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color, color.copy(alpha = 0.6f))
                    ),
                    cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2),
                    size = Size(fillWidth, trackHeight)
                )
                
                // Glowing head
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = (trackHeight / 2) + 1.dp.toPx(),
                    center = Offset(fillWidth, trackHeight / 2)
                )
                drawCircle(
                    color = color,
                    radius = (trackHeight / 2) + 4.dp.toPx(),
                    center = Offset(fillWidth, trackHeight / 2),
                    alpha = 0.5f
                )
            }
        }
    }
}
