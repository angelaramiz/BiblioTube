package com.bibliotube.downloader

import com.yausername.youtubedl_android.YoutubeDLRequest

// Replica fiel de DownloadUtil: addOptionsForVideoDownloads + downloadVideo (rama video).
internal object BtRequestBuilder {

    fun infoRequest(url: String, playlistIndex: Int? = null): YoutubeDLRequest =
        YoutubeDLRequest(url).apply {
            addOption("-o", BtVideoOptions.BASENAME)
            if (playlistIndex != null) {
                addOption("--playlist-items", playlistIndex)
                addOption("--dump-json")
            } else {
                addOption("--dump-single-json")
            }
            addOption("--no-playlist")
            addOption("--socket-timeout", "5")
        }

    fun playlistRequest(url: String): YoutubeDLRequest =
        YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("-o", BtVideoOptions.BASENAME)
            addOption("-R", "1")
        }

    fun videoSorter(o: BtVideoOptions): String {
        val codec = when (o.videoCodec) {
            BtVideoOptions.VIDEO_COMPAT -> "proto,vcodec:h264,ext"
            BtVideoOptions.VIDEO_QUALITY ->
                if (o.supportAv1Hw) "vcodec:av01" else "vcodec:vp9.2"
            else -> ""
        }
        val res = when (o.resolution) {
            1 -> "res:2160"; 2 -> "res:1440"; 3 -> "res:1080"
            4 -> "res:720"; 5 -> "res:480"; 6 -> "res:360"
            7 -> "+res"; else -> ""
        }
        return if (o.videoCodec == BtVideoOptions.VIDEO_COMPAT) {
            listOf(codec, res).filter { it.isNotEmpty() }.joinToString(",")
        } else {
            listOf(res, codec).filter { it.isNotEmpty() }.joinToString(",")
        }
    }

    fun YoutubeDLRequest.videoOptions(o: BtVideoOptions): YoutubeDLRequest = apply {
        addOption("--add-metadata")
        addOption("--no-embed-info-json")
        if (o.formatId.isNotEmpty()) {
            addOption("-f", o.formatId)
            if (o.mergeAudioStream) addOption("--audio-multistreams")
        } else if (o.useCustomSorter && o.customSorter.isNotEmpty()) {
            addOption("-S", o.customSorter)
        } else {
            videoSorter(o).takeIf { it.isNotEmpty() }?.let { addOption("-S", it) }
        }
        if (o.downloadSubtitle) {
            if (o.autoSubtitle) {
                addOption("--write-auto-subs")
                if (o.skipAutoTranslated) addOption("--extractor-args", "youtube:skip=translated_subs")
            }
            if (o.subtitleLangs.isNotEmpty()) addOption("--sub-langs", o.subtitleLangs)
            if (o.embedSubtitle) {
                addOption("--embed-subs")
                if (o.keepSubtitleFile) addOption("--write-subs")
            } else addOption("--write-subs")
            when (o.convertSubtitle) {
                1 -> addOption("--convert-subs", "ass")
                2 -> addOption("--convert-subs", "srt")
                3 -> addOption("--convert-subs", "vtt")
                4 -> addOption("--convert-subs", "lrc")
            }
        }
        if (o.mergeToMkv) {
            addOption("--remux-video", "mkv")
            addOption("--merge-output-format", "mkv")
        }
        if (o.embedThumbnail) addOption("--embed-thumbnail")
        if (o.clips.isEmpty() && o.embedChapters) addOption("--embed-chapters")
    }

    fun YoutubeDLRequest.audioOptions(o: BtAudioOptions): YoutubeDLRequest = apply {
        addOption("-x")
        if (o.downloadSubtitle) {
            addOption("--write-subs")
            if (o.autoSubtitle) {
                addOption("--write-auto-subs")
                if (o.skipAutoTranslated) addOption("--extractor-args", "youtube:skip=translated_subs")
            }
            if (o.subtitleLangs.isNotEmpty()) addOption("--sub-langs", o.subtitleLangs)
            when (o.convertSubtitle) {
                1 -> addOption("--convert-subs", "ass")
                2 -> addOption("--convert-subs", "srt")
                3 -> addOption("--convert-subs", "vtt")
                4 -> addOption("--convert-subs", "lrc")
            }
        }
        if (o.formatId.isNotEmpty()) {
            addOption("-f", o.formatId)
            if (o.mergeAudioStream) addOption("--audio-multistreams")
        } else if (o.convertAudio) {
            when (o.convertFormat) {
                BtAudioOptions.AUDIO_MP3 -> addOption("--audio-format", "mp3")
                BtAudioOptions.AUDIO_M4A -> addOption("--audio-format", "m4a")
            }
        } else {
            o.toSorter().takeIf { it.isNotEmpty() }?.let { addOption("-S", it) }
        }
        if (o.embedMetadata) {
            addOption("--embed-metadata")
            addOption("--embed-thumbnail")
            addOption("--convert-thumbnails", "jpg")
            if (o.cropArtworkSquare) {
                addOption("--ppa", "ffmpeg: -c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
            }
        }
        if (o.playlistUrl.isNotEmpty()) {
            addOption("--parse-metadata", "%(album,playlist,title)s:%(meta_album)s")
            addOption("--parse-metadata", "%(track_number,playlist_index)d:%(meta_track)s")
        } else {
            addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")
        }
        addOption("--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s")
    }

    fun YoutubeDLRequest.globalOptions(
        o: BtVideoOptions,
        videoDir: String,
        tempDir: String?,
        cookiesFile: String?,
        archiveFile: String?,
    ): YoutubeDLRequest = apply {
        addOption("--no-mtime")
        addOption("--retries", o.retries.coerceIn(0, 10))
        addOption("--fragment-retries", o.fragmentRetries.coerceIn(0, 10))
        addOption("--socket-timeout", o.socketTimeout.coerceIn(3, 60))
        if (o.restrictFilenames) addOption("--restrict-filenames")
        if (o.forceIpv4) addOption("-4")
        if (o.debug) addOption("-v")
        if (o.rateLimitKb.isNotEmpty()) addOption("-r", "${o.rateLimitKb}K")
        if (o.useCookies && cookiesFile != null) {
            addOption("--cookies", cookiesFile)
            if (o.userAgent.isNotEmpty()) addOption("--add-header", "User-Agent:${o.userAgent}")
        }
        if (o.useProxy && o.proxyUrl.isNotEmpty()) addOption("--proxy", o.proxyUrl)
        if (o.useArchive && archiveFile != null) addOption("--download-archive", archiveFile)
        if (o.playlistItem != 0 && o.downloadPlaylist) {
            addOption("--playlist-items", o.playlistItem)
        } else addOption("--no-playlist")
        if (o.useAria2c) addOption("--downloader", "libaria2c.so")
        else if (o.concurrentFragments > 1) addOption("--concurrent-fragments", o.concurrentFragments)
        if (o.sponsorBlock) addOption("--sponsorblock-remove", o.sponsorBlockCats)
        if (o.writeThumbnailFile) {
            addOption("--write-thumbnail")
            addOption("--convert-thumbnails", "png")
        }
        addOption("-P", videoDir)
        if (tempDir != null) addOption("-P", "temp:$tempDir")
        o.clips.forEach { addOption("--download-sections", "*${it.start}-${it.end}") }
        if (o.newTitle.isNotEmpty()) addOption("--replace-in-metadata", "title", ".+", o.newTitle)
        val out = when {
            o.splitByChapter -> BtVideoOptions.OUTPUT_CHAPTERS
            o.clips.isNotEmpty() -> BtVideoOptions.OUTPUT_CLIPS
            else -> o.outputTemplate
        }
        if (o.splitByChapter) addOption("--split-chapters")
        addOption("-o", out)
    }
}
