package com.bibliotube.app.data.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
private const val PREFS = "bibliotube_drive"

sealed interface DriveToken {
    data class Ok(val token: String) : DriveToken
    data class NeedsAction(val intent: Intent) : DriveToken
    data class Error(val message: String) : DriveToken
}

object DriveAccount {
    fun email(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("email", null)

    fun signInIntent(activity: Activity): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        return GoogleSignIn.getClient(activity, gso).signInIntent
    }

    fun onSignedIn(context: Context, account: GoogleSignInAccount?) {
        val email = account?.email ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("email", email).apply()
    }

    fun signOut(context: Context) {
        val app = context.applicationContext
        GoogleSignIn.getClient(app, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun token(context: Context): DriveToken = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val acct = GoogleSignIn.getLastSignedInAccount(app)
            ?: return@withContext DriveToken.Error("Sin cuenta vinculada")
        val gAccount: Account = acct.account ?: return@withContext DriveToken.Error("Sin cuenta vinculada")
        try {
            DriveToken.Ok(GoogleAuthUtil.getToken(app, gAccount, DRIVE_SCOPE))
        } catch (e: UserRecoverableAuthException) {
            DriveToken.NeedsAction(e.intent ?: return@withContext DriveToken.Error("Accion requerida"))
        } catch (e: GoogleAuthException) {
            DriveToken.Error(e.message ?: "Error de auth")
        } catch (e: Exception) {
            DriveToken.Error(e.message ?: "Error de red")
        }
    }

    fun invalidate(context: Context, token: String) {
        try {
            GoogleAuthUtil.clearToken(context.applicationContext, token)
        } catch (e: Exception) {
            // best effort
        }
    }
}
