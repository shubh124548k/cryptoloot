package com.kryptoloot.app.auth

class FirebaseUser(
    val displayName: String?,
    val photoUrl: String?,
    val email: String?,
    val uid: String
)

class FirebaseAuth private constructor() {
    companion object {
        private var instance: FirebaseAuth? = null
        private var currentUser: FirebaseUser? = null

        fun getInstance(): FirebaseAuth {
            if (instance == null) {
                instance = FirebaseAuth()
            }
            return instance!!
        }

        fun setCurrentUser(user: FirebaseUser?) {
            currentUser = user
        }
    }

    val currentUser: FirebaseUser?
        get() = FirebaseAuth.currentUser

    fun signOut() {
        FirebaseAuth.currentUser = null
    }
}
