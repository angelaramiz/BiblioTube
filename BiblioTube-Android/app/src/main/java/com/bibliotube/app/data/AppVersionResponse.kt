package com.bibliotube.app.data

data class AppVersionResponse(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    /** Tamano exacto del APK publicado (0 si el servidor no lo informa). */
    val apkSize: Long = 0L,
    /** SHA-256 hexadecimal del APK publicado (null si el servidor no lo informa). */
    val apkSha256: String? = null
)
