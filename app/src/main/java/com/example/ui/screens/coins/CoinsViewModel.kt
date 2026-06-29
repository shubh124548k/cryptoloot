package com.example.ui.screens.coins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RedemptionHistoryItem
import com.example.data.repository.RewardPack
import com.example.data.repository.RewardPackCatalog
import com.example.data.repository.RedeemQueueEntry
import com.example.data.repository.RewardsRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class CoinsUiState(
    val isLoading: Boolean = false,
    val coinBalance: Int = 0,
    val ratesError: String? = null,
    val historyList: List<RedemptionHistoryItem> = emptyList(),
    val showConfirmDialog: Boolean = false,
    val selectedCoins: Int = 0,
    val selectedPayout: Float = 0f,
    val redemptionSuccessMessage: String? = null,
    val createdRequestId: Int? = null,
    val successStatus: String? = null,
    val rewardPacks: List<RewardPack> = emptyList(),
    val redeemQueue: List<RedeemQueueEntry> = emptyList(),
    val activeRedeemStatus: String? = null,
    val activeQueuePosition: Int? = null,
    val estimatedQueueWait: String? = null,
    val pendingRedeemCount: Int = 0
)

class CoinsViewModel(
    private val rewardsRepo: RewardsRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoinsUiState())
    val uiState: StateFlow<CoinsUiState> = _uiState.asStateFlow()

    private var redeemJob: Job? = null
    private var selectedPack: RewardPack? = null

    init {
        userRepo.appState.onEach { snapshot ->
            _uiState.update {
                it.copy(
                    coinBalance = snapshot.coinBalance,
                    historyList = snapshot.transactionHistory.map { tx ->
                        RedemptionHistoryItem(
                            request_id = tx.id.filter { it.isDigit() }.toIntOrNull() ?: 0,
                            coin_cost = tx.amount,
                            payout_value = tx.cashAmount ?: tx.rewardAmount?.toFloat() ?: 0f,
                            status = tx.status,
                            code_value = null,
                            created_at = tx.timestamp,
                            transaction_type = tx.transactionType.name,
                            description = tx.description.ifEmpty { tx.message.orEmpty() },
                            queue_id = tx.queueId,
                            coins_before = tx.coinsBefore,
                            coins_after = tx.coinsAfter,
                            completed_at = tx.completedTimestamp,
                            device_id = tx.deviceId,
                            server_synced = tx.serverSyncFlag,
                            version_number = tx.versionNumber,
                            reward_name = tx.rewardPack ?: tx.rewardName,
                            cash_amount = tx.cashAmount ?: tx.rewardAmount?.toFloat()
                        )
                    },
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
                    estimatedQueueWait = snapshot.redeemStats.estimatedWaitText,
                    pendingRedeemCount = snapshot.redeemStats.pendingCount
                )
            }
        }.launchIn(viewModelScope)
        loadData()
    }

    fun loadData() {
        _uiState.update { it.copy(coinBalance = userRepo.getLocalCoins(), isLoading = true) }
        viewModelScope.launch {
            try {
                withTimeoutOrNull(8000L) { rewardsRepo.getRedemptionHistory() }
                userRepo.refreshFromPreferences()
                _uiState.update {
                    it.copy(isLoading = false)
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        ratesError = "Timed out while syncing historical transactions."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        ratesError = "Failed to sync historical transactions with KryptoLoot backend."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun openConfirmDialog(coins: Int, payout: Float, pack: RewardPack? = null) {
        if (redeemJob?.isActive == true) return
        selectedPack = pack
        _uiState.update {
            it.copy(
                showConfirmDialog = true,
                selectedCoins = coins,
                selectedPayout = payout
            )
        }
    }

    fun closeConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun executeRedemption() {
        val coins = _uiState.value.selectedCoins
        if (coins <= 0 || redeemJob?.isActive == true) return
        val pack = selectedPack
        if (pack == null) {
            _uiState.update { it.copy(ratesError = "Selected reward pack is unavailable.") }
            return
        }

        redeemJob?.cancel()
        _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }
        redeemJob = viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(8000L) { rewardsRepo.redeemCoins(coins, pack) }
                if (response == null) {
                    _uiState.update {
                        it.copy(
                            ratesError = "Timed out while processing redemption request."
                        )
                    }
                } else if (response.success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            redemptionSuccessMessage = response.message,
                            createdRequestId = response.request_id,
                            successStatus = response.status,
                            coinBalance = userRepo.getLocalCoins()
                        )
                    }
                    loadData()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            ratesError = response.message
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        ratesError = "Timed out while processing redemption request."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        ratesError = "Unable to process redemption request. Check connectivity."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun dismissSuccessState() {
        _uiState.update {
            it.copy(
                redemptionSuccessMessage = null,
                createdRequestId = null,
                successStatus = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(ratesError = null) }
    }
}
