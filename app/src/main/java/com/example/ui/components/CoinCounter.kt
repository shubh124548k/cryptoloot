package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

enum class CoinCounterSize {
    SMALL, MEDIUM, LARGE
}

@Composable
fun CoinCounter(
    coins: Int,
    modifier: Modifier = Modifier,
    size: CoinCounterSize = CoinCounterSize.MEDIUM
) {
    // Smooth integer counting using spring animation
    val animatedCoins by animateIntAsState(
        targetValue = coins,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CoinCounterAnim"
    )
    
    // Rotating coin animation
    val infiniteTransition = rememberInfiniteTransition(label = "CoinSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )
    
    val coinGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoinGlow"
    )
    
    when (size) {
        CoinCounterSize.SMALL -> {
            Row(
                modifier = modifier
                    .shadow(
                        elevation = 5.dp,
                        shape = CircleShape,
                        ambientColor = NeonGold,
                        spotColor = NeonGold
                    )
                    .background(
                        brush = Brush.linearGradient(listOf(CardSurface, CardSurface2)),
                        shape = CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.MonetizationOn,
                    contentDescription = "Coin",
                    tint = NeonGold,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$animatedCoins",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        CoinCounterSize.MEDIUM -> {
            Row(
                modifier = modifier
                    .shadow(
                        elevation = 9.dp,
                        shape = CircleShape,
                        ambientColor = NeonGold,
                        spotColor = NeonGold
                    )
                    .background(
                        brush = Brush.linearGradient(listOf(CardSurface, CardSurface2)),
                        shape = CircleShape
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.MonetizationOn,
                    contentDescription = "Coin",
                    tint = NeonGold,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(rotation)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$animatedCoins",
                    color = NeonGold,
                    fontSize = 24.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.05.sp
                )
            }
        }
        
        CoinCounterSize.LARGE -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(
                            elevation = (16 * coinGlowAlpha).dp,
                            shape = CircleShape,
                            ambientColor = NeonGold,
                            spotColor = NeonGold
                        )
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(NeonGold.copy(alpha = 0.2f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.MonetizationOn,
                        contentDescription = "Large Coin",
                        tint = NeonGold,
                        modifier = Modifier
                            .size(90.dp)
                            .rotate(rotation)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$animatedCoins",
                    color = NeonGold,
                    fontSize = 54.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).sp,
                    modifier = Modifier.shadow(
                        elevation = 10.dp,
                        ambientColor = NeonGold,
                        spotColor = NeonGold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                val rupeeVal = animatedCoins * 0.0333
                Text(
                    text = "≈ ₹${String.format("%.2f", rupeeVal)}",
                    color = TextSecondary,
                    fontSize = 18.sp,
                    fontFamily = InterFamily
                )
            }
        }
    }
}
