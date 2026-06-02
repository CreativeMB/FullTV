package com.creativem.fulltv.peliculasvalidas

import com.creativem.fulltv.principal.Modelo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object Validacioneslista {
    // Usamos la clase Validaciones que ya apunta a Realtime Database
    private val validaciones = Validaciones()

    private var peliculasValidas: List<Modelo> = emptyList()
    private var peliculasInvalidas: List<Modelo> = emptyList()
    private var cargado = false


    suspend fun cargarPeliculas(forzarRefresh: Boolean = false) {
        // Si ya está cargado y no queremos forzar, salimos
        if (cargado && !forzarRefresh) return

        withContext(Dispatchers.IO) {
            // Llama a la nueva lógica de Realtime Database
            val (validas, invalidas) = validaciones.obtenerPeliculas()
            contadorCeroEnlaceMuerto(invalidas)
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

    fun obtenerPeliculasValidas(): List<Modelo> = peliculasValidas
    fun obtenerPeliculasInvalidas(): List<Modelo> = peliculasInvalidas
    fun yaCargado(): Boolean = cargado

    private fun contadorCeroEnlaceMuerto(invalidas: List<Modelo>) {
        val database = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("movies")

        invalidas.forEach { movie ->
            // Si el enlace está muerto (está en la lista de inválidas)
            // y aún tiene tiempo en el contador, lo ponemos en 0.
            if (movie.countdownMinutes > 0 && movie.id.isNotEmpty()) {
                database.child(movie.id).child("countdownMinutes").setValue(0)
            }
        }
    }
}