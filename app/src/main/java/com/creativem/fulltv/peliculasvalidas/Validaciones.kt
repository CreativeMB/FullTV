package com.creativem.fulltv.peliculasvalidas

import android.util.Log
import com.creativem.fulltv.principal.Movie
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Validaciones {
    private val databaseRef = FirebaseDatabase.getInstance().reference

    // Aumentamos a 15 hilos para mayor velocidad de ráfaga
    private val connectionPool = Executors.newFixedThreadPool(15).asCoroutineDispatcher()

    // Cliente OkHttp con ráfaga masiva permitida
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS) // Un poco más para evitar errores por red lenta
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .connectionPool(ConnectionPool(100, 1, TimeUnit.MINUTES))
        .dispatcher(Dispatcher(Executors.newFixedThreadPool(15)).apply {
            maxRequests = 100
            maxRequestsPerHost = 50
        })
        .build()

    // MANTENEMOS EL NOMBRE: obtenerPeliculas
    suspend fun obtenerPeliculas(): Pair<List<Movie>, List<Movie>> = withContext(connectionPool) {
        try {
            val snapshot = databaseRef.child("movies").get().await()
            val peliculas = snapshot.children.mapNotNull { child ->
                child.getValue(Movie::class.java)?.copy(id = child.key ?: "")
            }

            // 🟢 OPTIMIZACIÓN: Validación en PARALELO masivo
            val resultados = peliculas.map { movie ->
                async {
                    val esValida = isUrlValid(movie.streamUrl)
                    movie.copy(isValid = esValida)
                }
            }.awaitAll()

            val (validas, invalidas) = resultados.partition { it.isValid }

            Pair(
                validas.sortedByDescending { it.createdAt },
                invalidas.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Log.e("Validaciones", "Error: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }

    // MANTENEMOS EL NOMBRE: isUrlValid
    suspend fun isUrlValid(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val validUrl = if (!url.startsWith("http")) "https://$url" else url
        return try {
            val request = Request.Builder().url(validUrl).head().build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}