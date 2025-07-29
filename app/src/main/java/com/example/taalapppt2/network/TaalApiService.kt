package com.example.taalapppt2.network
import com.example.taalapppt2.data.TaalApiResponse
import retrofit2.http.GET

interface TaalApiService {
    @GET("api/csv-data")
    suspend fun getLatestTaalData(): TaalApiResponse
}