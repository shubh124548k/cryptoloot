package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

@Composable
fun LoadingOverlay(
    message: String = "Syncing reward engine...",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotate"
    )
    
    val textPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TextPulse"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDark.copy(alpha = 0.85f))
            .clickable(enabled = true, onClick = {}), // Prevent taps behind
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                // outer ring
                CircularProgressIndicator(
                    progress = { 0.75f },
                    color = NeonPink,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotation)
                )
                // inner ring rotating in opposite direction
                CircularProgressIndicator(
                    progress = { 0.5f },
                    color = NeonCyan,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(70.dp)
                        .rotate(-rotation * 1.5f)
                )
                Text(
                    text = "KL",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = textPulse),
                fontSize = 15.sp,
                fontFamily = InterFamily,
                letterSpacing = 0.05.sp
            )
        }
    }
}
