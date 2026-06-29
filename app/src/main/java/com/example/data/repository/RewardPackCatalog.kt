package com.example.data.repository

data class RewardPack(
    val id: Int,
    val name: String,
    val requiredCoins: Int,
    val rewardAmount: Int,
    val isUnlocked: Boolean,
    val currentProgress: Int,
    val progressPercentage: Int
)

object RewardPackCatalog {
    private data class RewardPackDefinition(
        val id: Int,
        val name: String,
        val requiredCoins: Int,
        val rewardAmount: Int
    )

    private val definitions = listOf(
        RewardPackDefinition(1, "Bronze Pack", 300, 10),
        RewardPackDefinition(2, "Silver Pack", 700, 25),
        RewardPackDefinition(3, "Gold Pack", 1500, 50),
        RewardPackDefinition(4, "Diamond Pack", 3500, 120),
        RewardPackDefinition(5, "Elite Pack", 7000, 250)
    )

    fun fromBalance(currentCoins: Int): List<RewardPack> = definitions.map { definition ->
        val progress = currentCoins.coerceAtMost(definition.requiredCoins)
        val percentage = if (definition.requiredCoins > 0) {
            (progress * 100 / definition.requiredCoins).coerceIn(0, 100)
        } else {
            0
        }

        RewardPack(
            id = definition.id,
            name = definition.name,
            requiredCoins = definition.requiredCoins,
            rewardAmount = definition.rewardAmount,
            isUnlocked = currentCoins >= definition.requiredCoins,
            currentProgress = progress,
            progressPercentage = percentage
        )
    }
}
