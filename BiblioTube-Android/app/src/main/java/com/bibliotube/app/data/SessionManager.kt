package com.bibliotube.app.data

import android.content.Context
import com.bibliotube.app.data.remote.AuthClient
import com.bibliotube.app.data.remote.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SessionManager private constructor(context: Context) {
    private val auth = AuthClient(context.applicationContext)

    private val _session = MutableStateFlow(auth.session())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    suspend fun signIn(email: String, password: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            auth.signIn(email, password).also { it.onSuccess { _session.value = it } }
        }

    suspend fun signUp(email: String, password: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            auth.signUp(email, password).also { it.onSuccess { _session.value = it } }
        }

    suspend fun refreshIfNeeded() {
        val current = _session.value ?: return
        if (current.accessToken.isEmpty()) return
        withContext(Dispatchers.IO) {
            auth.refresh().onSuccess { _session.value = it }
        }
    }

    fun signOut() {
        auth.signOut()
        _session.value = null
    }

    companion object {
        @Volatile private var instance: SessionManager? = null

        fun get(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
    }
}
