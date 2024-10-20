package com.creativem.tvfullurl.modelo


data class User(
    val email: String = "",
    val nombre: String = "",  // Cambiado de 'name' a 'nombre'
    val puntos: Int = 0,      // Cambiado de 'points' a 'puntos'
    val id: String = ""       // Para almacenar el ID del documento
)