package com.creativem.fulltv.peliculas

import android.util.Log
import com.creativem.fulltv.principal.Movie
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.util.concurrent.Executors

class Validaciones {
    // NUEVA RUTA: Referencia a la raíz de Realtime Database
    private val databaseRef = FirebaseDatabase.getInstance().reference

    // Pool de conexiones para reutilizar hilos
    private val connectionPool = Executors.newFixedThreadPool(8).asCoroutineDispatcher()

    // --- FUNCIÓN PRINCIPAL: Obtener y validar películas ---
    suspend fun obtenerPeliculas(): Pair<List<Movie>, List<Movie>> = withContext(connectionPool) {
        try {
            // Leemos el nodo "movies" completo
            val snapshot = databaseRef.child("movies").get().await()

            // Convertimos los hijos del snapshot en una lista de objetos Movie
            val peliculas = snapshot.children.mapNotNull { child ->
                child.getValue(Movie::class.java)?.copy(id = child.key ?: "")
            }

            // Validación de URLs (se mantiene tu lógica original)
            val (peliculasValidas, peliculasInvalidas) = peliculas.partition { movie ->
                isUrlValid(movie.streamUrl)
            }

            // Ordenar por fecha (createdAt ya es Long, así que funciona directo)
            val peliculasOrdenadasValidas = peliculasValidas.sortedByDescending { it.createdAt }
            val peliculasOrdenadasInvalidas = peliculasInvalidas.sortedByDescending { it.createdAt }

            Pair(peliculasOrdenadasValidas, peliculasOrdenadasInvalidas)
        } catch (e: Exception) {
            Log.e("Validaciones", "Error al obtener películas de Realtime DB", e)
            Pair(emptyList(), emptyList())
        }
    }

    // --- Lógica de OKHTTP (Se mantiene idéntica para no romper la validación de URLs) ---
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .writeTimeout(500, TimeUnit.MILLISECONDS)
        .connectionPool(ConnectionPool(50, 1, TimeUnit.MINUTES))
        .dispatcher(Dispatcher(Executors.newFixedThreadPool(8)))
        .retryOnConnectionFailure(true)
        .build()

    suspend fun isUrlValid(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(validUrl).head().build()
                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful && response.code in 200..299
                }
            } catch (e: IOException) {
                false
            }
        }
    }


}