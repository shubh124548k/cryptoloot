package com.example.ui.screens.watchad

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WatchAdScreen(
    viewModel: WatchAdViewModel,
    isAdReady: Boolean,
    isRetryAvailable: Boolean,
    adStateText: String,
    dailyResetSeconds: Int,
    onWatchAdClick: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDark)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF0A0A0F),
            bottomBar = { BottomNavBar(currentRoute = "watch_ad", onNavigate = onNavigate) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Background layers first
                AnimatedBackground()
                
                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (dailyResetSeconds > 0) {
                        val hours = dailyResetSeconds / 3600
                        val minutes = (dailyResetSeconds % 3600) / 60
                        val secs = dailyResetSeconds % 60
                        val timeString = String.format("%02d:%02d:%02d", hours, minutes, secs)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color(0xFF05050A).copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                                .border(
                                    width = 2.dp,
                                    color = NeonPink,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "NEXT REWARDS UNLOCK IN:",
                                    color = NeonPink,
                                    fontSize = 14.sp,
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.1.em
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = timeString,
                                    color = NeonPink,
                                    fontSize = 36.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.1.em,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = NeonPink.copy(alpha = 0.8f),
                                            offset = Offset(0f, 0f),
                                            blurRadius = 15f
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        AdContainer()
                    }
                    
                    ProgressSection(uiState)
                    
                    if (dailyResetSeconds <= 0) {
                        WatchAdActionButton(
                            isAdReady = isAdReady,
                            isRetryAvailable = isRetryAvailable,
                            adStateText = adStateText,
                            onClick = onWatchAdClick
                        )
                    }
                }
            }
        }
        
        // Success Popup overlay modal
        if (uiState.showSuccessPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                // Confetti fall
                ParticleCanvas(particleCount = 50)
                
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(24.dp),
                    glowColor = NeonGold,
                    floatVariant = FloatVariant.MEDIUM
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "TRANSMISSION SECURED 🎉",
                            color = NeonGold,
                            fontSize = 20.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        CoinCounter(
                            coins = uiState.earnedCoins,
                            size = CoinCounterSize.MEDIUM
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "+${uiState.earnedCoins} Coins Earned!",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val newBal = uiState.newBalance
                        Text(
                            text = "New Balance: $newBal 🪙",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = InterFamily
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        NeonButton(
                            text = "WATCH ANOTHER AD 🎬",
                            onClick = {
                                viewModel.dismissPopup()
                            },
                            style = NeonButtonStyle.PRIMARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextButton(
                            onClick = {
                                viewModel.dismissPopup()
                                onNavigate("home")
                            }
                        ) {
                            Text(
                                text = "RETURN HOME",
                                color = NeonCyan,
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
        
        // Error toast overlay
        if (uiState.error != null) {
            ToastOverlay(
                toast = ToastMessage(message = uiState.error!!, type = ToastType.ERROR),
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        
        // Loading state
        if (uiState.isLoading) {
            LoadingOverlay(message = "Securing ad validation codes...")
        }
    }
}

@Composable
private fun AdContainer() {
    val infiniteTransition = rememberInfiniteTransition(label = "adBorder")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color(0xFF0D0D1A), RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF2D55).copy(alpha = borderAlpha),
                        Color(0xFF00D4FF).copy(alpha = borderAlpha * 0.8f),
                        Color(0xFFFF2D55).copy(alpha = borderAlpha)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Text(
            text = "⚡ COMMERCIAL TRANSMISSION",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color(0xFFFF2D55).copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                color = Color.White
            )
        )
        
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color(0xFF00D4FF).copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Text("PREMIUM PARTNER ADSTREAM", color = Color(0xFFA0AEC0), fontSize = 12.sp, letterSpacing = 0.08.em)
            Text("Live stream active • Do not close app", color = Color(0xFF4A5568), fontSize = 10.sp)
        }
        
        Text(
            text = "🔒 Secure ad validation active • Do not close",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            style = TextStyle(
                fontSize = 9.sp,
                color = Color(0xFF4A5568)
            )
        )
    }
}

@Composable
private fun ProgressSection(uiState: WatchAdUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Today's Limit",
                color = Color(0xFFA0AEC0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${uiState.adWatchedToday} / 100",
                color = Color(0xFFFF2D55),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(6.dp))
        
        val dailyProgress by animateFloatAsState(
            targetValue = (uiState.adWatchedToday / 100f).coerceIn(0f, 1f),
            animationSpec = tween(800, easing = FastOutSlowInEasing),
            label = "dailyProgress"
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(dailyProgress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFFF2D55), Color(0xFF9B59B6))),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
        
        Spacer(Modifier.height(14.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Session Velocity", color = Color(0xFFA0AEC0), fontSize = 12.sp)
            Text("${uiState.sessionAds} / 20", color = Color(0xFF00D4FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        
        val sessionProgress by animateFloatAsState(
            targetValue = (uiState.sessionAds / 20f).coerceIn(0f, 1f),
            animationSpec = tween(800),
            label = "sessionProgress"
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(sessionProgress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF00D4FF), Color(0xFF0080FF))),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Composable
private fun WatchAdActionButton(
    isAdReady: Boolean,
    isRetryAvailable: Boolean,
    adStateText: String,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "watchAdBtn")
    val buttonGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Button(
        onClick = onClick,
        enabled = isAdReady || isRetryAvailable,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 20.dp)
            .then(
                if (isAdReady) Modifier.shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = Color(0xFFFFD700).copy(alpha = buttonGlow),
                    ambientColor = Color(0xFFFFD700).copy(alpha = buttonGlow * 0.5f)
                ) else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (isAdReady)
                        Brush.horizontalGradient(listOf(Color(0xFFFF9900), Color(0xFFFFD700), Color(0xFFFFEE58)))
                    else
                        Brush.horizontalGradient(listOf(Color(0xFF2A2A3A), Color(0xFF1A1A2E))),
                    shape = RoundedCornerShape(14.dp)
                )
                .then(
                    if (isAdReady) Modifier.border(
                        width = 1.5.dp,
                        color = Color(0xFFFFEE58),
                        shape = RoundedCornerShape(14.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isAdReady && !isRetryAvailable) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFFA0AEC0),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = adStateText.uppercase(),
                        color = Color(0xFFA0AEC0),
                        fontSize = 13.sp,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.08.em
                    )
                } else {
                    Text(
                        text = "⚡  $adStateText  ⚡",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.1.em,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.White.copy(alpha = 0.5f),
                                offset = Offset(0f, 0f),
                                blurRadius = 8f
                            )
                        )
                    )
                }
            }
        }
    }
}
