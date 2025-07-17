package com.creativem.tvfullurl.modelo

import java.security.Timestamp


data class User(
    var userId: String = "",
    val email: String = "",
    val nombre: String = "",  // Cambiado de 'name' a 'nombre'
    var title: String = "", // Cambiado de 'points' a 'puntos'
    val id: String = "",      // Para almacenar el ID del documento
    val createdAt: Timestamp? = null,
    var estado: String = "activo",
    val castv: Long = 0,
    var isOnline: Boolean = false
)