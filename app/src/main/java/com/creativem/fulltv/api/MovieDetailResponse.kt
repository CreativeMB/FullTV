package com.creativem.fulltv.api

data class MovieDetailResponse(
    val id: Int,
    val runtime: Int?,
    val genres: List<Genre>,
    val original_language: String
)

data class Genre(
    val id: Int,
    val name: String
)
