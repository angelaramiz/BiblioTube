package com.bibliotube.downloader

// Equivalente reducido de Seal DownloadPreferences, solo rama video.
// Standard = defaults seguros. Custom = todo expuesto.

data class BtVideoOptions(
    val formatId: String = "",
    val mergeAudioStream: Boolean = false,
    val videoCodec: Int = VIDEO_COMPAT,
    val resolution: Int = RES_1080,
    val customSorter: String = "",
    val useCustomSorter: Boolean = false,
    val downloadSubtitle: Boolean = false,
    val embedSubtitle: Boolean = false,
    val keepSubtitleFile: Boolean = false,
    val subtitleLangs: String = "en.*,.*-orig",
    val autoSubtitle: Boolean = false,
    val skipAutoTranslated: Boolean = true,
    val convertSubtitle: Int = SUB_NONE,
    val mergeToMkv: Boolean = false,
    val embedThumbnail: Boolean = false,
    val embedChapters: Boolean = true,
    val writeThumbnailFile: Boolean = false,
    val splitByChapter: Boolean = false,
    val clips: List<BtVideoClip> = emptyList(),
    val newTitle: String = "",
    val playlistItem: Int = 0,
    val downloadPlaylist: Boolean = false,
    val subdirPlaylistTitle: Boolean = true,
    val subdirExtractor: Boolean = false,
    val outputTemplate: String = OUTPUT_ID,
    val useAria2c: Boolean = false,
    val concurrentFragments: Int = 1,
    val useCookies: Boolean = false,
    val userAgent: String = "",
    val useProxy: Boolean = false,
    val proxyUrl: String = "",
    val forceIpv4: Boolean = false,
    val rateLimitKb: String = "",
    val useArchive: Boolean = false,
    val sponsorBlock: Boolean = false,
    val sponsorBlockCats: String = "sponsor",
    val restrictFilenames: Boolean = false,
    val supportAv1Hw: Boolean = false,
    val debug: Boolean = false,
    val retries: Int = 3,
    val fragmentRetries: Int = 3,
    val socketTimeout: Int = 10,
) {
    companion object {
        const val VIDEO_COMPAT = 0
        const val VIDEO_QUALITY = 1
        const val RES_2160 = 1
        const val RES_1440 = 2
        const val RES_1080 = 3
        const val RES_720 = 4
        const val RES_480 = 5
        const val RES_360 = 6
        const val SUB_NONE = 0
        const val SUB_ASS = 1
        const val SUB_SRT = 2
        const val SUB_VTT = 3
        const val SUB_LRC = 4

        const val BASENAME = "%(title).200B"
        const val EXT = ".%(ext)s"
        const val OUTPUT_ID = "$BASENAME [%(id)s]$EXT"
        const val OUTPUT_CLIPS = "$BASENAME [%(section_start)d-%(section_end)d]$EXT"
        const val OUTPUT_CHAPTERS = "chapter:$BASENAME/%(section_number)d - %(section_title).200B$EXT"

        /** Modo 1 Standard: compat H264 <=1080p, subs off, capítulos embed, sin exóticos. */
        fun standard(): BtVideoOptions = BtVideoOptions()

        /** Modo 2 Personalizada: parte de standard y sobreescribe. */
        fun custom(block: Builder.() -> Unit): BtVideoOptions =
            Builder().apply(block).build()
    }

    class Builder internal constructor() {
        var o = standard()
        fun formatId(v: String) = apply { o = o.copy(formatId = v) }
        fun resolution(v: Int) = apply { o = o.copy(resolution = v) }
        fun qualityCodecAv1(v: Boolean) = apply {
            o = o.copy(videoCodec = VIDEO_QUALITY, supportAv1Hw = v)
        }
        fun sorter(v: String) = apply { o = o.copy(customSorter = v, useCustomSorter = true) }
        fun subtitles(langs: String = "en.*,.*-orig", embed: Boolean = true) = apply {
            o = o.copy(downloadSubtitle = true, subtitleLangs = langs, embedSubtitle = embed)
        }
        fun clips(v: List<BtVideoClip>) = apply { o = o.copy(clips = v) }
        fun build(): BtVideoOptions = o
        fun full(v: BtVideoOptions) = apply { o = v }
    }
}
