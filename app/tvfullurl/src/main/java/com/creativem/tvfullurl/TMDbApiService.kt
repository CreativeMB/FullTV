package com.creativem.tvfullurl

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
interface TMDbApiService {
    @GET("search/movie")
    fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("query") query: String
    ): Call<MovieResponse>
}