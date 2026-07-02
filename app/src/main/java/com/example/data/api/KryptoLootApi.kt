package com.example.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface KryptoLootApi {

    @POST("api/v1/auth/handshake")
    suspend fun handshake(
        @Body request: HandshakeRequest
    ): HandshakeResponse

    @POST("api/v1/auth/device-handshake")
    suspend fun deviceHandshake(
        @Body request: DeviceHandshakeRequest
    ): DeviceHandshakeResponse

    @POST("api/v1/auth/recover-uid")
    suspend fun recoverUid(
        @Body request: UidRecoveryRequest
    ): UidRecoveryResponse

    @POST("api/v1/ads/log-completion")
    suspend fun logCompletion(
        @Body request: AdCompletionRequest
    ): AdCompletionResponse

    @GET("api/v1/economy/rates")
    suspend fun getRates(): EconomyTiersResponse

    @POST("api/v1/rewards/redeem")
    suspend fun redeem(
        @Body request: RedeemRequest
    ): RedeemResponse

    @GET("api/v1/leaderboard")
    suspend fun getLeaderboard(
        @Query("period") period: String,
        @Query("limit") limit: Int
    ): List<LeaderboardItem>

    @GET("api/v1/users/{deviceId}/redemptions")
    suspend fun getRedemptions(
        @Path("deviceId") deviceId: String
    ): List<RedemptionHistoryItem>

    @POST("api/v1/debug/adjust")
    suspend fun debugAdjust(
        @Body request: DebugAdjustRequest
    ): DebugAdjustResponse
}
