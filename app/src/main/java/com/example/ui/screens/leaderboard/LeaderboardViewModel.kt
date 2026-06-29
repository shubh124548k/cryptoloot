package com.example.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.LeaderboardItem
import com.example.data.repository.LeaderboardEntry
import com.example.data.repository.LeaderboardRepository
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class LeaderboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val weeklyList: List<LeaderboardItem> = emptyList(),
    val allTimeList: List<LeaderboardItem> = emptyList(),
    val selectedTab: Int = 0,
    val userRank: Int = 42,
    val userBalance: Int = 0,
    val leaderboardScore: Int = 0,
    val currentLeague: String = "UNRANKED",
    val weeklyRank: Int = 0,
    val hallOfFameRank: Int = 0,
    val totalPlayers: Int = 0,
    val rankDifference: Int = 0
)

class LeaderboardViewModel(
    private val leaderboardRepo: LeaderboardRepository,
    private val userRepo: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        leaderboardRepo.leaderboardState.onEach { state ->
            val standing = state.currentUserStanding
            _uiState.update {
                it.copy(
                    userBalance = userRepo.getLocalCoins(),
                    leaderboardScore = standing.leaderboardScore,
                    currentLeague = standing.leaguePosition,
                    userRank = standing.currentRank,
                    weeklyRank = standing.weeklyRank,
                    hallOfFameRank = standing.hallOfFameRank,
                    totalPlayers = standing.totalPlayers,
                    rankDifference = standing.rankDifference
                )
            }
        }.launchIn(viewModelScope)

        leaderboardRepo.weeklyLeaderboard.onEach { weekly ->
            _uiState.update {
                it.copy(
                    weeklyList = weekly.entries.mapIndexed { index, entry -> entry.toLeaderboardItem(index + 1) },
                    totalPlayers = weekly.totalPlayers,
                    weeklyRank = weekly.currentUserStanding.weeklyRank,
                    rankDifference = weekly.currentUserStanding.rankDifference
                )
            }
        }.launchIn(viewModelScope)

        leaderboardRepo.hallOfFameLeaderboard.onEach { hallOfFame ->
            _uiState.update {
                it.copy(
                    allTimeList = hallOfFame.entries.mapIndexed { index, entry -> entry.toLeaderboardItem(index + 1) },
                    hallOfFameRank = hallOfFame.currentUserStanding.hallOfFameRank,
                    totalPlayers = hallOfFame.totalPlayers
                )
            }
        }.launchIn(viewModelScope)

        userRepo.appState.onEach { snapshot ->
            leaderboardRepo.refreshLeaderboard(snapshot)
            _uiState.update { it.copy(userBalance = snapshot.coinBalance) }
        }.launchIn(viewModelScope)
        loadLeaderboard()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun loadLeaderboard() {
        val snapshot = userRepo.getCurrentSnapshot()
        leaderboardRepo.refreshLeaderboard(snapshot)
        _uiState.update {
            it.copy(
                isLoading = false,
                userBalance = snapshot.coinBalance,
                leaderboardScore = snapshot.leaderboardState.stats.leaderboardScore,
                currentLeague = snapshot.leaderboardState.stats.currentLeague,
                userRank = snapshot.leaderboardState.currentUserStanding.currentRank,
                weeklyRank = snapshot.leaderboardState.currentUserStanding.weeklyRank,
                hallOfFameRank = snapshot.leaderboardState.currentUserStanding.hallOfFameRank,
                totalPlayers = snapshot.leaderboardState.stats.totalPlayers,
                rankDifference = snapshot.leaderboardState.stats.rankDifference
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun LeaderboardEntry.toLeaderboardItem(rank: Int): LeaderboardItem {
        return LeaderboardItem(
            rank = rank,
            device_id = displayName.ifBlank { userUid },
            coins = score,
            ads_watched = totalTransactions
        )
    }
}
