package com.creativem.fulltv.peliculas


import com.creativem.fulltv.principal.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object validacioneslista {
    private val validaciones = Validaciones()
    private var peliculasValidas: List<Movie> = emptyList()
    private var peliculasInvalidas: List<Movie> = emptyList()
    private var cargado = false

    suspend fun cargarPeliculas() {
        if (cargado) return

        withContext(Dispatchers.IO) {
            val (validas, invalidas) = validaciones.obtenerPeliculas()
            peliculasValidas = validas
            peliculasInvalidas = invalidas
            cargado = true
        }
    }

    suspend fun esperarCarga() {
        while (!cargado) {
            delay(200) // Espera 200ms hasta que esté cargado
        }
    }

    fun obtenerPeliculasValidas(): List<Movie> = peliculasValidas
    fun obtenerPeliculasInvalidas(): List<Movie> = peliculasInvalidas
    fun yaCargado(): Boolean = cargado
}