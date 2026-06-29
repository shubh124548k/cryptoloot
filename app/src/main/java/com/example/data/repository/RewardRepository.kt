package com.example.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RewardRepository(
    private val userRepo: UserRepository
) {
    private val _rewardProgress = MutableStateFlow<List<RewardProgress>>(emptyList())
    val rewardProgress: StateFlow<List<RewardProgress>> = _rewardProgress.asStateFlow()

    init {
        refreshRewards(userRepo.getCurrentSnapshot().coinBalance)
    }

    fun refreshRewards(currentCoins: Int) {
        val progress = RewardPackCatalog.fromBalance(currentCoins).map { pack ->
            RewardProgress(
                id = pack.id,
                name = pack.name,
                requiredCoins = pack.requiredCoins,
                rewardAmount = pack.rewardAmount,
                isUnlocked = pack.isUnlocked,
                currentProgress = pack.currentProgress,
                progressPercentage = pack.progressPercentage
            )
        }
        _rewardProgress.value = progress
    }
}
