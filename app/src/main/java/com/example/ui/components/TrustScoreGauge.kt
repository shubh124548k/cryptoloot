package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

@Composable
fun TrustScoreGauge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.coerceIn(0, 100).toFloat() / 100f,
        animationSpec = tween(1200, easing = EaseOutCubic),
        label = "TrustScoreAnim"
    )
    
    val gaugeColor = when (score) {
        in 80..100 -> NeonGreen
        in 50..79 -> NeonYellow
        in 20..49 -> NeonOrange
        else -> NeonRed
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val sizePx = size.width
                val arcRadius = (sizePx - strokeWidth) / 2f
                
                // Draw base background arc
                drawArc(
                    color = CardSurface2,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                )
                
                // Draw active animated arc
                drawArc(
                    color = gaugeColor,
                    startAngle = 180f,
                    sweepAngle = 180f * animatedScore,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = 12.dp)
            ) {
                Text(
                    text = "$score / 100",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Trust Score",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = InterFamily
                    )
                }
            }
        }
    }
}
