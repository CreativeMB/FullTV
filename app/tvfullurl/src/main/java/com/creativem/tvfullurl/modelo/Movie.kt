package com.creativem.cineflexurl.modelo

data class Movie(
    var id: String = "",       // El ID debe estar presente y no ser nulo
    var title: String = "",    // Título de la película, valor por defecto vacío
    var synopsis: String = "", // Sinopsis o descripción de la película (antes contenido)
    var imageUrl: String = "", // URL de la imagen de la película
    var streamUrl: String = "" // URL del stream para ver la película
)