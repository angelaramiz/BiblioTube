package com.bibliotube.downloader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTOs completos espejo de Seal util/VideoInfo.kt (solo renombrado Bt*).

@Serializable
data class BtVideoClip(val start: Int = 0, val end: Int = 0)

@Serializable
data class BtFormat(
    @SerialName("format_id") val formatId: String? = null,
    @SerialName("format_note") val formatNote: String? = null,
    val ext: String? = null,
    val acodec: String? = null,
    val vcodec: String? = null,
    val url: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val fps: Double? = null,
    @SerialName("audio_ext") val audioExt: String? = null,
    @SerialName("video_ext") val videoExt: String? = null,
    val format: String? = null,
    val resolution: String? = null,
    val vbr: Double? = null,
    val abr: Double? = null,
    val tbr: Double? = null,
    @SerialName("filesize") val fileSize: Double? = null,
    @SerialName("filesize_approx") val fileSizeApprox: Double? = null,
) {
    fun isAudioOnly(): Boolean = vcodec == null || vcodec == "none"
    fun isVideoOnly(): Boolean = acodec == null || acodec == "none"
    fun containsVideo(): Boolean = vcodec != null && vcodec != "none"
    fun containsAudio(): Boolean = acodec != null && acodec != "none"
    fun label(): String = listOfNotNull(formatId, resolution ?: formatNote, ext).joinToString(" · ")
}

@Serializable
data class BtChapter(
    val title: String? = null,
    @SerialName("start_time") val startTime: Double? = null,
    @SerialName("end_time") val endTime: Double? = null,
)

@Serializable
data class BtSubtitleFormat(val ext: String, val url: String, val name: String? = null)

@Serializable
data class BtRequestedDownload(
    @SerialName("requested_formats") val requestedFormats: List<BtFormat>? = emptyList(),
    @SerialName("format_id") val formatId: String? = null,
    val ext: String? = null,
    val acodec: String? = null,
    val vcodec: String? = null,
    val filename: String? = null,
)

@Serializable
data class BtVideoInfo(
    val id: String = "",
    val title: String = "",
    val formats: List<BtFormat>? = emptyList(),
    val thumbnail: String? = null,
    val description: String? = null,
    val uploader: String? = null,
    @SerialName("uploader_id") val uploaderId: String? = null,
    val subtitles: Map<String, List<BtSubtitleFormat>> = emptyMap(),
    @SerialName("automatic_captions") val automaticCaptions: Map<String, List<BtSubtitleFormat>> = emptyMap(),
    val duration: Double? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    val chapters: List<BtChapter>? = null,
    val channel: String? = null,
    @SerialName("original_url") val originalUrl: String? = null,
    val extractor: String? = null,
    @SerialName("extractor_key") val extractorKey: String = "",
    val playlist: String? = null,
    @SerialName("playlist_index") val playlistIndex: Int? = null,
    val vcodec: String? = null,
    val ext: String = "",
    @SerialName("requested_downloads") val requestedDownloads: List<BtRequestedDownload>? = null,
    val filename: String? = null,
    @SerialName("_type") val type: String? = null,
) {
    fun videoOnly(): List<BtFormat> = formats.orEmpty().filter { it.containsVideo() }
    fun audioOnly(): List<BtFormat> = formats.orEmpty().filter { it.isAudioOnly() }
    fun subtitleLangs(): Set<String> = subtitles.keys + automaticCaptions.keys
    fun bestFormatId(): String = formats?.lastOrNull()?.formatId ?: ""
}

@Serializable
data class BtPlaylistEntry(
    @SerialName("_type") val type: String? = null,
    val id: String? = null,
    val url: String? = null,
    val title: String? = null,
    val duration: Double? = null,
)

@Serializable
data class BtPlaylistInfo(
    val title: String? = null,
    @SerialName("_type") val type: String? = null,
    val entries: List<BtPlaylistEntry>? = emptyList(),
    @SerialName("webpage_url") val webpageUrl: String? = null,
    @SerialName("original_url") val originalUrl: String? = null,
    @SerialName("extractor_key") val extractorKey: String? = null,
)
