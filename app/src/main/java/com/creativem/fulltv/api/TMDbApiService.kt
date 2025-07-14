package com.creativem.fulltv.api

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
    // Cartelera
    @GET("movie/now_playing")
    fun getNowPlaying(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "es-MX",
        @Query("page") page: Int = 1
    ): Call<MovieResponse>
// populares
    @GET("movie/popular")
    fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int
    ): Call<MovieResponse>

    @GET("movie/top_rated")
    fun getTopRatedMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int
    ): Call<MovieResponse>

    @GET("discover/movie")
    fun getMoviesByGenre(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("with_genres") genreId: Int,
        @Query("page") page: Int
    ): Call<MovieResponse>

    @GET("movie/now_playing")
    fun getNowPlayingMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int
    ): Call<MovieResponse>

    @GET("search/movie")
    fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): Call<PeliculasResponse>



}

