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
import kotlinx.coroutines.launch
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
                    if (playing && currentSongTitle.startsWith("Łączenie")) {
                        currentSongTitle = "Na żywo: ${currentStation?.name ?: "Radio"}"
                    }
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val title = mediaMetadata.title?.toString()
                    val artist = mediaMetadata.artist?.toString()
                    updateSongTitleText(title, artist)
                }

                override fun onPlayerError(error: PlaybackException) {
                    currentSongTitle = "Błąd strumienia (kod: ${error.errorCodeName})"
                }
            })
        }, ContextCompat.getMainExecutor(application))

        search("Polska")
    }

    private fun ensureDefaultPresetStations() {
        val presets = listOf(
            RadioStation(
                id = "preset-hits-radio-black-country-shropshire",
                name = "Hits Radio Black Country & Shropshire",
                streamUrl = "https://stream-mz.hellorayo.co.uk/freeblackcountry.aac?direct=true&aw_0_1st.playerid=BMUK_Airable",
                country = "UK",
                tags = "Shropshire • Black Country • Rayo",
                favicon = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Hits_Radio_2022.svg/512px-Hits_Radio_2022.svg.png"
            ),
            RadioStation(
                id = "preset-hits-radio-shropshire-alt",
                name = "Hits Radio Shropshire (Stream 2 MP3)",
                streamUrl = "https://stream-al.hellorayo.co.uk/freeshropshire.mp3?direct=true&aw_0_1st.playerid=BMUK_Airable",
                country = "UK",
                tags = "Shropshire • Telford • Shrewsbury",
                favicon = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Hits_Radio_2022.svg/512px-Hits_Radio_2022.svg.png"
            ),
            RadioStation(
                id = "preset-greatest-hits-black-country-shropshire",
                name = "Greatest Hits Radio Black Country & Shropshire",
                streamUrl = "https://stream-mz.hellorayo.co.uk/net2blackcountry.aac?direct=true&aw_0_1st.playerid=BMUK_Airable",
                country = "UK",
                tags = "70s 80s 90s • Shropshire",
                favicon = "https://upload.wikimedia.org/wikipedia/en/thumb/5/52/Greatest_Hits_Radio_logo.svg/512px-Greatest_Hits_Radio_logo.svg.png"
            )
        )

        // Usuwamy stare wersje tych stacji (ze starymi niedziałającymi linkami) i wstawiamy nowe
        val presetIds = presets.map { it.id }.toSet()
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
                val q = query.trim().lowercase()
                if (q.contains("hits") || q.contains("shropshire") || q.contains("black country") || q.contains("free")) {
                    val preset = RadioStation(
                        id = "preset-hits-radio-black-country-shropshire",
                        name = "Hits Radio Black Country & Shropshire",
                        streamUrl = "https://stream-mz.hellorayo.co.uk/freeblackcountry.aac?direct=true&aw_0_1st.playerid=BMUK_Airable",
                        country = "UK",
                        tags = "Shropshire • Black Country • Rayo",
                        favicon = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Hits_Radio_2022.svg/512px-Hits_Radio_2022.svg.png"
                    )
                    apiResults.removeAll { it.id == preset.id }
                    apiResults.add(0, preset)
                }
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

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(station.name.trim())
            .setArtist(station.country)
            .setSubtitle(station.country)

        if (!station.favicon.isNullOrBlank()) {
            try {
                metadataBuilder.setArtworkUri(Uri.parse(station.favicon.trim()))
            } catch (_: Exception) {}
        }

        val cleanStreamUrl = station.streamUrl.trim()

        val mediaItem = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(cleanStreamUrl)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        controller.stop()
        controller.clearMediaItems()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                controller.prepare()
            }
            controller.play()
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
            tags = "Własny strumień",
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
