package com.example

/*
 * ==============================================================================================
 * KRYPTOLOOT ENTERPRISE PRODUCTION LAUNCHER META CONFIG & BRAND LOGO INTEGRATION
 * ==============================================================================================
 * INSTRUCTIONS FOR LOGO MAPPING:
 * 1. The custom fiery circular shield logo (with KL inside, gaming style) must be exported 
 *    as `ic_launcher.png` and `ic_launcher_round.png`.
 * 2. Place these image assets directly into the corresponding `res/mipmap-[density]` directories.
 * 3. Ensure `AndroidManifest.xml` explicitly references them inside the <application> tags:
 *      android:icon="@mipmap/ic_launcher"
 *      android:roundIcon="@mipmap/ic_launcher_round"
 * 4. This enforces proper high-resolution rendering of our neon branding on all Android home screens.
 * ==============================================================================================
 */

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.api.NetworkClient
import com.example.data.local.UserPreferences
import com.example.data.repository.AdRepository
import com.example.data.repository.LeaderboardRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.RewardRepository
import com.example.data.repository.RewardsRepository
import com.example.data.repository.SyncRepository
import com.example.data.repository.TransactionRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.BottomNavBar
import com.example.ui.components.threeDTiltEffect
import com.example.ui.theme.KryptoLootTheme
import com.example.ui.screens.coins.CoinsScreen
import com.example.ui.screens.coins.CoinsViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.leaderboard.LeaderboardScreen
import com.example.ui.screens.leaderboard.LeaderboardViewModel
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.watchad.WatchAdScreen
import com.example.ui.screens.watchad.WatchAdViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.lifecycleScope
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

class MainActivity : ComponentActivity() {

    private var rewardedAd: StartAppAd? = null
    private var isAdLoading = false
    private var adLoadRetryCount = 0
    private var adRetryCooldownJob: Job? = null
    private var adLoadTimeoutJob: Job? = null
    private val _adStateText = MutableStateFlow("CACHING PREMIUM ADSTREAM...")
    private val _isAdReady = MutableStateFlow(false)
    private val _isAdRetryAvailable = MutableStateFlow(false)

    private fun isRewardedAdReady(ad: StartAppAd?): Boolean {
        if (ad == null) return false
        return runCatching {
            ad.javaClass.getMethod("isReady").invoke(ad) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun loadRewardedAd() {
        if (isRewardedAdReady(rewardedAd) || isAdLoading) return
        adLoadTimeoutJob?.cancel()
        isAdLoading = true
        _isAdRetryAvailable.value = false
        _adStateText.value = "CACHING PREMIUM ADSTREAM..."
        _isAdReady.value = false

        adLoadTimeoutJob = lifecycleScope.launch {
            delay(8000L)
            if (isAdLoading && !isRewardedAdReady(rewardedAd)) {
                isAdLoading = false
                _isAdReady.value = false
                _adStateText.value = "No ads available. Please try again later."
                _isAdRetryAvailable.value = true
            }
        }

        val ad = StartAppAd(this)
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(p0: Ad) {
                rewardedAd = ad
                adLoadTimeoutJob?.cancel()
                isAdLoading = false
                adLoadRetryCount = 0
                _adStateText.value = "WATCH FULL AD TO EARN 1 COIN"
                _isAdReady.value = true
                _isAdRetryAvailable.value = false
            }

            override fun onFailedToReceiveAd(p0: Ad?) {
                rewardedAd = null
                adLoadTimeoutJob?.cancel()
                isAdLoading = false
                adLoadRetryCount += 1
                _adStateText.value = "No ads available. Please try again later."
                _isAdReady.value = false
                _isAdRetryAvailable.value = true
            }
        })
    }

    private fun requestAdAfterDelay() {
        if (isAdLoading || adRetryCooldownJob?.isActive == true) return
        adRetryCooldownJob?.cancel()
        adRetryCooldownJob = lifecycleScope.launch {
            delay(15000L)
            loadRewardedAd()
        }
    }

