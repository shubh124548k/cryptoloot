package com.example.ui.screens.watchad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AdRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class WatchAdUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val timeLeft: Int = 30,
    val adUrl: String = "https://www.youtube.com/embed/dQw4w9WgXcQ",
    val adWatchedToday: Int = 0,
    val sessionAds: Int = 0,
    val trustScore: Int = 100,
    val showSuccessPopup: Boolean = false,
    val earnedCoins: Int = 0,
    val oldBalance: Int = 0,
    val newBalance: Int = 0,
    val nextBreakTime: String? = null
)

class WatchAdViewModel(
    private val adRepo: AdRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchAdUiState())
    val uiState: StateFlow<WatchAdUiState> = _uiState.asStateFlow()
    private var rewardRequestInFlight = false

    init {
        userRepo.appState.onEach { snapshot ->
            _uiState.update {
                it.copy(
                    adWatchedToday = snapshot.dailyAdsWatched,
                    sessionAds = snapshot.sessionAds,
                    trustScore = snapshot.trustScore,
                    newBalance = snapshot.coinBalance
                )
            }
        }.launchIn(viewModelScope)
        loadData()
    }

    fun loadData() {
        val snapshot = userRepo.getCurrentSnapshot()
        _uiState.update {
            it.copy(
                adWatchedToday = snapshot.dailyAdsWatched,
                sessionAds = snapshot.sessionAds,
                trustScore = snapshot.trustScore
            )
        }
    }

    fun onRealAdCompleted() {
        if (rewardRequestInFlight) return
        rewardRequestInFlight = true
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val response = withTimeoutOrNull(8000L) { adRepo.logAdCompletion(30) }
                if (response == null) {
                    _uiState.update {
                        it.copy(
                            error = "Timed out while processing ad completion."
                        )
                    }
                } else if (response.success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showSuccessPopup = true,
                            earnedCoins = response.coins_earned,
                            newBalance = response.new_balance,
                            adWatchedToday = response.ads_today,
                            sessionAds = response.session_ads,
                            nextBreakTime = response.break_until,
                            trustScore = response.trust_score
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = response.message,
                            trustScore = response.trust_score
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        error = "Timed out while processing ad completion."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Unable to process ad completion request."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
                rewardRequestInFlight = false
            }
        }
    }

    fun dismissPopup() {
        _uiState.update { it.copy(showSuccessPopup = false) }
        loadData()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
