package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class ToastType {
    SUCCESS, ERROR, WARNING, INFO, COIN
}

data class ToastMessage(
    val id: Long = System.currentTimeMillis() + (0..10000).random(),
    val message: String,
    val type: ToastType = ToastType.INFO
)

@Composable
fun ToastOverlay(
    toast: ToastMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(400)
        ) + fadeIn(animationSpec = tween(400)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (toast != null) {
            val config = when (toast.type) {
                ToastType.SUCCESS -> Triple(NeonGreen.copy(alpha = 0.15f), NeonGreen, "✅")
                ToastType.ERROR -> Triple(NeonRed.copy(alpha = 0.15f), NeonRed, "❌")
                ToastType.WARNING -> Triple(NeonYellow.copy(alpha = 0.15f), NeonYellow, "⚠️")
                ToastType.INFO -> Triple(NeonCyan.copy(alpha = 0.15f), NeonCyan, "ℹ️")
                ToastType.COIN -> Triple(NeonGold.copy(alpha = 0.15f), NeonGold, "🪙")
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = config.second,
                        spotColor = config.second
                    )
                    .background(config.first, shape = RoundedCornerShape(12.dp))
                    .border(1.dp, config.second, shape = RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = config.third,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = toast.message,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = InterFamily,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDismiss() }
                    )
                }
            }
            
            // Auto-dismiss after 3.5s
            LaunchedEffect(toast.id) {
                kotlinx.coroutines.delay(3500)
                onDismiss()
            }
        }
    }
}
