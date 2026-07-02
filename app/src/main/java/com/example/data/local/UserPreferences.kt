package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class UserPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("kryptoloot_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_COIN_BALANCE = "coin_balance"
        private const val KEY_ADS_WATCHED_TODAY = "ads_watched_today"
        private const val KEY_SESSION_ADS = "session_ads"
        private const val KEY_TRUST_SCORE = "trust_score"
        private const val KEY_LAST_AD_TIMESTAMP = "last_ad_timestamp"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_MASTER_UID = "master_uid"
        private const val KEY_TOTAL_ADS_WATCHED = "total_ads_watched"
        private const val KEY_NOTIFICATION_COUNT = "notification_count"
        private const val KEY_TOTAL_COINS_EARNED = "total_coins_earned"
        private const val KEY_TRANSACTION_HISTORY_JSON = "transaction_history_json"
        private const val KEY_LEADERBOARD_JSON = "leaderboard_json"
        private const val KEY_NOTIFICATIONS_JSON = "notifications_json"
        private const val KEY_SYNC_STATE_JSON = "sync_state_json"
        private const val KEY_REDEEM_PAYMENTS_JSON = "redeem_payments_json"
            private const val KEY_OFFLINE_REDEEM_JSON = "offline_redeem_json"
        private const val KEY_DEBUG_TEST_BALANCE_APPLIED = "debug_test_balance_applied"
    }

    var masterUid: String?
        get() = prefs.getString(KEY_MASTER_UID, null)
        set(value) = prefs.edit().putString(KEY_MASTER_UID, value).apply()

    var displayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, "Krypton_Warrior")
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    var photoUrl: String?
        get() = prefs.getString(KEY_PHOTO_URL, null)
        set(value) = prefs.edit().putString(KEY_PHOTO_URL, value).apply()

    var coinBalance: Int
        get() = prefs.getInt(KEY_COIN_BALANCE, 0)
        set(value) = prefs.edit().putInt(KEY_COIN_BALANCE, value).apply()

    var debugTestBalanceApplied: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_TEST_BALANCE_APPLIED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_TEST_BALANCE_APPLIED, value).apply()

    var adsWatchedToday: Int
        get() = prefs.getInt(KEY_ADS_WATCHED_TODAY, 0)
        set(value) = prefs.edit().putInt(KEY_ADS_WATCHED_TODAY, value).apply()

    var sessionAds: Int
        get() = prefs.getInt(KEY_SESSION_ADS, 0)
        set(value) = prefs.edit().putInt(KEY_SESSION_ADS, value).apply()

    var trustScore: Int
        get() = prefs.getInt(KEY_TRUST_SCORE, 100)
        set(value) = prefs.edit().putInt(KEY_TRUST_SCORE, value).apply()

    var totalAdsWatched: Int
        get() = prefs.getInt(KEY_TOTAL_ADS_WATCHED, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_ADS_WATCHED, value).apply()


    var notificationCount: Int
        get() = prefs.getInt(KEY_NOTIFICATION_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_NOTIFICATION_COUNT, value).apply()

    var totalCoinsEarned: Int
        get() = prefs.getInt(KEY_TOTAL_COINS_EARNED, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_COINS_EARNED, value).apply()

    var transactionHistoryJson: String?
        get() = prefs.getString(KEY_TRANSACTION_HISTORY_JSON, null)
        set(value) = prefs.edit().putString(KEY_TRANSACTION_HISTORY_JSON, value).apply()

    var leaderboardJson: String?
        get() = prefs.getString(KEY_LEADERBOARD_JSON, null)
        set(value) = prefs.edit().putString(KEY_LEADERBOARD_JSON, value).apply()

    var notificationsJson: String?
        get() = prefs.getString(KEY_NOTIFICATIONS_JSON, null)
        set(value) = prefs.edit().putString(KEY_NOTIFICATIONS_JSON, value).apply()

    var syncStateJson: String?
        get() = prefs.getString(KEY_SYNC_STATE_JSON, null)
        set(value) = prefs.edit().putString(KEY_SYNC_STATE_JSON, value).apply()

    var redeemPaymentsJson: String?
        get() = prefs.getString(KEY_REDEEM_PAYMENTS_JSON, null)
        set(value) = prefs.edit().putString(KEY_REDEEM_PAYMENTS_JSON, value).apply()

    var offlineRedeemJson: String?
        get() = prefs.getString(KEY_OFFLINE_REDEEM_JSON, null)
        set(value) = prefs.edit().putString(KEY_OFFLINE_REDEEM_JSON, value).apply()

    var lastAdTimestamp: Long
        get() = prefs.getLong(KEY_LAST_AD_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AD_TIMESTAMP, value).apply()

    val deviceId: String
        get() {
            val hardwareId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            if (!hardwareId.isNullOrEmpty()) {
                return hardwareId
            }
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = "dev_" + UUID.randomUUID().toString().take(12).replace("-", "")
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    var lastResetDate: String?
        get() = prefs.getString(KEY_LAST_RESET_DATE, null)
        set(value) = prefs.edit().putString(KEY_LAST_RESET_DATE, value).apply()

    fun resetDailyLimitIfNeeded(currentDateString: String) {
        val lastDate = lastResetDate
        if (lastDate != currentDateString) {
            adsWatchedToday = 0
            sessionAds = 0
            lastResetDate = currentDateString
        }
    }
}
