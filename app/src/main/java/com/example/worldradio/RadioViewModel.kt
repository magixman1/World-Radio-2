package com.example.worldradio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("dorota_radio_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    var searchResults by mutableStateOf<List<RadioStation>>(emptyList())
        private set

    val favoriteStations = mutableStateListOf<RadioStation>()

    var currentStation by mutableStateOf<RadioStation?>(null)
        private set

    var currentSongTitle by mutableStateOf<String>("Wybierz stację, aby rozpocząć")
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    private var fallbackUrls = mutableListOf<String>()
    private var fallbackIndex = 0

    init {
        loadFavorites()
        ensureDefaultPresetStations()

        val sessionToken = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java)
        )
        val future = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            val controller = future.get()
            mediaController = controller
            isPlaying = controller.isPlaying

            controller.currentMediaItem?.let { item ->
                if (currentStation == null && item.mediaId.isNotEmpty()) {
                    currentStation = RadioStation(
                        id = item.mediaId,
                        name = item.mediaMetadata.title?.toString() ?: "Dorota Radio",
                        streamUrl = "",
                        country = item.mediaMetadata.subtitle?.toString() ?: "",
                        tags = "",
                        favicon = item.mediaMetadata.artworkUri?.toString() ?: ""
                    )
                }
            }

            controller.mediaMetadata.let { meta ->
                val title = meta.title?.toString()
                val artist = meta.artist?.toString()
                updateSongTitleText(title, artist)
            }

            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    if (playing && (currentSongTitle.startsWith("Łączenie") || currentSongTitle.startsWith("Przełączanie"))) {
                        currentSongTitle = "Na żywo: ${currentStation?.name ?: "Radio"}"
                    }
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val title = mediaMetadata.title?.toString()
                    val artist = mediaMetadata.artist?.toString()
                    updateSongTitleText(title, artist)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (fallbackIndex < fallbackUrls.size) {
                        val nextUrl = fallbackUrls[fallbackIndex]
                        fallbackIndex++
                        currentSongTitle = "Przełączanie na strumień zapasowy ($fallbackIndex/${fallbackUrls.size})..."
                        playDirectUrl(nextUrl)
                    } else {
                        currentSongTitle = "Strumień wymaga WebPlayera (kliknij ikonę 🌐 poniżej)"
                    }
                }
            })
        }, ContextCompat.getMainExecutor(application))

        search("Shropshire")
    }

    private fun ensureDefaultPresetStations() {
        val skey = System.currentTimeMillis() / 1000
        val presets = listOf(
            RadioStation(
                id = "preset-hits-radio-black-country-shropshire",
                name = "Hits Radio Black Country & Shropshire",
                streamUrl = "https://stream-mz.hellorayo.co.uk/freeblackcountry.aac?direct=true&aw_0_1st.skey=$skey&aw_0_1st.playerid=BMUK_RPi",
                country = "UK",
                tags = "https://hellorayo.co.uk/hits-radio/shropshire/",
                favicon = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Hits_Radio_2022.svg/512px-Hits_Radio_2022.svg.png"
            ),
            RadioStation(
                id = "preset-beverley-fm",
                name = "Beverley FM 107.8",
                streamUrl = "https://beverleyfm.com/radio-player/",
                country = "UK",
                tags = "https://beverleyfm.com/radio-player/",
                favicon = ""
            ),
            RadioStation(
                id = "preset-bbc-shropshire",
                name = "BBC Radio Shropshire",
                streamUrl = "http://as-hls-ww-live.akamaized.net/pool_904/live/ww/bbc_radio_shropshire/bbc_radio_shropshire.isml/bbc_radio_shropshire-audio%3d96000.norewind.m3u8",
                country = "UK",
                tags = "Shropshire • Telford • Shrewsbury",
                favicon = ""
            )
        )

        val presetIds = presets.map { it.id }.toSet() + setOf(
            "preset-hits-radio-shropshire-alt",
            "preset-greatest-hits-black-country-shropshire"
        )
        favoriteStations.removeAll { it.id in presetIds }
        favoriteStations.addAll(0, presets)
        saveFavorites()
    }

    private fun updateSongTitleText(title: String?, artist: String?) {
        val stationName = currentStation?.name?.trim()
        currentSongTitle = when {
            !title.isNullOrBlank() && !artist.isNullOrBlank() && title != stationName -> "$artist - $title"
            !title.isNullOrBlank() && title != stationName -> title
            !artist.isNullOrBlank() -> artist
            else -> stationName?.let { "Na żywo: $it" } ?: "Odtwarzanie radia"
        }
    }

    private fun loadFavorites() {
        val json = prefs.getString("favorite_stations", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<RadioStation>>() {}.type
                val savedList: List<RadioStation> = gson.fromJson(json, type)
                favoriteStations.clear()
                favoriteStations.addAll(savedList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveFavorites() {
        val json = gson.toJson(favoriteStations.toList())
        prefs.edit().putString("favorite_stations", json).apply()
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                val apiResults = RetrofitInstance.api.searchStations(query.trim()).toMutableList()
                searchResults = apiResults
            } catch (e: Exception) {
                searchResults = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun playStation(station: RadioStation) {
        val controller = mediaController ?: return
        if (currentStation?.id == station.id && controller.isPlaying) {
            togglePlayPause()
            return
        }
        currentStation = station
        currentSongTitle = "Łączenie z ${station.name}..."

        fallbackUrls.clear()
        fallbackIndex = 0

        val skey = System.currentTimeMillis() / 1000
        val nameLower = station.name.lowercase()

        if (nameLower.contains("hits radio") || nameLower.contains("shropshire") && !nameLower.contains("bbc")) {
            fallbackUrls.addAll(
                listOf(
                    "https://stream-mz.hellorayo.co.uk/freeblackcountry.mp3?direct=true&aw_0_1st.skey=1602676850",
                    "https://stream-al.hellorayo.co.uk/freeshropshire.aac?direct=true&aw_0_1st.skey=$skey",
                    "https://stream-mz.hellorayo.co.uk/hits.aac?direct=true&aw_0_1st.skey=1602676850",
                    "http://edge-bauermz-01-gos2.sharp-stream.com/freeblackcountry.mp3?aw_0_1st.skey=$skey&aw_0_1st.playerid=BMUK_RPi",
                    "http://edge-bauermz-01-gos2.sharp-stream.com/hits.mp3?aw_0_1st.skey=$skey&aw_0_1st.playerid=BMUK_RPi"
                )
            )
        } else if (nameLower.contains("beverley")) {
            fallbackUrls.addAll(
                listOf(
                    "https://stream.zeno.fm/beverley-fm-107-8",
                    "http://s2.radio.co/s774878795/listen"
                )
            )
        }

        viewModelScope.launch {
            val resolvedUrl = resolveStreamFromUrlOrPage(station.streamUrl.trim())
            playDirectUrl(resolvedUrl)
        }
    }

    private fun playDirectUrl(streamUrl: String) {
        val controller = mediaController ?: return
        val station = currentStation ?: return

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(station.name.trim())
            .setArtist(station.country)
            .setSubtitle(station.country)

        if (!station.favicon.isNullOrBlank()) {
            try {
                metadataBuilder.setArtworkUri(Uri.parse(station.favicon.trim()))
            } catch (_: Exception) {}
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(streamUrl)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        controller.stop()
        controller.clearMediaItems()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    // Automatycznie wyciąga bezpośredni link audio ze strony HTML (np. beverleyfm.com/radio-player/) lub pliku .m3u/.pls
    private suspend fun resolveStreamFromUrlOrPage(url: String): String = withContext(Dispatchers.IO) {
        try {
            val lower = url.lowercase()
            if (lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".m3u8") || lower.contains("hellorayo") || lower.contains("sharp-stream")) {
                return@withContext url
            }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val contentType = conn.contentType?.lowercase() ?: ""
            if (contentType.contains("audio") || contentType.contains("mpeg") || contentType.contains("aac") || contentType.contains("ogg")) {
                conn.disconnect()
                return@withContext url
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // Szukamy linku strumienia w kodzie strony lub playliście
            val regex = Regex("""(https?://[^\s"'<>]+(\.mp3|\.aac|\.m3u8|/listen|/stream|radio\.co/[^\s"'<>]+|zeno\.fm/[^\s"'<>]+|broadcast\.radio/[^\s"'<>]+|sharp-stream\.com/[^\s"'<>]+))""", RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            if (match != null) {
                return@withContext match.value
            }
            for (line in text.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    return@withContext trimmed
                }
                if (trimmed.startsWith("File1=", ignoreCase = true)) {
                    return@withContext trimmed.substringAfter("=").trim()
                }
            }
        } catch (_: Exception) {}
        return@withContext url
    }

    fun stopPlayback() {
        mediaController?.pause()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                currentStation?.let { playStation(it) }
            } else {
                controller.play()
            }
        }
    }

    fun nextStation() {
        val current = currentStation ?: return
        val list = if (favoriteStations.any { it.id == current.id }) favoriteStations else searchResults
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == current.id }
        val nextIndex = if (index in 0 until list.size - 1) index + 1 else 0
        playStation(list[nextIndex])
    }

    fun previousStation() {
        val current = currentStation ?: return
        val list = if (favoriteStations.any { it.id == current.id }) favoriteStations else searchResults
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == current.id }
        val prevIndex = if (index > 0) index - 1 else list.size - 1
        playStation(list[prevIndex])
    }

    fun toggleFavorite(station: RadioStation) {
        val existing = favoriteStations.find { it.id == station.id }
        if (existing != null) {
            favoriteStations.remove(existing)
        } else {
            favoriteStations.add(station)
        }
        saveFavorites()
    }

    fun addCustomStation(name: String, url: String, country: String, iconUrl: String = "") {
        val newStation = RadioStation(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            streamUrl = url.trim(),
            country = country.trim().ifEmpty { "Własna" },
            tags = url.trim(),
            favicon = iconUrl.trim()
        )
        favoriteStations.add(0, newStation)
        saveFavorites()
    }

    fun isFavorite(station: RadioStation): Boolean {
        return favoriteStations.any { it.id == station.id }
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
