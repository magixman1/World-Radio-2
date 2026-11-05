package com.example.worldradio

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import java.util.UUID

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val player = ExoPlayer.Builder(application).build()

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
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
        search("Polska")
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
        if (currentStation?.id == station.id) {
            togglePlayPause()
            return
        }
        currentStation = station
        player.setMediaItem(MediaItem.fromUri(station.streamUrl))
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun toggleFavorite(station: RadioStation) {
        val existing = favoriteStations.find { it.id == station.id }
        if (existing != null) {
            favoriteStations.remove(existing)
        } else {
            favoriteStations.add(station)
        }
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
    }

    fun isFavorite(station: RadioStation): Boolean {
        return favoriteStations.any { it.id == station.id }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
