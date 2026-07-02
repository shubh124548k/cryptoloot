package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.NotificationItem
import com.example.data.repository.NotificationRepository
import com.example.ui.theme.*

@Composable
fun NotificationBell(
    notificationRepo: NotificationRepository,
    onClick: () -> Unit
) {
    val notifications by notificationRepo.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CardSurface.copy(alpha = 0.85f))
            .border(1.dp, CardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (unreadCount > 0) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
            contentDescription = "Notifications",
            tint = if (unreadCount > 0) NeonCyan else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(18.dp)
                    .background(NeonRed, CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun NotificationPanel(
    notificationRepo: NotificationRepository,
    onDismiss: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit
) {
    val notifications by notificationRepo.notifications.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 60.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardSurface2.copy(alpha = 0.95f))
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clickable(enabled = false) { }
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NOTIFICATIONS",
                        color = NeonCyan,
                        fontSize = 14.sp,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (notifications.any { !it.isRead }) {
                            TextButton(onClick = { notificationRepo.markAllAsRead() }) {
                                Text("Mark all read", color = NeonCyan, fontSize = 11.sp, fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(onClick = onDismiss)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (notifications.isEmpty()) {
                    Text(
                        text = "No notifications yet.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontFamily = InterFamily,
                        modifier = Modifier.padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(notifications) { item ->
                            NotificationCard(
                                item = item,
                                onClick = {
                                    notificationRepo.markAsRead(item.id)
                                    onNotificationClick(item)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (item.isRead) CardSurface.copy(alpha = 0.5f) else CardSurface)
            .border(1.dp, if (item.isRead) CardBorder else NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!item.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(NeonCyan, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = item.title,
                color = if (item.isRead) TextSecondary else Color.White,
                fontSize = 13.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.message,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = InterFamily,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.timestamp,
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = InterFamily
        )
    }
}
