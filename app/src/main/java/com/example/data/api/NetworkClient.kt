package com.example.data.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object NetworkClient {
    private var ACTIVE_BASE_URL = "http://10.0.2"
    // Default local API server

    fun updateServerTargetDomain(newDomainUrl: String) {
        ACTIVE_BASE_URL = newDomainUrl
        _api = null
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private var _api: KryptoLootApi? = null

    val api: KryptoLootApi
        get() {
            if (_api == null) {
                _api = Retrofit.Builder()
                    .baseUrl(ACTIVE_BASE_URL)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                    .create(KryptoLootApi::class.java)
            }
            return _api!!
        }
}
