package com.bibliotube.app.api

import com.bibliotube.app.BuildConfig
import com.bibliotube.app.data.AppVersionResponse
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(val serverUrl: String) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    @PublishedApi internal val gson = Gson()
    var authToken: String? = null

    data class Result(val info: AppVersionResponse?, val error: String?)

    fun fetchVersion(): Result {
        return try {
            val url = serverUrl.trimEnd('/') + "/api/app-version?app=bibliotube"
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = try {
                        val json = JSONObject(body)
                        // Servidor nuevo: {success:false, error:"..."}; legacy: {error:"..."}
                        json.optString("error", "Error ${res.code}").ifEmpty { "Error ${res.code}" }
                    } catch (e: Exception) { "Error ${res.code}" }
                    return Result(null, msg)
                }
                // Servidor nuevo: {success:true, data:{...}}; legacy: {...} directo
                val info = try {
                    val json = JSONObject(body)
                    val payload = if (json.has("data")) json.getJSONObject("data").toString() else body
                    gson.fromJson(payload, AppVersionResponse::class.java)
                } catch (e: Exception) {
                    return Result(null, "Respuesta invalida")
                }
                Result(info, null)
            }
        } catch (e: Exception) {
            Result(null, e.message ?: "Error de red")
        }
    }

    fun appVersionName(): String = BuildConfig.VERSION_NAME

    // ---------- Boveda (requiere JWT de Supabase Auth) ----------
    data class ApiResult<T>(val data: T?, val error: String?)

    @PublishedApi internal fun vaultGet(path: String): String? {
        val token = authToken ?: return null
        val req = Request.Builder()
            .url(serverUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw RuntimeException(extractError(body, res.code))
            return extractData(body)
        }
    }

    @PublishedApi internal fun vaultWrite(method: String, path: String, payload: Map<String, Any?>?): String? {
        val token = authToken ?: throw RuntimeException("Sin sesion")
        val json = gson.toJson(payload ?: emptyMap<String, Any>())
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder()
            .url(serverUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
        when (method) {
            "POST" -> builder.post(body)
            "PATCH" -> builder.patch(body)
            "DELETE" -> builder.delete()
        }
        http.newCall(builder.build()).execute().use { res ->
            val txt = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw RuntimeException(extractError(txt, res.code))
            return extractData(txt)
        }
    }

    @PublishedApi internal fun extractData(txt: String): String? {
        if (txt.isBlank()) return null
        val json = JSONObject(txt)
        if (json.has("data")) {
            val d = json.get("data")
            return if (d == JSONObject.NULL) null else d.toString()
        }
        return txt
    }

    @PublishedApi internal fun extractError(txt: String, code: Int): String {
        if (txt.isBlank()) return "Error $code"
        return try {
            val json = JSONObject(txt)
            json.optString("error", "Error $code").ifEmpty { "Error $code" }
        } catch (e: Exception) { "Error $code" }
    }

    inline fun <reified T> vaultList(path: String): ApiResult<List<T>> = try {
        val raw = vaultGet(path) ?: return ApiResult(emptyList(), null)
        val arr = if (raw.trimStart().startsWith("[")) raw else JSONObject(raw).optJSONArray("apps")?.toString() ?: raw
        val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, T::class.java).type
        ApiResult(gson.fromJson(arr, type), null)
    } catch (e: Exception) {
        ApiResult(null, e.message ?: "Error de red")
    }

    inline fun <reified T> vaultObject(path: String): ApiResult<T> = try {
        val raw = vaultGet(path) ?: return ApiResult(null, "Vacio")
        ApiResult(gson.fromJson(raw, T::class.java), null)
    } catch (e: Exception) {
        ApiResult(null, e.message ?: "Error de red")
    }

    inline fun <reified T> vaultPost(path: String, payload: Map<String, Any?>): ApiResult<T> = try {
        ApiResult(gson.fromJson(vaultWrite("POST", path, payload), T::class.java), null)
    } catch (e: Exception) {
        ApiResult(null, e.message ?: "Error de red")
    }

    inline fun <reified T> vaultPatch(path: String, payload: Map<String, Any?>): ApiResult<T> = try {
        ApiResult(gson.fromJson(vaultWrite("PATCH", path, payload), T::class.java), null)
    } catch (e: Exception) {
        ApiResult(null, e.message ?: "Error de red")
    }

    fun vaultDelete(path: String): ApiResult<Boolean> = try {
        vaultWrite("DELETE", path, null)
        ApiResult(true, null)
    } catch (e: Exception) {
        ApiResult(null, e.message ?: "Error de red")
    }

    fun fetchPreview(url: String): ApiResult<com.bibliotube.app.data.model.VideoPreview> {
        return try {
            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
            val req = Request.Builder()
                .url(serverUrl.trimEnd('/') + "/api/preview?url=$encoded")
                .build()
            http.newCall(req).execute().use { res ->
                val txt = res.body?.string().orEmpty()
                if (!res.isSuccessful) return ApiResult(null, extractError(txt, res.code))
                val raw = extractData(txt) ?: txt
                ApiResult(gson.fromJson(raw, com.bibliotube.app.data.model.VideoPreview::class.java), null)
            }
        } catch (e: Exception) {
            ApiResult(null, e.message ?: "Error de red")
        }
    }
}