    private fun showRewardedAd(onRewardEarned: () -> Unit, onAdFailedToShow: (String) -> Unit) {
        val ad = rewardedAd ?: return onAdFailedToShow("No ads available. Please try again later.")
        if (isRewardedAdReady(ad)) {
            var rewardHandled = false
            ad.setVideoListener(object : VideoListener {
                override fun onVideoCompleted() {
                    if (rewardHandled) return
                    rewardHandled = true
                    onRewardEarned()
                }
            })
            val displayed = ad.showAd(object : AdDisplayListener {
                override fun adHidden(p0: Ad) {
                    rewardedAd = null
                    _isAdReady.value = false
                    _adStateText.value = "CACHING PREMIUM ADSTREAM..."
                    _isAdRetryAvailable.value = false
                    loadRewardedAd()
                }
                override fun adDisplayed(p0: Ad) {}
                override fun adClicked(p0: Ad) {}
                override fun adNotDisplayed(p0: Ad) {
                    rewardedAd = null
                    _isAdReady.value = false
                    _adStateText.value = "CACHING PREMIUM ADSTREAM..."
                    _isAdRetryAvailable.value = false
                    loadRewardedAd()
                    onAdFailedToShow("Ad failed to show")
                }
            })
            if (!displayed) {
                rewardedAd = null
                _isAdReady.value = false
                _adStateText.value = "CACHING PREMIUM ADSTREAM..."
                _isAdRetryAvailable.value = false
                loadRewardedAd()
                onAdFailedToShow("Ad failed to show")
            }
        } else if (_isAdRetryAvailable.value) {
            requestAdAfterDelay()
            onAdFailedToShow("No ads available. Please try again later.")
        } else {
            _adStateText.value = "No ads available. Please try again later."
            _isAdReady.value = false
            _isAdRetryAvailable.value = true
            onAdFailedToShow("No ads available. Please try again later.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize StartApp SDK
        StartAppSDK.init(this, "205482155")
        loadRewardedAd()
        
        // Setup direct constructor injection
        val prefs = UserPreferences(applicationContext)
        val api = NetworkClient.api
        val userRepo = UserRepository(applicationContext, api, prefs)
        val adRepo = AdRepository(api, prefs, userRepo)
        val rewardsRepo = RewardsRepository(api, prefs, userRepo)
        val rewardRepo = RewardRepository(userRepo)
        val transactionRepo = TransactionRepository(prefs, userRepo)
        val leaderboardRepo = LeaderboardRepository(userRepo, prefs)
        val notificationRepo = NotificationRepository(prefs)
        val syncRepo = SyncRepository(prefs, userRepo, rewardRepo, transactionRepo, leaderboardRepo, notificationRepo)
        userRepo.attachRepositories(rewardRepo, transactionRepo, leaderboardRepo, notificationRepo, syncRepo)
        
        // Instantiation of view models
        val homeViewModel = HomeViewModel(userRepo, adRepo)
        val watchAdViewModel = WatchAdViewModel(adRepo, userRepo)
        val coinsViewModel = CoinsViewModel(rewardsRepo, userRepo)
        val leaderboardViewModel = LeaderboardViewModel(leaderboardRepo, userRepo)
        val sharedAppViewModel = SharedAppViewModel(userRepo)
        
        setContent {
            KryptoLootTheme {
                val navController = rememberNavController()
                
                NavHost(
                    navController = navController,
                    startDestination = "handshake_boot"
                ) {
                    composable("handshake_boot") {
                        HandshakeBootScreen(
                            userRepo = userRepo,
                            onComplete = { response ->
                                response.daily_reset_seconds_remaining?.let { 
                                    sharedAppViewModel.setInitialCountdown(it)
                                }
                                homeViewModel.refreshData()
                                navController.navigate("home_dashboard") {
                                    popUpTo("handshake_boot") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("home_dashboard") {
                        var currentTab by remember { mutableStateOf("home") }
                        val coroutineScope = rememberCoroutineScope()
                        
                        val isAdReady by _isAdReady.collectAsState()
                        val isAdRetryAvailable by _isAdRetryAvailable.collectAsState()
                        val adStateText by _adStateText.collectAsState()
                        val dailyResetSeconds by sharedAppViewModel.dailyResetTimerState.collectAsState()
                        
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                if (currentTab != "watch_ad") {
                                    BottomNavBar(
                                        currentRoute = currentTab,
                                        onNavigate = { route ->
                                            currentTab = route
                                            if (route == "home") homeViewModel.refreshData()
                                            if (route == "coins") coinsViewModel.loadData()
                                            if (route == "leaderboard") leaderboardViewModel.loadLeaderboard()
                                        }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when (currentTab) {
                                    "home" -> {
                                        HomeScreen(
                                            viewModel = homeViewModel,
                                            onNavigate = { route ->
                                                if (route == "watch_ad") {
                                                    currentTab = "watch_ad"
                                                } else {
                                                    currentTab = route
                                                }
                                            }
                                        )
                                    }
                                    "watch_ad" -> {
                                        WatchAdScreen(
                                            viewModel = watchAdViewModel,
                                            isAdReady = isAdReady,
                                            isRetryAvailable = isAdRetryAvailable,
                                            adStateText = adStateText,
                                            dailyResetSeconds = dailyResetSeconds,
                                            onWatchAdClick = {
                                                showRewardedAd(
                                                    onRewardEarned = {
                                                        watchAdViewModel.onRealAdCompleted()
                                                    },
                                                    onAdFailedToShow = { msg ->
                                                        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            },
                                            onNavigate = { route -> currentTab = route }
                                        )
                                    }
                                    "coins" -> {
                                        CoinsScreen(viewModel = coinsViewModel)
                                    }
                                    "leaderboard" -> {
                                        LeaderboardScreen(viewModel = leaderboardViewModel)
                                    }
                                    "profile" -> {
                                        val appState by userRepo.appState.collectAsState()
                                        ProfileScreen(
                                            userCoins = appState.coinBalance,
                                            adsWatched = appState.dailyAdsWatched,
                                            trustScore = appState.trustScore,
                                            deviceId = appState.deviceId,
                                            displayName = appState.displayName ?: userRepo.getDisplayName(),
                                            photoUrl = appState.photoUrl,
                                            masterUid = appState.masterUid ?: userRepo.getMasterUid(),
                                            pendingRedeems = appState.redeemStats.pendingCount,
                                            completedRedeems = appState.redeemStats.completedCount,
                                            rejectedRedeems = appState.redeemStats.rejectedCount,
                                            lastRedeemDate = appState.redeemStats.lastRedeemAt,
                                            totalLifetimeRedeems = appState.redeemStats.totalLifetimeRedeems,
                                            totalTransactions = appState.transactionStats.totalTransactions,
                                            coinsEarnedLifetime = appState.transactionStats.coinsEarnedLifetime,
                                            coinsRedeemedLifetime = appState.transactionStats.coinsRedeemedLifetime,
                                            lastTransactionTime = appState.transactionStats.lastTransactionTime,
                                            averageCoinsPerReward = appState.transactionStats.averageCoinsPerReward,
                                            currentRank = appState.leaderboardState.currentUserStanding.currentRank,
                                            currentLeague = appState.leaderboardState.stats.currentLeague,
                                            leaderboardScore = appState.leaderboardState.stats.leaderboardScore,
                                            onRecoverUid = { pastedUid, onResult ->
                                                coroutineScope.launch {
                                                    val response = userRepo.performUidRecovery(pastedUid)
                                                    if (response.status == "success") {
                                                        homeViewModel.refreshData()
                                                        coinsViewModel.loadData()
                                                        onResult(true, "Profile Recovered Successfully! ⚡")
                                                    } else {
                                                        onResult(false, response.message ?: "Recovery failed. Please verify the UID.")
                                                    }
                                                }
                                            },
                                            onResetApp = {
                                                userRepo.resetState()
                                                Toast.makeText(applicationContext, "App protocols reset!", Toast.LENGTH_SHORT).show()
                                                navController.navigate("handshake_boot") {
                                                    popUpTo("home_dashboard") { inclusive = true }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HandshakeBootScreen(
    userRepo: UserRepository,
    onComplete: (com.example.data.api.DeviceHandshakeResponse) -> Unit
) {
    var statusText by remember { mutableStateOf("CONNECTING TO SECURE KRYPTON GRID...") }
    var subtext by remember { mutableStateOf("Establishing encrypted handshake...") }
    
    LaunchedEffect(Unit) {
        delay(1200)
        statusText = "DECRYPTING HARDWARE TOKEN MATRIX..."
        subtext = "Device ID: ${userRepo.getDeviceId().take(16)}..."
        delay(1000)
        
        statusText = "EXECUTING SECURITY HANDSHAKE..."
        subtext = "POST /api/v1/auth/device-handshake"
        
        val startTime = System.currentTimeMillis()
        val response = userRepo.performDeviceHandshake()
        val duration = System.currentTimeMillis() - startTime
        
        val remainingDelay = (2500 - duration).coerceAtLeast(0)
        delay(remainingDelay)
        
        statusText = "HARDWARE SECURE HANDSHAKE COMPLETED ⚡"
        subtext = "Master UID: ${response.master_uid}"
        delay(1000)
        
        onComplete(response)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.DeepDark),
        contentAlignment = Alignment.Center
    ) {
        com.example.ui.components.AnimatedBackground()
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .threeDTiltEffect()
                    .background(com.example.ui.theme.NeonPink.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .border(2.dp, com.example.ui.theme.NeonPink, RoundedCornerShape(24.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Decryption Shield",
                    tint = com.example.ui.theme.NeonPink,
                    modifier = Modifier.size(45.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "KRYPTOLOOT GATEWAY",
                color = Color.White,
                fontSize = 28.sp,
                fontFamily = com.example.ui.theme.RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            
            Text(
                text = "HARDWARE-ANCHORED CODESYNC SEQUENCE",
                color = com.example.ui.theme.NeonCyan,
                fontSize = 11.sp,
                fontFamily = com.example.ui.theme.RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            CircularProgressIndicator(
                color = com.example.ui.theme.NeonCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = statusText,
                color = com.example.ui.theme.NeonCyan,
                fontSize = 12.sp,
                fontFamily = com.example.ui.theme.JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = subtext,
                color = com.example.ui.theme.TextMuted,
                fontSize = 10.sp,
                fontFamily = com.example.ui.theme.JetBrainsMonoFamily,
                textAlign = TextAlign.Center
            )
        }
    }
}
