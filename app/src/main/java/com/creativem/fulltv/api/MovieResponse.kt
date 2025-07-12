package com.creativem.fulltv.api


data class MovieResponse(
    val results: List<TmdbMovie>
)

data class TmdbMovie(
    val id: Int,
    val title: String,
    val overview: String,
    val release_date: String,
    val poster_path: String?,
    val vote_average: Float,
    // Campos adicionales necesarios
    val streamUrl: String = "",         // Puedes manejar esto con una función si lo generas tú
    val imageUrl: String = "",          // Alternativa a poster_path si la construyes tú
    val year: String = ""              // Puedes usar vote_average o un campo auxiliar
)
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
