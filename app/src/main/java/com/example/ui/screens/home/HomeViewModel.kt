package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AdRepository
import com.example.data.repository.RewardPack
import com.example.data.repository.RewardPackCatalog
import com.example.data.repository.RedeemQueueEntry
import com.example.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val coinBalance: Int = 0,
    val adsWatchedToday: Int = 0,
    val sessionAds: Int = 0,
    val dailyCap: Int = 100,
    val sessionCap: Int = 15,
    val breakUntil: Long? = null,
    val trustScore: Int = 100,
    val operationalStatus: String = "ACTIVE",
    val estimatedRupeeValue: String = "₹0.00",
    val nextRewardCoins: Int = 300,
    val totalCoinsEarned: Int = 0,
    val rewardPacks: List<RewardPack> = emptyList(),
    val redeemQueue: List<RedeemQueueEntry> = emptyList(),
    val activeRedeemStatus: String? = null,
    val activeQueuePosition: Int? = null,
    val estimatedQueueWait: String? = null
)

class HomeViewModel(
    private val userRepo: UserRepository,
    private val adRepo: AdRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var initializeJob: Job? = null

    init {
        userRepo.appState.onEach { snapshot ->
            val rupeeVal = snapshot.coinBalance * 0.0333f
            _uiState.update {
                it.copy(
                    isLoading = false,
                    coinBalance = snapshot.coinBalance,
                    adsWatchedToday = snapshot.dailyAdsWatched,
                    sessionAds = snapshot.sessionAds,
                    breakUntil = snapshot.breakUntil,
                    trustScore = snapshot.trustScore,
                    operationalStatus = snapshot.operationalStatus,
                    estimatedRupeeValue = "₹${String.format("%.2f", rupeeVal)}",
                    totalCoinsEarned = snapshot.totalCoinsEarned.coerceAtLeast(snapshot.coinBalance),
                    rewardPacks = RewardPackCatalog.fromBalance(snapshot.coinBalance),
                    redeemQueue = snapshot.redeemRequests.map { request ->
                        RedeemQueueEntry(
                            id = request.id,
                            userUid = request.userUid,
                            username = request.username,
                            packId = request.packId,
                            packName = request.packName,
                            coinCost = request.coinsUsed,
                            rewardAmount = request.rewardAmount,
                            requestTimestamp = request.requestTimestamp.ifEmpty { request.createdAt },
                            currentStatus = request.status,
                            processingPosition = request.queuePosition,
                            estimatedProcessingTime = request.estimatedProcessingTime,
                            previousBalance = snapshot.coinBalance + request.coinsUsed,
                            currentBalance = snapshot.coinBalance,
                            createdAt = request.createdAt,
                            updatedAt = request.updatedAt
                        )
                    },
                    activeRedeemStatus = snapshot.redeemStats.activeStatus,
                    activeQueuePosition = snapshot.redeemStats.activeQueuePosition,
                    estimatedQueueWait = snapshot.redeemStats.estimatedWaitText
                )
            }
        }.launchIn(viewModelScope)
        initialize()
    }

    fun initialize() {
        initializeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        initializeJob = viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(8000L) { userRepo.performHandshake() }
                if (response == null) {
                    userRepo.refreshFromPreferences()
                    _uiState.update { it.copy(error = "Timed out while syncing with KryptoLoot server.") }
                } else {
                    val breakTime = userRepo.getCurrentSnapshot().breakUntil
                    if (breakTime != null && breakTime > System.currentTimeMillis()) {
                        startBreakCountdown(breakTime)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                userRepo.refreshFromPreferences()
                _uiState.update { it.copy(error = "Timed out while syncing with KryptoLoot server.") }
            } catch (e: Exception) {
                userRepo.refreshFromPreferences()
                _uiState.update { it.copy(error = "Failed to sync with KryptoLoot server.") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshData() {
        initialize()
    }

    private fun startBreakCountdown(breakUntilMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val diff = breakUntilMs - now
                if (diff <= 0) {
                    _uiState.update { it.copy(breakUntil = null) }
                    break
                }
                _uiState.update { it.copy(breakUntil = breakUntilMs) }
                delay(1000)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
