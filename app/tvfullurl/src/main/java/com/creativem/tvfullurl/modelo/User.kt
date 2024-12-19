package com.creativem.tvfullurl.modelo

import java.security.Timestamp


data class User(
    val email: String = "",
    val nombre: String = "",  // Cambiado de 'name' a 'nombre'
    var title: String = "",
    val puntos: Int = 0,      // Cambiado de 'points' a 'puntos'
    val id: String = "",       // Para almacenar el ID del documento
    val createdAt: Timestamp? = null
)