package com.kryptoloot.app.auth

import android.app.Activity

class LoginResult(val accessToken: AccessToken)

class AccessToken(val token: String, val userId: String)

interface FacebookCallback<T> {
    fun onSuccess(result: T)
    fun onCancel()
    fun onError(exception: Exception)
}

class LoginManager {
    companion object {
        private var instance: LoginManager? = null
        fun getInstance(): LoginManager {
            if (instance == null) {
                instance = LoginManager()
            }
            return instance!!
        }
    }

    fun logInWithReadPermissions(activity: Activity, permissions: Collection<String>) {
        // No-op to preserve the current UX flow without crashing on unsupported SDKs.
    }

    fun registerCallback(callback: FacebookCallback<LoginResult>) {
        // No-op to preserve the current UX flow without crashing on unsupported SDKs.
    }
}
