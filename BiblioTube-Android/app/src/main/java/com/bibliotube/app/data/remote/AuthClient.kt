package com.bibliotube.app.data.remote

import android.content.Context
import com.bibliotube.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val AUTH_PREFS = "bibliotube_auth"
private val JSON = "application/json; charset=utf-8".toMediaType()

data class AuthSession(val accessToken: String, val refreshToken: String, val userId: String, val email: String)

private data class GoTrueResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    val user: GoTrueUser?
)
private data class GoTrueUser(val id: String?, val email: String?)

class AuthClient(context: Context, private val http: OkHttpClient = defaultHttp()) {
    private val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    fun session(): AuthSession? {
        val at = prefs.getString("access_token", null) ?: return null
        return AuthSession(
            at,
            prefs.getString("refresh_token", null).orEmpty(),
            prefs.getString("user_id", null).orEmpty(),
            prefs.getString("email", null).orEmpty()
        )
    }

    fun signIn(email: String, password: String): Result<AuthSession> =
        passwordGrant("signin", email, password)

    fun signUp(email: String, password: String): Result<AuthSession> {
        val body = gson.toJson(mapOf("email" to email, "password" to password)).toRequestBody(JSON)
        val req = Request.Builder()
            .url("$base/auth/v1/signup")
            .header("apikey", anonKey)
            .post(body)
            .build()
        return try {
            http.newCall(req).execute().use { res ->
                val txt = res.body?.string().orEmpty()
                if (!res.isSuccessful) return Result.failure(Exception(errorOf(txt, res.code)))
                parseSession(txt)?.let { save(it); Result.success(it) }
                    ?: Result.failure(Exception("Respuesta de registro invalida"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error de red"))
        }
    }

    fun refresh(): Result<AuthSession> {
        val rt = session()?.refreshToken?.takeIf { it.isNotEmpty() }
            ?: return Result.failure(Exception("Sin sesion"))
        val body = gson.toJson(mapOf("refresh_token" to rt)).toRequestBody(JSON)
        val req = Request.Builder()
            .url("$base/auth/v1/token?grant_type=refresh_token")
            .header("apikey", anonKey)
            .post(body)
            .build()
        return try {
            http.newCall(req).execute().use { res ->
                val txt = res.body?.string().orEmpty()
                if (!res.isSuccessful) return Result.failure(Exception(errorOf(txt, res.code)))
                parseSession(txt)?.let { save(it); Result.success(it) }
                    ?: Result.failure(Exception("No se pudo renovar la sesion"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error de red"))
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }

    private fun passwordGrant(kind: String, email: String, password: String): Result<AuthSession> {
        val body = gson.toJson(mapOf("email" to email, "password" to password)).toRequestBody(JSON)
        val req = Request.Builder()
            .url("$base/auth/v1/token?grant_type=password")
            .header("apikey", anonKey)
            .post(body)
            .build()
        return try {
            http.newCall(req).execute().use { res ->
                val txt = res.body?.string().orEmpty()
                if (!res.isSuccessful) return Result.failure(Exception("$kind: ${errorOf(txt, res.code)}"))
                parseSession(txt)?.let { save(it); Result.success(it) }
                    ?: Result.failure(Exception("Respuesta de login invalida"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error de red"))
        }
    }

    private fun parseSession(txt: String): AuthSession? {
        val r = gson.fromJson(txt, GoTrueResponse::class.java) ?: return null
        if (r.accessToken.isNullOrEmpty()) return null
        return AuthSession(r.accessToken, r.refreshToken.orEmpty(), r.user?.id.orEmpty(), r.user?.email.orEmpty())
    }

    private fun errorOf(txt: String, code: Int): String {
        return try {
            val json = com.google.gson.JsonParser.parseString(txt).asJsonObject
            json.get("msg")?.asString ?: json.get("error_description")?.asString
            ?: json.get("error")?.asString ?: "Error $code"
        } catch (e: Exception) { "Error $code" }
    }

    private fun save(s: AuthSession) {
        prefs.edit()
            .putString("access_token", s.accessToken)
            .putString("refresh_token", s.refreshToken)
            .putString("user_id", s.userId)
            .putString("email", s.email)
            .apply()
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
