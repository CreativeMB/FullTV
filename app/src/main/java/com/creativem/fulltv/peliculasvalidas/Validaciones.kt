package com.creativem.fulltv.peliculasvalidas

import android.util.Log
import com.creativem.fulltv.principal.Movie
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Validaciones {
    private val databaseRef = FirebaseDatabase.getInstance().reference

    // Usamos Dispatchers.IO que está optimizado para red y escala mejor que un pool fijo
    private val dispatcher = Dispatchers.IO

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS) // Aumentado a 2s para mayor precisión
        .readTimeout(2, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectionPool(ConnectionPool(50, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 100
            maxRequestsPerHost = 20
        })
        .build()

    suspend fun obtenerPeliculas(): Pair<List<Movie>, List<Movie>> = withContext(dispatcher) {
        try {
            val snapshot = databaseRef.child("movies").get().await()
            val peliculas = snapshot.children.mapNotNull { child ->
                child.getValue(Movie::class.java)?.copy(id = child.key ?: "")
            }

            // Validación en PARALELO masivo usando async
            val resultados = peliculas.map { movie ->
                async {
                    val esValida = isUrlValid(movie.streamUrl)
                    movie.copy(isValid = esValida)
                }
            }.awaitAll()

            val (validas, invalidas) = resultados.partition { it.isValid }

            Pair(
                validas.sortedByDescending { it.createdAt },
                invalidas.sortedByDescending { it.createdAt }
            )
        } catch (e: Exception) {
            Log.e("Validaciones", "Error obteniendo películas: ${e.message}")
            Pair(emptyList(), emptyList())
        }
    }

    suspend fun isUrlValid(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false

        // Limpiar URL y asegurar protocolo
        val validUrl = url.trim().let {
            if (!it.startsWith("http")) "http://$it" else it
        }

        return try {
            val request = Request.Builder()
                .url(validUrl)
                .head() // Primero intentamos HEAD por velocidad
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""

                    // 🛡️ FILTRO CRÍTICO:
                    // Si el link es exitoso pero el contenido es HTML, NO es un video (es un error o publicidad)
                    if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                        return false
                    }

                    // Aceptamos formatos de video comunes o flujos de streaming
                    val esVideo = contentType.contains("video") ||
                            contentType.contains("mpegurl") ||
                            contentType.contains("application/octet-stream") ||
                            contentType.isEmpty() // Algunos servidores no envían tipo, pero el link sirve

                    return esVideo
                }

                // Si el HEAD falla (405 Method Not Allowed), intentamos un GET pequeño como último recurso
                if (response.code == 405 || response.code == 403) {
                    return checkWithGet(validUrl)
                }

                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Algunos servidores bloquean el método HEAD, intentamos un GET limitado
    private fun checkWithGet(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Range", "bytes=0-1024") // Solo pedimos el primer KB para no gastar datos
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful && !(response.header("Content-Type")?.contains("text/html") ?: false)
            }
        } catch (e: Exception) {
            false
        }
    }
}