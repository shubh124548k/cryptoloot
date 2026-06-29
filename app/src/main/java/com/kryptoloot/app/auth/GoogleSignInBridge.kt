package com.kryptoloot.app.auth

import android.content.Context
import android.content.Intent

class GoogleSignInOptions {
    class Builder {
        fun requestEmail(): Builder = this
        fun requestProfile(): Builder = this
        fun build(): GoogleSignInOptions = GoogleSignInOptions()
    }
}

class GoogleSignInAccount(
    val displayName: String?,
    val photoUrl: String?,
    val email: String?,
    val id: String
)

class GoogleSignInClient(private val context: Context) {
    fun getSignInIntent(): Intent = Intent()
}

object GoogleSignIn {
    fun getClient(context: Context, options: GoogleSignInOptions): GoogleSignInClient = GoogleSignInClient(context)
}
