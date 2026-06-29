package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

enum class NeonButtonStyle {
    PRIMARY, SECONDARY, WATCH_AD
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NeonButtonStyle = NeonButtonStyle.PRIMARY,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Scale on press animation
    val scale by animateFloatAsState(
        targetValue = if (!enabled) 1f else if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ButtonScale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "ButtonPulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )
    
    val buttonShape = RoundedCornerShape(10.dp)
    
    val modifierWithClicks = if (enabled) {
        modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
    } else {
        modifier
    }
    
    val backgroundBrush = when {
        !enabled -> Brush.linearGradient(colors = listOf(TextMuted.copy(alpha = 0.2f), TextMuted.copy(alpha = 0.2f)))
        style == NeonButtonStyle.PRIMARY -> Brush.linearGradient(colors = listOf(NeonPink, NeonPurple))
        style == NeonButtonStyle.SECONDARY -> Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
        style == NeonButtonStyle.WATCH_AD -> Brush.linearGradient(colors = listOf(CardSurface.copy(alpha = 0.6f), CardSurface2.copy(alpha = 0.7f)))
        else -> Brush.linearGradient(colors = listOf(NeonPink, NeonPurple))
    }
    
    val borderBrush = when {
        !enabled -> Brush.linearGradient(colors = listOf(TextMuted.copy(alpha = 0.4f), TextMuted.copy(alpha = 0.4f)))
        style == NeonButtonStyle.SECONDARY -> Brush.linearGradient(colors = listOf(NeonCyan, NeonCyan))
        style == NeonButtonStyle.WATCH_AD -> Brush.linearGradient(colors = listOf(NeonPink, NeonOrange, NeonGold))
        else -> Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
    }
    
    val shadowColor = when {
        !enabled -> Color.Transparent
        style == NeonButtonStyle.PRIMARY -> NeonPink
        style == NeonButtonStyle.SECONDARY -> NeonCyan
        style == NeonButtonStyle.WATCH_AD -> NeonPink
        else -> NeonPink
    }
    
    val shadowElevation = when {
        !enabled -> 0.dp
        style == NeonButtonStyle.WATCH_AD -> (8 * pulseGlow).dp
        else -> 4.dp
    }
    
    Box(
        modifier = modifierWithClicks
            .shadow(
                elevation = shadowElevation,
                shape = buttonShape,
                ambientColor = shadowColor.copy(alpha = 0.25f),
                spotColor = shadowColor.copy(alpha = 0.25f)
            )
            .background(backgroundBrush, shape = buttonShape)
            .then(
                if (style == NeonButtonStyle.SECONDARY || style == NeonButtonStyle.WATCH_AD || !enabled) {
                    Modifier.border(2.dp, borderBrush, shape = buttonShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (!enabled) TextMuted else if (style == NeonButtonStyle.SECONDARY) NeonCyan else Color.White,
                fontSize = if (style == NeonButtonStyle.WATCH_AD) 18.sp else 16.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.05.sp
            )
        }
    }
}
