package com.example.worldradio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

    var isPlaying by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    init {
        loadFavorites()

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
                        name = item.mediaMetadata.title?.toString() ?: "Radio",
                        streamUrl = "",
                        country = item.mediaMetadata.subtitle?.toString() ?: "",
                        tags = ""
                    )
                }
            }

            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
        }, ContextCompat.getMainExecutor(application))

        search("Polska")
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
                searchResults = RetrofitInstance.api.searchStations(query.trim())
            } catch (e: Exception) {
                searchResults = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun playStation(station: RadioStation) {
        val controller = mediaController ?: return
        if (currentStation?.id == station.id) {
            togglePlayPause()
            return
        }
        currentStation = station

        val metadata = MediaMetadata.Builder()
            .setTitle(station.name.trim())
            .setArtist(station.country)
            .setSubtitle(station.country)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(station.streamUrl)
            .setMediaMetadata(metadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
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

    fun addCustomStation(name: String, url: String, country: String) {
        val newStation = RadioStation(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            streamUrl = url.trim(),
            country = country.trim().ifEmpty { "Własna" },
            tags = "Własny strumień"
        )
        favoriteStations.add(newStation)
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
