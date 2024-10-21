package com.creativem.cineflexurl.modelo

import com.google.firebase.Timestamp

data class Movie(
    var id: String = "",       // El ID debe estar presente y no ser nulo
    var userId: String = "", // Agrega el ID del usuario que hizo el pedido
    var userName: String = "",
    var title: String = "",    // Título de la película, valor por defecto vacío
    var year: String = "", // Sinopsis o descripción de la película (antes contenido)
    var imageUrl: String = "", // URL de la imagen de la película
    var streamUrl: String = "", // URL del stream para ver la película
    val createdAt: Timestamp = Timestamp.now()
)