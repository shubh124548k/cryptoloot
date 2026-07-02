package com.example.data.api

import android.content.Context
import android.util.Log
import com.kryptoloot.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.local.DeviceUtils

object NetworkClient {
    private var ACTIVE_BASE_URL = BuildConfig.BASE_URL
    // Default local API server from BuildConfig; runtime override is available via updateServerTargetDomain.
    // For DEBUG builds, initialize with sensible default depending on emulator vs physical device.

    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG) return
        // If ACTIVE_BASE_URL was overridden already, respect it
        if (ACTIVE_BASE_URL != BuildConfig.BASE_URL) return

        val isEmulator = DeviceUtils.isRunningOnEmulator()
        if (isEmulator) {
            // Emulator runtime should use emulator loopback host.
            val emulatorBaseUrl = "http://10.0.2.2:5000/"
            ACTIVE_BASE_URL = emulatorBaseUrl
            Log.d("NetworkClient", "DEBUG emulator detected; using BASE_URL=$ACTIVE_BASE_URL")
        } else {
            // Physical device uses the debug build's configured LAN backend.
            ACTIVE_BASE_URL = BuildConfig.BASE_URL
            Log.d("NetworkClient", "DEBUG physical device detected; using LAN BASE_URL=$ACTIVE_BASE_URL")
        }
        _api = null
    }

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
