package com.example.data.repository

import com.example.data.api.AdCompletionRequest
import com.example.data.api.AdCompletionResponse
import com.example.data.api.KryptoLootApi
import com.example.data.local.UserPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdRepository(
    private val api: KryptoLootApi,
    private val prefs: UserPreferences,
    private val userRepo: UserRepository
) {
    suspend fun logAdCompletion(timeSpentSeconds: Int): AdCompletionResponse {
        val deviceId = prefs.deviceId
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        prefs.resetDailyLimitIfNeeded(today)

        if (prefs.adsWatchedToday >= 100) {
            return AdCompletionResponse(
                success = false,
                coins_earned = 0,
                new_balance = prefs.coinBalance,
                ads_today = prefs.adsWatchedToday,
                session_ads = prefs.sessionAds,
                break_until = "DAILY_CAP_REACHED",
                trust_score = prefs.trustScore,
                message = "Daily ad limit of 100 reached. Please wait until tomorrow! ⏰"
            )
        }

        if (timeSpentSeconds < 10) {
            prefs.trustScore = (prefs.trustScore - 15).coerceAtLeast(0)
            userRepo.refreshFromPreferences()
            return AdCompletionResponse(
                success = false,
                coins_earned = 0,
                new_balance = prefs.coinBalance,
                ads_today = prefs.adsWatchedToday,
                session_ads = prefs.sessionAds,
                break_until = null,
                trust_score = prefs.trustScore,
                message = "AD SKIP DETECTED! Your trust score has been reduced. Please watch the ad completely. ⚠️"
            )
        }

        return try {
            val response = api.logCompletion(
                AdCompletionRequest(
                    device_id = deviceId,
                    time_spent_seconds = timeSpentSeconds
                )
            )
            if (response.success) {
                userRepo.applyAdCompletion(response)
            }
            response
        } catch (e: Exception) {
            AdCompletionResponse(
                success = false,
                coins_earned = 0,
                new_balance = prefs.coinBalance,
                ads_today = prefs.adsWatchedToday,
                session_ads = prefs.sessionAds,
                break_until = null,
                trust_score = prefs.trustScore,
                message = "Unable to verify ad completion with the server. No reward was awarded."
            )
        }
    }
}
