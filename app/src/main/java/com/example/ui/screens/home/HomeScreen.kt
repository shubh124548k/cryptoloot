package com.example.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.RewardPackCatalog
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    
    // Rotating watch ad play icon animation
    val infiniteTransition = rememberInfiniteTransition(label = "WatchIcon")
    val playRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PlayRotation"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDark)
    ) {
        // Section A — Animated Background Layer
        AnimatedBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Section B — Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo & title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(10.dp),
                                ambientColor = NeonPink.copy(alpha = 0.4f),
                                spotColor = NeonPink.copy(alpha = 0.4f)
                            )
                            .background(
                                brush = Brush.linearGradient(listOf(NeonPink, NeonPurple)),
                                shape = RoundedCornerShape(10.dp)
                            )
                    ) {
                        Text(
                            text = "KL",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "KryptoLoot",
                            color = NeonPink,
                            fontSize = 18.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.1.sp
                        )
                        Text(
                            text = "Earn. Get Rewards. Dominate.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.02.sp
                        )
                    }
                }
                
                // Animated coin balance chip
                CoinCounter(
                    coins = uiState.coinBalance,
                    size = CoinCounterSize.SMALL,
                    modifier = Modifier.clickable { onNavigate("coins") }
                )
            }
            
            // Section C — Stats Cards Row (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1 — Daily Ads Progress
                NeonCard(
                    modifier = Modifier.width(160.dp),
                    glowColor = NeonPink,
                    floatVariant = FloatVariant.SLOW
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "Ads watched",
                        tint = NeonPink,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Daily Ads",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = InterFamily
                    )
                    Text(
                        text = "${uiState.adsWatchedToday} / ${uiState.dailyCap}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NeonProgressBar(
                        progress = uiState.adsWatchedToday.toFloat() / uiState.dailyCap.toFloat(),
                        color = NeonPink,
                        showLabel = false
                    )
                }
                
                // Card 2 — Coins Earned Today
                NeonCard(
                    modifier = Modifier.width(160.dp),
                    glowColor = NeonGold,
                    floatVariant = FloatVariant.MEDIUM
                ) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = "Coins Earned Today",
                        tint = NeonGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Coins Earned",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = InterFamily
                    )
                    Text(
                        text = "+${uiState.adsWatchedToday * 10}",
                        color = NeonGold,
                        fontSize = 18.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "≈ ₹${String.format("%.2f", (uiState.adsWatchedToday * 10) * 0.0333f)}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = InterFamily
                    )
                }
                
                // Card 3 — Session / Break Timer
                val onBreak = uiState.breakUntil != null
                NeonCard(
                    modifier = Modifier.width(160.dp),
                    glowColor = if (onBreak) NeonRed else NeonGreen,
                    floatVariant = FloatVariant.FAST
                ) {
                    Icon(
                        imageVector = if (onBreak) Icons.Default.HourglassBottom else Icons.Default.Bolt,
                        contentDescription = "Session",
                        tint = if (onBreak) NeonRed else NeonGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (onBreak) "Break Time" else "Ad Interval",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = InterFamily
                    )
                    if (onBreak) {
                        val remainingMs = (uiState.breakUntil ?: 0) - System.currentTimeMillis()
                        val remainingSecs = (remainingMs / 1000).coerceAtLeast(0)
                        val mins = remainingSecs / 60
                        val secs = remainingSecs % 60
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            color = NeonRed,
                            fontSize = 18.sp,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "${uiState.sessionAds} / ${uiState.sessionCap} Ads",
                            color = NeonGreen,
                            fontSize = 18.sp,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NeonProgressBar(
                        progress = if (onBreak) 1.0f else uiState.sessionAds.toFloat() / uiState.sessionCap.toFloat(),
                        color = if (onBreak) NeonRed else NeonGreen,
                        showLabel = false
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.activeRedeemStatus != null) {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    glowColor = NeonCyan,
                    floatVariant = FloatVariant.NONE
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Redeem Status",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Queue #${uiState.activeQueuePosition ?: 0}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.activeRedeemStatus ?: "PROCESSING",
                                color = NeonGold,
                                fontSize = 12.sp,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Estimated",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = InterFamily
                            )
                            Text(
                                text = uiState.estimatedQueueWait ?: "Processing now",
                                color = NeonGreen,
                                fontSize = 14.sp,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Section D — Main Watch Ad Button (Center Stage)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val onBreak = uiState.breakUntil != null
                
                NeonButton(
                    text = if (onBreak) "ON BREAK ⏱️" else "⚡ WATCH AD & EARN",
                    onClick = { onNavigate("watch_ad") },
                    style = NeonButtonStyle.WATCH_AD,
                    enabled = !onBreak,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .then(if (!onBreak) Modifier.rotate(playRotation) else Modifier)
                        )
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = uiState.rewardPacks
                        .joinToString(" | ") { pack -> "${pack.requiredCoins} 🪙 = ₹${pack.rewardAmount}" },
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Section E — Quick Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Redeem card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate("coins") }
                ) {
                    NeonCard(
                        glowColor = NeonPink,
                        floatVariant = FloatVariant.NONE,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Redeem",
                            tint = NeonPink,
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Redeem 🎁",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                
                // History card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate("profile") }
                ) {
                    NeonCard(
                        glowColor = NeonCyan,
                        floatVariant = FloatVariant.NONE,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = NeonCyan,
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "History 📜",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                
                // Leaderboard card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate("leaderboard") }
                ) {
                    NeonCard(
                        glowColor = NeonPurple,
                        floatVariant = FloatVariant.NONE,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = "Leaderboard",
                            tint = NeonPurple,
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Leaderboard 🏆",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Section F — Trust Score Display
            Text(
                text = "ANTI-CHEAT INTELLIGENCE",
                color = NeonCyan,
                fontSize = 12.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                glowColor = NeonCyan,
                floatVariant = FloatVariant.SLOW
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrustScoreGauge(
                        score = uiState.trustScore,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "Security Protection",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keep VPN and emulator turned off. Low scores decrease earnings and may freeze redemption claims.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp)) // Padding for bottom navbar
        }
        
        // Error state toast overlay
        if (uiState.error != null) {
            ToastOverlay(
                toast = ToastMessage(message = uiState.error!!, type = ToastType.ERROR),
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        
        // Screen load spinner overlay
        if (uiState.isLoading) {
            LoadingOverlay()
        }
    }
}
