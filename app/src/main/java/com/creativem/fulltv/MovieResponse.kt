package com.creativem.fulltv


data class MovieResponse(
    val results: List<TmdbMovie>
)

data class TmdbMovie(
    val title: String,
    val overview: String,
    val release_date: String,
    val poster_path: String?,
    val vote_average: Float
)
