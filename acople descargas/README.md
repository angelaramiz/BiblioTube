# Acople Descargas — Base Seal para BiblioTube

Recreación funcional del núcleo de descarga de Seal
(`JunkFood02/Seal`, GUI de `yt-dlp` vía `youtubedl-android 0.17.3`)
para integrar en app Kotlin Android existente, no clon literal de UI.

## 2 modos

- **Standard:** `BtDownloader.downloadStandard(url, dir)` — info + sorter auto + `-o` seguro. 1 llamada.
- **Personalizada:** `BtVideoOptions` completo + `BtDownloader.downloadCustom(...)` — `-f/-S`, subs, mkv, clips, capítulos, playlist, aria2c, proxy, cookies, archive, sponsorblock.

Multiplataforma = 100% extractores `yt-dlp`. Sin código por-sitio.
Ver `proyectos/seal/input` (origen): `util/DownloadUtil.kt`, `App.kt:91-93`, `gradle/libs.versions.toml:107-109`.

## Dependencias (app/host)

```kotlin
// libs.versions.toml
youtubedlAndroid = "0.17.3"
youtubedl-android-library = { group = "io.github.junkfood02.youtubedl-android", name = "library", version.ref = "youtubedlAndroid" }
youtubedl-android-ffmpeg = { group = "io.github.junkfood02.youtubedl-android", name = "ffmpeg", version.ref = "youtubedlAndroid" }
youtubedl-android-aria2c = { group = "io.github.junkfood02.youtubedl-android", name = "aria2c", version.ref = "youtubedlAndroid" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.bundles.youtubedlAndroid) // o las 3 por separado
```

`minSdk >= 26`, `arm64-v8a` recomendado. Permisos: INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS, WRITE_EXTERNAL_STORAGE (legacy) / MediaStore.

## Init (Application.onCreate, IO thread)

```kotlin
YoutubeDL.init(this)
FFmpeg.init(this)
Aria2c.init(this)
```

## Uso

```kotlin
val dl = BtDownloader(context, videoDir = File(...), tempDir = File(...))
val info = dl.fetchInfo("https://...") // --dump-single-json
dl.downloadStandard("https://...", onProgress = { p, eta, txt -> }) // Result<List<String>>
dl.downloadCustom(url = "...", playlistIndex = 0, options = BtVideoOptions.custom(...), onProgress = {...})
dl.cancel(taskId)
dl.updateYtDlp("nightly")
```

Archivos: `bt-downloader/src/main/java/com/bibliotube/downloader/`
- `BtModels.kt` (full: BtVideoInfo/formats/subs/chapters/playlist), `BtPreferences.kt`, `BtAudioOptions.kt`, `BtRequestBuilder.kt`, `BtDownloader.kt`, `BtIntegrationExample.kt`

Audio: `downloadAudio()` con `-x --audio-format --embed-metadata --parse-metadata` espejo `DownloadUtil.kt:559-626`.

Cola/historial/notif/extras:
- `BtQueue.kt` espejo `DownloaderV2`: `Idle->FetchingInfo->Ready->Running->Done/Error`, `Semaphore(2)`, cancel.
- `BtHistory.kt` espejo `DownloadedVideoInfo/Dao` (interfaz + memoria, listo para Room).
- `BtNotifications.kt` espejo `NotificationUtil` (progreso/done/error).
- `BtExtras.kt`: `BtCommandTemplate` + `BtFiles` (cookies Netscape, archive, config) espejo `CommandTemplate`/`getCookiesFile/getArchiveFile`.

Cierre almacenamiento/servicio/update:
- `BtStorage.kt` espejo `FileUtil.kt:107-205` (public/private/tmp, `scanPostDownload`, `moveToSdcard` SAF).
- `BtService.kt` espejo `DownloadService.kt` (foreground vacío + `BtNotifications.CHANNEL`).
- `BtUpdate.kt` espejo `UpdateUtil.kt:56-75` (stable/nightly + guarda versión).
