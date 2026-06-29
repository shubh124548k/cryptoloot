package com.example.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    userCoins: Int,
    adsWatched: Int,
    trustScore: Int,
    deviceId: String,
    displayName: String,
    photoUrl: String?,
    masterUid: String,
    pendingRedeems: Int,
    completedRedeems: Int,
    rejectedRedeems: Int,
    lastRedeemDate: String?,
    totalLifetimeRedeems: Int,
    onRecoverUid: (String, (Boolean, String) -> Unit) -> Unit,
    onResetApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Toggles
    var pushNotifications by remember { mutableStateOf(true) }
    var soundEffects by remember { mutableStateOf(true) }
    var dataSaver by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDark)
    ) {
        AnimatedBackground()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 100.dp) // padding for bottom navigation
        ) {
            // Title
            Text(
                text = "USER WARRIOR PROFILE",
                color = NeonCyan,
                fontSize = 18.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.05.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // User Identity Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.linearGradient(listOf(NeonPink, NeonPurple))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Large Avatar
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color.White.copy(alpha = 0.2f), shape = CircleShape)
                            .border(1.5.dp, Color.White, shape = CircleShape)
                    ) {
                        val initials = if (displayName.length >= 2) displayName.take(2).uppercase() else "KL"
                        Text(
                            text = initials,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("device_id", deviceId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "ID: ${deviceId.take(12)}...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    
                    // Account status pill
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Glowing Gold Card for Master UID
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .threeDTiltEffect()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF221A0F)) // Dark gold background
                    .border(2.dp, Color(0xFFFF9900), RoundedCornerShape(16.dp)) // Glowing Orange-Gold border
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "YOUR KRYPTOLOOT MASTER UID",
                        color = Color(0xFFFF9900),
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = masterUid,
                            color = Color(0xFFFFD700), // Electric gold
                            fontSize = 20.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF9900).copy(alpha = 0.2f))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("master_uid", masterUid)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Master UID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy UID",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "COPY",
                                    color = Color(0xFFFFD700),
                                    fontSize = 10.sp,
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secure Recovery Input Panel
            var recoveryInput by remember { mutableStateOf("") }
            var isRecovering by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, NeonOrange.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SECURE ACCOUNT RECOVERY PROTOCOL",
                        color = NeonOrange,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Paste your previous account UID below to link your historical balances and credentials to this physical hardware identity.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = InterFamily
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it.uppercase().trim() },
                        label = { Text("PASTE PREVIOUS ACCOUNT UID", fontFamily = RajdhaniFamily, fontSize = 12.sp) },
                        placeholder = { Text("KL-XXXX-XXXX", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonOrange,
                            unfocusedBorderColor = CardBorder,
                            focusedLabelColor = NeonOrange,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = DeepDark,
                            unfocusedContainerColor = DeepDark
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (recoveryInput.isEmpty() || !recoveryInput.startsWith("KL-")) {
                                Toast.makeText(context, "Please enter a valid UID (format: KL-XXXX-XXXX)", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            isRecovering = true
                            onRecoverUid(recoveryInput) { success, message ->
                                isRecovering = false
                                if (success) {
                                    recoveryInput = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonOrange,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isRecovering
                    ) {
                        if (isRecovering) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "RECOVER DATA ⚡",
                                fontSize = 14.sp,
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "REDEEM QUEUE METRICS",
                color = NeonCyan,
                fontSize = 12.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeonCard(
                glowColor = NeonCyan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatMiniCard(
                            icon = Icons.Default.HourglassTop,
                            iconColor = NeonYellow,
                            value = "$pendingRedeems",
                            label = "Pending",
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniCard(
                            icon = Icons.Default.CheckCircle,
                            iconColor = NeonGreen,
                            value = "$completedRedeems",
                            label = "Completed",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatMiniCard(
                            icon = Icons.Default.Report,
                            iconColor = NeonRed,
                            value = "$rejectedRedeems",
                            label = "Rejected",
                            modifier = Modifier.weight(1f)
                        )
                        StatMiniCard(
                            icon = Icons.Default.Replay,
                            iconColor = NeonPurple,
                            value = "$totalLifetimeRedeems",
                            label = "Lifetime",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Last Redeem: ${lastRedeemDate ?: "N/A"}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = InterFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Stats Title
            Text(
                text = "OPERATIONAL PERFORMANCE",
                color = NeonCyan,
                fontSize = 12.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // 2x3 Grid using Columns and Rows for better custom scroll integration
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatMiniCard(
                        icon = Icons.Default.Tv,
                        iconColor = NeonPink,
                        value = "$adsWatched",
                        label = "Ads Watched",
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniCard(
                        icon = Icons.Default.MonetizationOn,
                        iconColor = NeonGold,
                        value = "$userCoins 🪙",
                        label = "Coin Balance",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val rupeeVal = userCoins * 0.0333
                    StatMiniCard(
                        icon = Icons.Default.Paid,
                        iconColor = NeonGreen,
                        value = "₹${String.format("%.2f", rupeeVal)}",
                        label = "Total Cash Val",
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniCard(
                        icon = Icons.Default.Shield,
                        iconColor = NeonCyan,
                        value = "$trustScore",
                        label = "Trust Rating",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatMiniCard(
                        icon = Icons.Default.CalendarMonth,
                        iconColor = NeonYellow,
                        value = "June 2026",
                        label = "Enlisted Since",
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniCard(
                        icon = Icons.Default.Leaderboard,
                        iconColor = NeonPurple,
                        value = "#11",
                        label = "League Standing",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Trust Score Section
            Text(
                text = "TRUST DECREE POLICY",
                color = NeonCyan,
                fontSize = 12.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeonCard(
                glowColor = NeonCyan,
                modifier = Modifier.fillMaxWidth().threeDTiltEffect()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrustScoreGauge(
                            score = trustScore,
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "What affects your rating?",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• VPN use: -25 points\n• Emulator use: -25 points\n• Skipping ads: -15 points\n• Honest use: +2 points/day recovery (Max 100)",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = InterFamily,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Settings List
            Text(
                text = "WAR-ROOM SYSTEM SETTINGS",
                color = NeonCyan,
                fontSize = 12.sp,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            NeonCard(
                glowColor = NeonCyan,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Setting 1: Dark mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dark Mode Theme", color = Color.White, fontSize = 14.sp, fontFamily = InterFamily, fontWeight = FontWeight.Bold)
                        Text("Always enabled for Cyberpunk theme", color = TextMuted, fontSize = 10.sp, fontFamily = InterFamily)
                    }
                    Switch(checked = true, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = NeonPink))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Setting 2: Push notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Push Alerts", color = Color.White, fontSize = 14.sp, fontFamily = InterFamily)
                    Switch(
                        checked = pushNotifications,
                        onCheckedChange = { pushNotifications = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonPink, checkedTrackColor = NeonPink.copy(0.3f))
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Setting 3: Sound effects
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound Effects", color = Color.White, fontSize = 14.sp, fontFamily = InterFamily)
                    Switch(
                        checked = soundEffects,
                        onCheckedChange = { soundEffects = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Setting 4: Data saver
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Data Saver Mode", color = TextSecondary, fontSize = 14.sp, fontFamily = InterFamily)
                    Switch(
                        checked = dataSaver,
                        onCheckedChange = { dataSaver = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Clear Cache
                Text(
                    text = "Clear Client Cache",
                    color = NeonYellow,
                    fontSize = 14.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Toast
                                .makeText(context, "System cache cleaned successfully!", Toast.LENGTH_SHORT)
                                .show()
                        }
                        .padding(vertical = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Reset app
                Text(
                    text = "RESET KRYPTOLOOT PROTOCOLS",
                    color = NeonRed,
                    fontSize = 14.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResetApp() }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StatMiniCard(
    icon: ImageVector,
    iconColor: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier.threeDTiltEffect(),
        glowColor = iconColor,
        floatVariant = FloatVariant.NONE
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = InterFamily
            )
        }
    }
}
