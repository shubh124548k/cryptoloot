package com.example.ui.screens.coins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RedemptionHistoryItem
import com.example.data.repository.RedeemDeliveryType
import com.example.data.repository.RedeemDestination
import com.example.data.repository.RedeemPaymentRepository
import com.example.data.repository.RedeemQueueEntry
import com.example.data.repository.RewardPack
import com.example.data.repository.RewardPackCatalog
import com.example.data.repository.RewardsRepository
import com.example.data.repository.TransactionType
import com.example.data.repository.UserRepository
import kotlinx.coroutines.Job
import android.util.Log
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
    val pendingRedeemCount: Int = 0,
    val paymentDestinationUpi: String = "",
    val paymentDestinationMobile: String = "",
    val paymentRedeemCode: String = "",
    val paymentDeliveryType: RedeemDeliveryType = RedeemDeliveryType.UPI,
    val paymentErrorMessage: String? = null
)

class CoinsViewModel(
    private val rewardsRepo: RewardsRepository,
    private val userRepo: UserRepository,
    private val paymentRepo: RedeemPaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoinsUiState())
    val uiState: StateFlow<CoinsUiState> = _uiState.asStateFlow()

    private val _focusedRequestId = MutableStateFlow<Int?>(null)
    val focusedRequestId: StateFlow<Int?> = _focusedRequestId.asStateFlow()

    fun focusRequest(id: Int?) {
        _focusedRequestId.value = id
    }

    fun consumeFocus() {
        _focusedRequestId.value = null
    }

    private var redeemJob: Job? = null
    private var selectedPack: RewardPack? = null

    init {
        userRepo.appState.onEach { snapshot ->
            val redemptionItems = snapshot.transactionHistory.filter {
                it.transactionType == TransactionType.QUEUE_COMPLETED || it.transactionType == TransactionType.REDEEM_REQUEST
            }
            Log.d("SYNC_TRACE", "CoinsViewModel.onEach: snapshot.transactionHistory.size=${snapshot.transactionHistory.size} redemptionItems=$redemptionItems")
            redemptionItems.forEach { tx ->
                Log.d("SYNC_TRACE", "CoinsViewModel.onEach:   tx queueId=${tx.queueId} type=${tx.transactionType.name} status=${tx.status} adminReply='${tx.adminReply}'")
            }
            _uiState.update {
                it.copy(
                    coinBalance = snapshot.coinBalance,
                    historyList = snapshot.transactionHistory.map { tx ->
                        RedemptionHistoryItem(
                            request_id = tx.id.substringAfter("txn-").substringBefore("-").toIntOrNull() ?: tx.queueId?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
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
                            cash_amount = tx.cashAmount ?: tx.rewardAmount?.toFloat(),
                            admin_reply = tx.adminReply
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
            val currentHistory = _uiState.value.historyList
            Log.d("SYNC_TRACE", "CoinsViewModel.onEach: AFTER UPDATE historyList.size=${currentHistory.size}")
            currentHistory.forEach { item ->
                Log.d("SYNC_TRACE", "CoinsViewModel.onEach:   history item queue_id=${item.queue_id} type=${item.transaction_type} status=${item.status} admin_reply='${item.admin_reply}'")
            }
        }.launchIn(viewModelScope)
        loadData()
    }

    fun loadData() {
        Log.d("SYNC_TRACE", "loadData: CALLED")
        _uiState.update { it.copy(coinBalance = userRepo.getLocalCoins(), isLoading = true) }
        viewModelScope.launch {
            Log.d("SYNC_TRACE", "loadData: coroutine started, calling getRedemptionHistory()")
            try {
                val result = withTimeoutOrNull(8000L) { rewardsRepo.getRedemptionHistory() }
                Log.d("SYNC_TRACE", "loadData: getRedemptionHistory returned result=${if (result == null) "TIMEOUT" else "list(${result.size})"}")
                userRepo.refreshFromPreferences()
                Log.d("SYNC_TRACE", "loadData: after refreshFromPreferences, current historyList size=${_uiState.value.historyList.size}")
                _uiState.update {
                    it.copy(isLoading = false)
                }
            } catch (e: TimeoutCancellationException) {
                Log.d("SYNC_TRACE", "loadData: TIMEOUT exception: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        ratesError = "Timed out while syncing historical transactions."
                    )
                }
            } catch (e: Exception) {
                Log.d("SYNC_TRACE", "loadData: EXCEPTION ${e::class.simpleName}: ${e.message}")
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
        if (redeemJob?.isActive == true) {
            Log.e("REDEEM_TRACE", "STEP_03_REJECTED openConfirmDialog blocked: redeemJob active")
            return
        }
        selectedPack = pack
        Log.e("REDEEM_TRACE", "STEP_03_OPEN openConfirmDialog pack=${pack?.name} coins=$coins payout=$payout")
        _uiState.update {
            it.copy(
                showConfirmDialog = true,
                selectedCoins = coins,
                selectedPayout = payout,
                paymentErrorMessage = null,
                paymentDestinationUpi = "",
                paymentDestinationMobile = "",
                paymentRedeemCode = "",
                paymentDeliveryType = RedeemDeliveryType.UPI
            )
        }
    }

    fun closeConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false, paymentErrorMessage = null) }
    }

    fun updatePaymentDestination(
        upiId: String? = null,
        mobileNumber: String? = null,
        redeemCode: String? = null,
        deliveryType: RedeemDeliveryType? = null
    ) {
        _uiState.update {
            it.copy(
                paymentDestinationUpi = upiId ?: it.paymentDestinationUpi,
                paymentDestinationMobile = mobileNumber ?: it.paymentDestinationMobile,
                paymentRedeemCode = redeemCode ?: it.paymentRedeemCode,
                paymentDeliveryType = deliveryType ?: it.paymentDeliveryType,
                paymentErrorMessage = null
            )
        }
    }

    fun executeRedemption() {
        Log.e("REDEEM_TRACE", "STEP_04_ENTRY executeRedemption() called")
        val coins = _uiState.value.selectedCoins
        if (coins <= 0 || redeemJob?.isActive == true) {
            Log.e("REDEEM_TRACE", "STEP_04_REJECTED coins=$coins redeemJobActive=${redeemJob?.isActive}")
            return
        }
        val pack = selectedPack
        if (pack == null) {
            Log.e("REDEEM_TRACE", "STEP_04_REJECTED selectedPack=null")
            _uiState.update { it.copy(ratesError = "Selected reward pack is unavailable.") }
            return
        }

        val destination = RedeemDestination(
            upiId = _uiState.value.paymentDestinationUpi,
            mobileNumber = _uiState.value.paymentDestinationMobile,
            deliveryType = _uiState.value.paymentDeliveryType,
            redeemCode = _uiState.value.paymentRedeemCode
        )
        Log.e("REDEEM_TRACE", "STEP_05_DESTINATION upi=${destination.upiId} mobile=${destination.mobileNumber} type=${destination.deliveryType}")

        redeemJob?.cancel()
        _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }
        redeemJob = viewModelScope.launch {
                Log.e("REDEEM_TRACE", "STEP_06_COROUTINE launched: pack=${pack.name} coins=$coins")
                try {
                    val currentBalance = userRepo?.getLocalCoins() ?: 0
                    Log.e("REDEEM_TRACE", "STEP_07_BEFORE_API localBalance=$currentBalance calling paymentRepo.submitRedeem()")
                    val paymentResult = paymentRepo.submitRedeem(destination, pack, coins)
                Log.e("REDEEM_TRACE", "STEP_10_RESULT received: success=${paymentResult.success} message=${paymentResult.message} txnId=${paymentResult.payment?.transactionId} backendTxnId=${paymentResult.backendTransactionId} backendRequestId=${paymentResult.backendRequestId}")
                if (!paymentResult.success) {
                    Log.e("REDEEM_TRACE", "STEP_11_FAIL showing error to user: ${paymentResult.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            paymentErrorMessage = paymentResult.message,
                            ratesError = paymentResult.message
                        )
                    }
                    if (paymentResult.message?.contains("insufficient", ignoreCase = true) == true) {
                        Log.e("REDEEM_TRACE", "STEP_11A_INSUFFICIENT refreshing balance from server")
                        try {
                            userRepo.performDeviceHandshake()
                        } catch (_: Exception) {}
                    }
                    return@launch
                }

                val successMessage = paymentResult.message
                val status = if (paymentResult.success) "PENDING" else "REJECTED"
                Log.e("REDEEM_TRACE", "STEP_12_SUCCESS showing success to user: message=$successMessage status=$status backendRequestId=${paymentResult.backendRequestId} backendTxnId=${paymentResult.backendTransactionId}")
                val backendDisplayId = paymentResult.backendRequestId?.take(9)
                    ?: paymentResult.backendTransactionId?.filter { it.isDigit() }?.take(9)
                    ?: "0"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        redemptionSuccessMessage = successMessage,
                        createdRequestId = backendDisplayId.toIntOrNull(),
                        successStatus = status,
                        coinBalance = userRepo.getLocalCoins(),
                        paymentErrorMessage = null
                    )
                }
                loadData()
            } catch (e: TimeoutCancellationException) {
                Log.e("REDEEM_TRACE", "STEP_13_TIMEOUT exception: ${e.message}")
                _uiState.update {
                    it.copy(
                        ratesError = "Timed out while processing redemption request."
                    )
                }
            } catch (e: Exception) {
                Log.e("REDEEM_TRACE", "STEP_13_EXCEPTION type=${e::class.simpleName} message=${e.message}")
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
        _uiState.update { it.copy(ratesError = null, paymentErrorMessage = null) }
    }

    // === POLLING SUPPORT ===
    /**
     * Start polling for redemption status updates
     * Should be called when CoinsScreen (history) is displayed
     */
    fun startRedemptionPolling() {
        Log.d("CoinsViewModel", "Starting redemption status polling")
        paymentRepo.startStatusPolling()
    }

    /**
     * Stop polling for redemption status updates  
     * Should be called when CoinsScreen is closed
     */
    fun stopRedemptionPolling() {
        Log.d("CoinsViewModel", "Stopping redemption status polling")
        paymentRepo.stopStatusPolling()
    }
}
