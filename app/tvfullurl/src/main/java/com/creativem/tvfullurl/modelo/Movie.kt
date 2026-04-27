package com.creativem.cineflexurl.modelo

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties // Evita que la app se cierre si Firebase tiene campos que no están en esta clase
class Movie {
    // 1. Campos con valores por defecto
    // Esto es vital para que Firebase pueda "rellenar" el objeto al leerlo
    var id: String = ""
    var userId: String = ""
    var nombre: String = ""
    var title: String = ""
    var originalTitle: String = ""
    var castv: Int = 0
    var email: String = ""
    var imageUrl: String = ""
    var streamUrl: String = ""
    var trailerUrl: String = ""
    var createdAt: Long = System.currentTimeMillis()
    var countdownMinutes: Int = 0

    // 2. Constructor vacío
    // OBLIGATORIO para Firebase. Sin esto, Firebase no puede hacer 'doc.getValue(Movie::class.java)'
    constructor()

    // 3. Constructor secundario
    // Útil para crear objetos rápidamente desde tu código sin llenar todos los campos
    constructor(id: String, title: String, imageUrl: String, streamUrl: String) {
        this.id = id
        this.title = title
        this.imageUrl = imageUrl
        this.streamUrl = streamUrl
    }

    // 4. Función de clonado manual
    // Como ya no es una 'data class', no tienes el método .copy().
    // Esta función te permite crear una copia modificada fácilmente.
    fun clona(title: String, imageUrl: String, streamUrl: String): Movie {
        val nuevo = Movie()
        nuevo.id = this.id
        nuevo.title = title
        nuevo.imageUrl = imageUrl
        nuevo.streamUrl = streamUrl
        nuevo.createdAt = this.createdAt
        return nuevo
    }
}