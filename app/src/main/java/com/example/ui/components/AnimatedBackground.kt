package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun AnimatedBackground(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "EnergyOrbs")
    
    // Float animations for background energy orbs
    val orb1X by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb1X"
    )
    val orb1Y by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb1Y"
    )
    
    val orb2X by infiniteTransition.animateFloat(
        initialValue = 200f,
        targetValue = -100f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb2X"
    )
    val orb2Y by infiniteTransition.animateFloat(
        initialValue = 300f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(23000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb2Y"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDark)
    ) {
        // Energy Orbs (large blurred gradient circles)
        Box(
            modifier = Modifier
                .offset(x = orb1X.dp, y = orb1Y.dp)
                .size(300.dp)
                .blur(80.dp)
                .background(color = NeonPink.copy(alpha = 0.08f), shape = CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = orb2X.dp, y = orb2Y.dp)
                .size(350.dp)
                .blur(90.dp)
                .background(color = NeonCyan.copy(alpha = 0.07f), shape = CircleShape)
        )
        Box(
            modifier = Modifier
                .offset(x = (orb1X + 150).dp, y = (orb2Y - 100).dp)
                .size(250.dp)
                .blur(70.dp)
                .background(color = NeonPurple.copy(alpha = 0.09f), shape = CircleShape)
        )
        
        // Particle canvas layered on top
        ParticleCanvas(particleCount = 50)
        
        // Snowfall layered on top
        SnowfallCanvas(snowflakesCount = 30)
    }
}
