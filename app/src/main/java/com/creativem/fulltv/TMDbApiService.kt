package com.creativem.fulltv

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TMDbApiService {

    // Buscar películas
    @GET("search/movie")
    fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("query") query: String
    ): Call<MovieResponse>

    // Detalles completos
    @GET("movie/{movie_id}")
    fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String
    ): Call<MovieDetailResponse>

    // Créditos: reparto y crew
    @GET("movie/{movie_id}/credits")
    fun getCredits(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): Call<CreditsResponse>
}

