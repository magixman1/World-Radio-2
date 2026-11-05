package com.example.worldradio

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class RadioStation(
    @SerializedName("stationuuid") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("url_resolved") val streamUrl: String,
    @SerializedName("country") val country: String,
    @SerializedName("tags") val tags: String
)

interface RadioBrowserApi {
    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") query: String,
        @Query("limit") limit: Int = 40,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioStation>
}

object RetrofitInstance {
    val api: RadioBrowserApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://de1.api.radio-browser.info/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadioBrowserApi::class.java)
    }
}
