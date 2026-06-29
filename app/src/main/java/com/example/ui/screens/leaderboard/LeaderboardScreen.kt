package com.example.ui.screens.leaderboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val selectedTab = uiState.selectedTab
    val listToDisplay = if (selectedTab == 0) uiState.weeklyList else uiState.allTimeList
    
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
        ) {
            // Header Tabs Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = NeonPink,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = NeonPink
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = {
                        Text(
                            text = "WEEKLY LEAGUE",
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (selectedTab == 0) NeonPink else TextMuted
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = {
                        Text(
                            text = "HALL OF FAME",
                            fontFamily = RajdhaniFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (selectedTab == 1) NeonPink else TextMuted
                        )
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp, start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Podium Section for Top 3
                if (listToDisplay.size >= 3) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Rank #2 (Left)
                            val r2 = listToDisplay[1]
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .threeDTiltEffect()
                                    .background(CardSurface, RoundedCornerShape(12.dp))
                                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🥈",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = r2.device_id.take(8),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = InterFamily,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${r2.coins} 🪙",
                                    color = NeonGold,
                                    fontSize = 14.sp,
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Rank #1 (Center, Elevated)
                            val r1 = listToDisplay[0]
                            Column(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .threeDTiltEffect()
                                    .background(CardSurface2, RoundedCornerShape(16.dp))
                                    .border(1.5.dp, NeonGold, RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "👑 🥇",
                                    fontSize = 28.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = r1.device_id.take(10),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = InterFamily,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${r1.coins} 🪙",
                                    color = NeonGold,
                                    fontSize = 18.sp,
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Rank #3 (Right)
                            val r3 = listToDisplay[2]
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .threeDTiltEffect()
                                    .background(CardSurface, RoundedCornerShape(12.dp))
                                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🥉",
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = r3.device_id.take(8),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = InterFamily,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${r3.coins} 🪙",
                                    color = NeonGold,
                                    fontSize = 14.sp,
                                    fontFamily = RajdhaniFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Ranks 4+ in lazy list
                itemsIndexed(listToDisplay.drop(3)) { index, item ->
                    LeaderboardRow(
                        rank = index + 4,
                        name = item.device_id,
                        coins = item.coins,
                        modifier = Modifier.threeDTiltEffect()
                    )
                }
            }
        }
        
        // Sticky Bottom "Your Rank" Card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp, start = 24.dp, end = 24.dp)
                .threeDTiltEffect()
                .shadow(12.dp, shape = RoundedCornerShape(16.dp), ambientColor = NeonPink, spotColor = NeonPink)
                .background(
                    brush = Brush.horizontalGradient(listOf(CardSurface, CardSurface2)),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.dp, NeonPink, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "YOUR STANDING",
                        color = NeonPink,
                        fontSize = 10.sp,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Rank #${uiState.userRank}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "${uiState.userBalance} 🪙",
                    color = NeonGold,
                    fontSize = 20.sp,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Loading state
        if (uiState.isLoading) {
            LoadingOverlay(message = "Synchronizing cyberpunk leaderboard matrix...")
        }
    }
}
