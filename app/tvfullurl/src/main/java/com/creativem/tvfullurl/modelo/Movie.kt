package com.creativem.cineflexurl.modelo

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties // Evita que la app se cierre si Firebase tiene campos que no están en esta clase
class Movie {
    // 1. Campos con valores por defecto
    var id: String = ""
    var requestTimestamp: Long = 0L // Almacena la fecha exacta de la solicitud
    var userId: String = ""
    var nombre: String = ""
    var title: String = ""
    var originalTitle: String = ""
    var castv: Int = 0
    var email: String = ""
    var imageUrl: String = ""
    var streamUrl: String = ""
    var trailerUrl: String = ""
    var activeRequestId: String = "" // Almacena el ID de la solicitud actual que se va a procesar
    var createdAt: Long = System.currentTimeMillis()
    var countdownMinutes: Int = 0
    var fechaActivacion: String? = null
    var horaActivacion: Int = -1
    // NUEVO CAMPO: Agregado para almacenar el año de la película
    var year: String = ""

    // 2. Constructor vacío obligatorio para Firebase
    constructor()

    // 3. Constructor secundario
    constructor(id: String, title: String, imageUrl: String, streamUrl: String) {
        this.id = id
        this.title = title
        this.imageUrl = imageUrl
        this.streamUrl = streamUrl
    }

    // Constructor secundario opcional (con año incluido)
    constructor(id: String, title: String, imageUrl: String, streamUrl: String, year: String) {
        this.id = id
        this.title = title
        this.imageUrl = imageUrl
        this.streamUrl = streamUrl
        this.year = year
    }

    // 4. Función de clonado manual actualizada con la propiedad 'year'
    fun clona(title: String, imageUrl: String, streamUrl: String): Movie {
        val nuevo = Movie()
        nuevo.id = this.id
        nuevo.title = title
        nuevo.imageUrl = imageUrl
        nuevo.streamUrl = streamUrl
        nuevo.createdAt = this.createdAt
        nuevo.year = this.year // Se asegura de transferir el año al clonar
        return nuevo
    }
}