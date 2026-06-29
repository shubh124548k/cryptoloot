package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

@Composable
fun LeaderboardRow(
    rank: Int,
    name: String,
    coins: Int,
    modifier: Modifier = Modifier
) {
    val rankColor = when (rank) {
        1 -> NeonGold
        2 -> NeonCyan
        3 -> NeonPurple
        else -> TextSecondary
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface.copy(alpha = 0.65f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.03f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(rankColor.copy(alpha = 0.15f), shape = CircleShape)
                .border(1.dp, rankColor, shape = CircleShape)
        ) {
            Text(
                text = "$rank",
                color = rankColor,
                fontSize = 14.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Avatar mock
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(
                    brush = Brush.linearGradient(listOf(NeonPink.copy(0.3f), NeonCyan.copy(0.3f))),
                    shape = CircleShape
                )
                .border(1.dp, NeonPurple, shape = CircleShape)
        ) {
            Text(
                text = name.take(2).uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Name
        Text(
            text = name,
            color = Color.White,
            fontSize = 15.sp,
            fontFamily = InterFamily,
            modifier = Modifier.weight(1f)
        )
        
        // Coins
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$coins 🪙",
                color = NeonGold,
                fontSize = 16.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Rank #${rank}",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = InterFamily
            )
        }
    }
}
