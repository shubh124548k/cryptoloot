package com.example.ui.screens.history

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.RedemptionHistoryItem
import com.example.ui.screens.coins.CoinsViewModel
import com.example.ui.theme.*

@Composable
fun HistoryScreen(
    coinsViewModel: CoinsViewModel,
    focusedRequestId: Int? = null,
    onFocusConsumed: () -> Unit = {}
) {
    val uiState by coinsViewModel.uiState.collectAsState()
    val allItems = uiState.historyList.filter { item ->
        item.transaction_type == "REDEEM_REQUEST" ||
            item.transaction_type == "REDEEM_PROCESSING" ||
            item.transaction_type == "REDEEM_APPROVED" ||
            item.transaction_type == "REDEEM_REJECTED" ||
            item.transaction_type == "QUEUE_COMPLETED"
    }.sortedByDescending { it.created_at }
    Log.d("SYNC_TRACE", "HistoryScreen: uiState.historyList.size=${uiState.historyList.size} allItems (after filter)=${allItems.size}")
    allItems.forEach { item ->
        Log.d("SYNC_TRACE", "HistoryScreen:   item queue_id=${item.queue_id} type=${item.transaction_type} status=${item.status} admin_reply='${item.admin_reply}' request_id=${item.request_id}")
    }
    var expandedId by remember { mutableStateOf<Int?>(if (focusedRequestId != null) focusedRequestId else null) }

    LaunchedEffect(focusedRequestId) {
        if (focusedRequestId != null) {
            expandedId = focusedRequestId
            onFocusConsumed()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDark)
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "REDEMPTION HISTORY",
                color = NeonCyan,
                fontSize = 12.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (allItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .background(CardSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No redemptions yet. Earn 300 coins to redeem!",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = InterFamily,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            items(allItems) { item ->
                val isCompleted = item.status == "COMPLETED" && !item.admin_reply.isNullOrBlank()
                val isExpanded = expandedId == item.request_id
                val isFocused = focusedRequestId == item.request_id

                val infiniteTransition = rememberInfiniteTransition()
                val glowAlpha by if (isCompleted) {
                    infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 0.9f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "glowAlpha"
                    )
                } else {
                    remember { mutableFloatStateOf(0f) }
                }
                val pulseAlpha by if (isCompleted) {
                    infiniteTransition.animateFloat(
                        initialValue = 0.95f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                } else {
                    remember { mutableFloatStateOf(1f) }
                }

                val borderColor = if (isCompleted) {
                    Brush.linearGradient(
                        colors = listOf(
                            NeonGold.copy(alpha = glowAlpha),
                            NeonGold.copy(alpha = glowAlpha * 0.6f),
                            NeonGold.copy(alpha = glowAlpha)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(CardBorder, CardBorder)
                    )
                }
                val bgColor = if (isCompleted) {
                    Brush.linearGradient(
                        colors = listOf(
                            CardSurface.copy(alpha = 0.9f),
                            NeonGold.copy(alpha = 0.04f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(CardSurface, CardSurface)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .graphicsLayer(alpha = pulseAlpha)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable {
                            expandedId = if (isExpanded) null else item.request_id
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            val icon = when {
                                isCompleted -> Icons.Default.CheckCircle
                                item.status == "PENDING" || item.transaction_type == "REDEEM_PROCESSING" -> Icons.Default.HourglassTop
                                item.status == "REJECTED" -> Icons.Default.Cancel
                                else -> Icons.Default.SwapHoriz
                            }
                            val iconTint = when {
                                isCompleted -> NeonGold
                                item.status == "PENDING" || item.transaction_type == "REDEEM_PROCESSING" -> NeonYellow
                                item.status == "REJECTED" -> NeonRed
                                else -> TextSecondary
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = item.status,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Request #${item.request_id}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${item.reward_name ?: "Pack"} • ${item.coin_cost} Coins → ₹${String.format("%.2f", item.payout_value)}",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = InterFamily
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when {
                                isCompleted -> NeonGold
                                item.status == "PENDING" -> NeonYellow
                                item.status == "REJECTED" -> NeonRed
                                else -> NeonGreen
                            }
                            val statusBg = if (isCompleted) NeonGold.copy(alpha = 0.15f)
                            else statusColor.copy(alpha = 0.15f)
                            val statusBorder = if (isCompleted) NeonGold.copy(alpha = glowAlpha)
                            else statusColor
                            Box(
                                modifier = Modifier
                                    .background(statusBg, RoundedCornerShape(4.dp))
                                    .border(1.dp, statusBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isCompleted) "COMPLETED" else item.status,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = "Expand",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            HorizontalDivider(color = CardBorder, modifier = Modifier.padding(bottom = 12.dp))

                            DetailRow("Request ID", "#${item.request_id}")
                            DetailRow("Pack", item.reward_name ?: "N/A")
                            DetailRow("Coins", "${item.coin_cost}")
                            DetailRow("Amount", "₹${String.format("%.2f", item.payout_value)}")
                            DetailRow("Status", item.status)
                            DetailRow("Submitted", item.created_at)
                            if (item.completed_at != null) {
                                DetailRow("Completed", item.completed_at)
                            }

                            if (!item.admin_reply.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "CEO REPLY",
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.admin_reply,
                                    color = NeonGreen,
                                    fontSize = 13.sp,
                                    fontFamily = InterFamily,
                                    lineHeight = 18.sp
                                )
                            }

                            if (item.code_value != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "REWARD CODE",
                                    color = NeonGold,
                                    fontSize = 11.sp,
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.code_value,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (item.queue_id != null) {
                                DetailRow("Queue ID", item.queue_id)
                            }
                            DetailRow("Type", item.transaction_type)
                            if (item.description.isNotBlank()) {
                                DetailRow("Description", item.description)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            color = NeonCyan,
            fontSize = 12.sp,
            fontFamily = RajdhaniFamily,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = InterFamily
        )
    }
}
