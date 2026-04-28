package com.creativem.fulltv.api


data class MovieResponse(
    val results: List<TmdbMovie>
)

data class TmdbMovie(
    val id: Int,
    val title: String,
    val original_title: String,
    val overview: String,
    val release_date: String,
    val poster_path: String?,
    val vote_average: Double,
    // Campos adicionales necesarios
    val streamUrl: String = "",         // Puedes manejar esto con una función si lo generas tú
    val imageUrl: String = "",          // Alternativa a poster_path si la construyes tú
    val castv: Int = 0,           // Puedes usar vote_average o un campo auxiliar
)

    {
        // URL de imagen unificada
        val imagenFinal: String?
        get() = when {
            !imageUrl.isNullOrEmpty() -> imageUrl
            !poster_path.isNullOrEmpty() -> "https://image.tmdb.org/t/p/w500$poster_path"
            else -> null
        }
    }

data class CreditsResponse(
    val cast: List<CastMember>,
    val crew: List<CrewMember>
)

data class CastMember(
    val name: String,
    val character: String,
    val profile_path: String? // foto del actor
)

data class CrewMember(
    val name: String,
    val job: String // buscamos "Director"
)
data class PeliculasResponse(
    val results: List<TmdbMovie>,
    val page: Int,
    val total_results: Int,
    val total_pages: Int
)

