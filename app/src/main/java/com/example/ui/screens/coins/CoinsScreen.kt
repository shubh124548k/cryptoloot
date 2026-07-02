package com.example.ui.screens.coins

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.RewardPackCatalog
import android.util.Log
import com.example.data.repository.RedeemDeliveryType
import kotlinx.coroutines.delay
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
private fun DeliveryMethodOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .shadow(
                elevation = if (selected) 16.dp else 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (selected) NeonCyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.16f),
                spotColor = if (selected) NeonCyan.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.16f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) NeonCyan else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) NeonCyan.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.02f),
            contentColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (selected) NeonCyan.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) NeonCyan else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.74f),
                    fontFamily = InterFamily,
                    fontSize = 12.sp,
                    maxLines = 2,
                    softWrap = true,
                    lineHeight = 14.sp
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun CoinsScreen(
    viewModel: CoinsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Start polling for redemption status updates when screen is shown
    // Stop when screen is dismissed
    DisposableEffect(Unit) {
        viewModel.startRedemptionPolling()
        onDispose {
            viewModel.stopRedemptionPolling()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepDark)
    ) {
        AnimatedBackground()
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Giant Coin counter
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "COIN DEPOT",
                        color = NeonGold,
                        fontSize = 12.sp,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CoinCounter(
                        coins = uiState.coinBalance,
                        size = CoinCounterSize.LARGE
                    )
                }
            }
            
            // Tiers Section header
            item {
                Text(
                    text = "REDEEM REWARDS PACKS",
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            
            items(uiState.rewardPacks) { pack ->
                val badgeColor = when (pack.id) {
                    1 -> NeonCyan
                    2 -> NeonPurple
                    3 -> NeonGold
                    4 -> NeonPink
                    else -> NeonCyan
                }
                val badgeText = when (pack.id) {
                    1 -> "BRONZE TIER 🥉"
                    2 -> "SILVER TIER 🥈"
                    3 -> "GOLD TIER 🥇"
                    4 -> "DIAMOND TIER 💎"
                    5 -> "ELITE TIER 👑"
                    else -> "REWARD TIER"
                }
                RedemptionCard(
                    title = pack.name,
                    coinCost = pack.requiredCoins,
                    payoutText = "₹${pack.rewardAmount}",
                    badgeText = badgeText,
                    badgeColor = badgeColor,
                    currentBalance = uiState.coinBalance,
                    statusText = when {
                        uiState.activeRedeemStatus == null -> "READY"
                        uiState.activeRedeemStatus == "QUEUED" -> "QUEUED"
                        else -> "PROCESSING"
                    },
                    actionEnabled = uiState.activeRedeemStatus == null,
                    onRedeem = {
                        Log.e("REDEEM_TRACE", "STEP_01_TAP onRedeem pack=${pack.name} coins=${pack.requiredCoins}")
                        viewModel.openConfirmDialog(pack.requiredCoins, pack.rewardAmount.toFloat(), pack)
                    },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            

            
            item {
                val infiniteTransition = rememberInfiniteTransition()
                val alphaGlow by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .background(Color(0xFF0D1B2A), RoundedCornerShape(12.dp))
                        .border(
                            width = 2.dp,
                            color = Color(0xFF1E90FF).copy(alpha = alphaGlow),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/direct/t/18132025546537798"))
                            context.startActivity(intent)
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "If you had redeem coins for any rupees if code not came you can directly contact on instagram link",
                        color = NeonYellow.copy(alpha = alphaGlow),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Confirmation Dialog
        if (uiState.showConfirmDialog) {
            Log.d("TRACE", "ConfirmDialog shown selectedCoins=${uiState.selectedCoins} selectedPayout=${uiState.selectedPayout}")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(enabled = true, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(24.dp),
                    glowColor = NeonPink,
                    floatVariant = FloatVariant.NONE
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CONFIRM REDEMPTION",
                            color = NeonPink,
                            fontSize = 18.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Are you sure you want to redeem ${uiState.selectedCoins} coins for a ₹${String.format("%.2f", uiState.selectedPayout)} payout?",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = InterFamily,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // Delivery method chooser
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val selected = uiState.paymentDeliveryType
                            DeliveryMethodOptionCard(
                                title = "UPI ID",
                                subtitle = "Receive payout via UPI",
                                icon = Icons.Outlined.AccountBalanceWallet,
                                selected = selected == RedeemDeliveryType.UPI,
                                onClick = { viewModel.updatePaymentDestination(deliveryType = RedeemDeliveryType.UPI) }
                            )
                            DeliveryMethodOptionCard(
                                title = "Mobile Number",
                                subtitle = "Receive payout via mobile number",
                                icon = Icons.Outlined.PhoneAndroid,
                                selected = selected == RedeemDeliveryType.MOBILE,
                                onClick = { viewModel.updatePaymentDestination(deliveryType = RedeemDeliveryType.MOBILE) }
                            )
                            DeliveryMethodOptionCard(
                                title = "Redeem Code",
                                subtitle = "Receive a game or platform redeem code",
                                icon = Icons.Outlined.SportsEsports,
                                selected = selected == RedeemDeliveryType.REDEEM_CODE,
                                onClick = { viewModel.updatePaymentDestination(deliveryType = RedeemDeliveryType.REDEEM_CODE) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        when (uiState.paymentDeliveryType) {
                            RedeemDeliveryType.UPI -> {
                                OutlinedTextField(
                                    value = uiState.paymentDestinationUpi,
                                    onValueChange = { viewModel.updatePaymentDestination(upiId = it) },
                                    label = { Text("UPI ID") },
                                    placeholder = { Text("example@upi") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        focusedLabelColor = NeonCyan,
                                        cursorColor = NeonCyan
                                    ),
                                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                                )
                            }
                            RedeemDeliveryType.MOBILE -> {
                                OutlinedTextField(
                                    value = uiState.paymentDestinationMobile,
                                    onValueChange = { viewModel.updatePaymentDestination(mobileNumber = it) },
                                    label = { Text("Mobile Number") },
                                    placeholder = { Text("9876543210") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        focusedLabelColor = NeonCyan,
                                        cursorColor = NeonCyan
                                    ),
                                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                                )
                            }
                            RedeemDeliveryType.REDEEM_CODE -> {
                                OutlinedTextField(
                                    value = uiState.paymentRedeemCode,
                                    onValueChange = { viewModel.updatePaymentDestination(redeemCode = it) },
                                    label = { Text("Game / Platform") },
                                    placeholder = { Text("Example: Free Fire") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonCyan,
                                        focusedLabelColor = NeonCyan,
                                        cursorColor = NeonCyan
                                    ),
                                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (uiState.paymentErrorMessage != null) {
                            LaunchedEffect(uiState.paymentErrorMessage) {
                                delay(5000L)
                                viewModel.clearError()
                            }
                            val errorAlpha by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = tween(400),
                                label = "errorAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .graphicsLayer(alpha = errorAlpha)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xCC1A1A2E),
                                                Color(0xCC12121F)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.linearGradient(
                                            colors = listOf(NeonRed.copy(alpha = 0.8f), NeonRed.copy(alpha = 0.2f))
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Error",
                                            tint = NeonRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "REDEEM ERROR",
                                            color = NeonRed,
                                            fontSize = 14.sp,
                                            fontFamily = RajdhaniFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = uiState.paymentErrorMessage.orEmpty(),
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        fontFamily = InterFamily,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "DISMISS",
                                        color = NeonCyan,
                                        fontSize = 11.sp,
                                        fontFamily = RajdhaniFamily,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .clickable { viewModel.clearError() }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TextButton(
                                onClick = { viewModel.closeConfirmDialog() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CANCEL", color = TextSecondary, fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold)
                            }
                            NeonButton(
                                text = "CONFIRM ⚡",
                                onClick = {
                                    Log.e("REDEEM_TRACE", "STEP_02_CONFIRM CONFIRM button tapped")
                                    viewModel.executeRedemption()
                                },
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                    }
                }
            }
        }
        
        // Post-Redemption Success State overlay
        if (uiState.redemptionSuccessMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = true, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
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
                            text = "REDEMPTION SUBMITTED 🎉",
                            color = NeonGold,
                            fontSize = 20.sp,
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Box(
                            modifier = Modifier
                                .background(NeonYellow.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, NeonYellow, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⏳ PENDING APPROVAL",
                                color = NeonYellow,
                                fontSize = 12.sp,
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Request ID: #${uiState.createdRequestId}",
                            color = Color.White,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = uiState.redemptionSuccessMessage ?: "",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = InterFamily,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        NeonButton(
                            text = "DISMISS",
                            onClick = { viewModel.dismissSuccessState() },
                            style = NeonButtonStyle.PRIMARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        // Error toast overlay
        if (uiState.ratesError != null) {
            ToastOverlay(
                toast = ToastMessage(message = uiState.ratesError!!, type = ToastType.ERROR),
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        
        // Loading state
        if (uiState.isLoading) {
            LoadingOverlay(message = "Routing transaction on ledger...")
        }
    }
}
