package com.example.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.LeaderboardItem
import com.example.data.api.KryptoLootApi
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

data class LeaderboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val weeklyList: List<LeaderboardItem> = emptyList(),
    val allTimeList: List<LeaderboardItem> = emptyList(),
    val selectedTab: Int = 0,
    val userRank: Int = 42,
    val userBalance: Int = 0
)

class LeaderboardViewModel(
    private val api: KryptoLootApi,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        userRepo.appState.onEach { snapshot ->
            _uiState.update { it.copy(userBalance = snapshot.coinBalance) }
        }.launchIn(viewModelScope)
        loadLeaderboard()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun loadLeaderboard() {
        val bal = userRepo.getLocalCoins()
        _uiState.update { it.copy(isLoading = true, userBalance = bal) }
        viewModelScope.launch {
            try {
                val weekly = withTimeoutOrNull(8000L) { api.getLeaderboard("weekly", 20) }
                val allTime = withTimeoutOrNull(8000L) { api.getLeaderboard("alltime", 20) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        weeklyList = weekly ?: emptyList(),
                        allTimeList = allTime ?: emptyList(),
                        userRank = (weekly ?: emptyList()).indexOfFirst { item -> item.device_id == userRepo.getDeviceId() }.takeIf { it >= 0 } ?: 42,
                        userBalance = userRepo.getLocalCoins()
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Leaderboard unavailable right now."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Leaderboard unavailable right now."
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
