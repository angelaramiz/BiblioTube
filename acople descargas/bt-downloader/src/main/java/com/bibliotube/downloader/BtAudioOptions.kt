package com.bibliotube.downloader

// Rama audio espejo de Seal DownloadUtil.addOptionsForAudioDownloads.

data class BtAudioOptions(
    val formatId: String = "",
    val mergeAudioStream: Boolean = false,
    val convertAudio: Boolean = false,
    val convertFormat: Int = AUDIO_KEEP,
    val useCustomPreset: Boolean = false,
    val audioCodec: Int = CODEC_AAC,
    val audioQuality: Int = Q_MEDIUM,
    val downloadSubtitle: Boolean = false,
    val autoSubtitle: Boolean = false,
    val skipAutoTranslated: Boolean = true,
    val subtitleLangs: String = "en.*,.*-orig",
    val convertSubtitle: Int = 0,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val cropArtworkSquare: Boolean = false,
    val playlistUrl: String = "",
) {
    companion object {
        const val AUDIO_KEEP = 0
        const val AUDIO_MP3 = 1
        const val AUDIO_M4A = 2
        const val CODEC_ANY = 0
        const val CODEC_AAC = 1
        const val CODEC_OPUS = 2
        const val Q_HIGH = 0
        const val Q_MEDIUM = 1
        const val Q_LOW = 2

        fun standard(): BtAudioOptions = BtAudioOptions()

        fun presetMp3High(): BtAudioOptions = BtAudioOptions(
            convertAudio = true, convertFormat = AUDIO_MP3,
            useCustomPreset = true, audioCodec = CODEC_AAC, audioQuality = Q_HIGH,
        )
    }

    fun toSorter(): String {
        if (!useCustomPreset) return ""
        val codec = when (audioCodec) {
            CODEC_AAC -> "acodec:aac"
            CODEC_OPUS -> "acodec:opus"
            else -> ""
        }
        val q = when (audioQuality) {
            Q_HIGH -> "abr~192"; Q_MEDIUM -> "abr~128"; Q_LOW -> "abr~64"; else -> ""
        }
        return listOf(codec, q).filter { it.isNotEmpty() }.joinToString(",")
    }
}
