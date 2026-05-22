package com.creativem.tvfullurl.modelo

data class User(
    var userId: String = "",
    var correo: String = "",
    var nombre: String = "",
    var estado: String = "activo",
    var isOnline: Boolean = false,
    // Usamos Any? para que Firebase no explote al convertir
    var fechaCreacion: Any? = null,
    var ultimaConexion: Any? = null,
    var castv: Any? = null,
    var totalGastado: Any? = null,
    var createdAt: Any? = null
)