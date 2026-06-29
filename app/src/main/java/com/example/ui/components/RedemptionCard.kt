package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

@Composable
fun RedemptionCard(
    title: String,
    coinCost: Int,
    payoutText: String,
    badgeText: String,
    badgeColor: Color,
    currentBalance: Int,
    statusText: String = "READY",
    actionEnabled: Boolean = true,
    onRedeem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (currentBalance.toFloat() / coinCost.toFloat()).coerceIn(0f, 1f)
    val canRedeem = currentBalance >= coinCost && actionEnabled
    
    NeonCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = badgeColor,
        floatVariant = FloatVariant.SLOW
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Requires $coinCost 🪙",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = InterFamily
                )
            }
            Text(
                text = payoutText,
                color = NeonGold,
                fontSize = 28.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .background(
                    color = when (statusText) {
                        "QUEUED" -> NeonYellow.copy(alpha = 0.15f)
                        "PROCESSING" -> NeonCyan.copy(alpha = 0.15f)
                        else -> NeonGreen.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = 1.dp,
                    color = when (statusText) {
                        "QUEUED" -> NeonYellow
                        "PROCESSING" -> NeonCyan
                        else -> NeonGreen
                    },
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = statusText,
                color = when (statusText) {
                    "QUEUED" -> NeonYellow
                    "PROCESSING" -> NeonCyan
                    else -> NeonGreen
                },
                fontSize = 10.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        NeonProgressBar(
            progress = progress,
            color = badgeColor,
            label = "$currentBalance / $coinCost"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (canRedeem) {
            val infiniteTransition = rememberInfiniteTransition(label = "claimBtnGlow")
            val buttonGlow by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow"
            )
            
            androidx.compose.material3.Button(
                onClick = onRedeem,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(
                        elevation = (8 * buttonGlow).dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = Color(0xFFFFD700).copy(alpha = buttonGlow),
                        ambientColor = Color(0xFFFFD700).copy(alpha = buttonGlow * 0.5f)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFD700), // Gold
                                    Color(0xFFFF9F00), // Amber
                                    Color(0xFFFFD700)  // Gold
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (actionEnabled) "CLAIM REWARD ⚡" else "REDEEM LOCKED 🔒",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.08.em
                    )
                }
            }
        } else {
            androidx.compose.material3.Button(
                onClick = {},
                enabled = false,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFF1F1F2E),
                    disabledContentColor = Color(0xFF4A5568)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (statusText == "PROCESSING") "PROCESSING ⏳" else "LOCK 🔒",
                    color = Color(0xFF4A5568),
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.05.em
                )
            }
        }
    }
}
