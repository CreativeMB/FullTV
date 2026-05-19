package com.creativem.tvfullurl

// --- MODELOS DE DATOS PARA LA API ---
data class MovieResponse(val results: List<TmdbMovie>)
data class TmdbMovie(
    val id: Int,
    val title: String,
    val original_title: String,
    val poster_path: String?,
    val release_date: String?
)