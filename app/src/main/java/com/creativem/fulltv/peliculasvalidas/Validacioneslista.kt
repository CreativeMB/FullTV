package com.creativem.fulltv.peliculasvalidas

import com.creativem.fulltv.principal.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object Validacioneslista {
    // Usamos la clase Validaciones que ya apunta a Realtime Database
    private val validaciones = Validaciones()

    private var peliculasValidas: List<Movie> = emptyList()
    private var peliculasInvalidas: List<Movie> = emptyList()
    private var cargado = false

    /**
     * Carga las películas desde la nueva ruta de Realtime Database.
     * @param forzarRefresh Si es true, ignora la caché y descarga de nuevo.
     */
    suspend fun cargarPeliculas(forzarRefresh: Boolean = false) {
        // Si ya está cargado y no queremos forzar, salimos
        if (cargado && !forzarRefresh) return

        withContext(Dispatchers.IO) {
            // Llama a la nueva lógica de Realtime Database
            val (validas, invalidas) = validaciones.obtenerPeliculas()

            // Los IDs y las fechas (Long) ya vienen procesados desde Validaciones.kt
            peliculasValidas = validas
            peliculasInvalidas = invalidas
            cargado = true
        }
    }

    suspend fun esperarCarga() {
        while (!cargado) {
            delay(200)
        }
    }

    // --- Getters ---
    fun obtenerPeliculasValidas(): List<Movie> = peliculasValidas
    fun obtenerPeliculasInvalidas(): List<Movie> = peliculasInvalidas
    fun yaCargado(): Boolean = cargado

    /**
     * Limpia la caché por si necesitas que la App vuelva a consultar
     * la base de datos (útil si agregaste pelis nuevas en el panel).
     */
    fun resetearCache() {
        cargado = false
        peliculasValidas = emptyList()
        peliculasInvalidas = emptyList()
    }
}