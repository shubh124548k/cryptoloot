package com.example.ui.screens.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ParticleCanvas
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startExitAnimation by remember { mutableStateOf(false) }
    
    // Infinite spin transition
    val infiniteTransition = rememberInfiniteTransition(label = "SplashLogoSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LogoRotation"
    )
    
    // Pulsing monogram scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoPulse"
    )
    
    // Swipe shimmer effect for branding
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "Shimmer"
    )
    
    // Entrance animations
    var taglineVisible by remember { mutableStateOf(false) }
    var progressBarProgress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        // Staggered reveal
        delay(600)
        taglineVisible = true
        
        // Progress bar fill over 2.2s
        val steps = 100
        for (i in 1..steps) {
            delay(18)
            progressBarProgress = i.toFloat() / steps.toFloat()
        }
        
        // Wait for 2.5s mark to end splash
        delay(200)
        startExitAnimation = true
        delay(400) // Slide up transition time
        onSplashComplete()
    }
    
    // Page exit slide-up transition
    val exitOffset by animateFloatAsState(
        targetValue = if (startExitAnimation) -1000f else 0f,
        animationSpec = tween(400, easing = EaseInCubic),
        label = "ExitAnim"
    )
    
    val exitAlpha by animateFloatAsState(
        targetValue = if (startExitAnimation) 0f else 1f,
        animationSpec = tween(300, easing = EaseInOutSine),
        label = "ExitAlpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .offset(y = exitOffset.dp)
            .background(DeepDark),
        contentAlignment = Alignment.Center
    ) {
        // High fidelity ambient background
        ParticleCanvas(particleCount = 60)
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(exitAlpha)
        ) {
            // Hexagon logo monogram
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale)
            ) {
                // outer segment arc drawing
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 4.dp.toPx()
                    val r = (size.width - strokeW) / 2f
                    
                    // Rotating glow outer ring
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(NeonPink, NeonCyan, NeonPurple, NeonPink)
                        ),
                        startAngle = rotation,
                        sweepAngle = 320f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                        size = Size(r * 2f, r * 2f),
                        topLeft = Offset(strokeW / 2f, strokeW / 2f)
                    )
                }
                
                // Centered dynamic greeting letters
                Text(
                    text = "KL",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).sp,
                    modifier = Modifier.shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = NeonPink,
                        spotColor = NeonCyan
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Cyberpunk wordmark KryptoLoot
            val textBrush = Brush.horizontalGradient(
                colors = listOf(NeonPink, NeonCyan, NeonPurple),
                startX = shimmerOffset,
                endX = shimmerOffset + 150f
            )
            
            Text(
                text = "KryptoLoot",
                style = androidx.compose.ui.text.TextStyle(
                    brush = textBrush,
                    fontSize = 38.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.04).sp
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Animated progress bar
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .background(CardSurface2, CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressBarProgress)
                        .background(
                            brush = Brush.horizontalGradient(listOf(NeonPink, NeonCyan)),
                            shape = CircleShape
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tagline reveal
            AnimatedVisibility(
                visible = taglineVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 20 }),
                exit = fadeOut()
            ) {
                Text(
                    text = "Earn Coins. Get Rewards. Dominate.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = InterFamily,
                    letterSpacing = 0.05.sp
                )
            }
        }
    }
}
