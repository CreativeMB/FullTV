package com.creativem.fulltv.adapter

import android.util.Log
import com.creativem.fulltv.data.Movie
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class FirestoreRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val peliculasCollection = firestore.collection("movies")

    // Pool de conexiones para reutilizar conexiones HTTP
    private val connectionPool = Executors.newFixedThreadPool(8).asCoroutineDispatcher()

    suspend fun obtenerPeliculas(): Pair<List<Movie>, List<Movie>> = coroutineScope {
        try {
            val snapshot = peliculasCollection.get().await()
            val peliculas = snapshot.documents.mapNotNull { document ->
                document.toObject(Movie::class.java)?.copy(id = document.id)
            }

            // Validación en paralelo con límite de concurrencia
            val (peliculasValidas, peliculasInvalidas) =
                validarPeliculasConcurrente(peliculas, maxConcurrentRequests = 16)

            // Ordenar las listas por fecha de publicación
            val peliculasOrdenadasValidas = peliculasValidas.sortedByDescending { it.createdAt }
            val peliculasOrdenadasInvalidas = peliculasInvalidas.sortedByDescending { it.createdAt }

            Pair(peliculasOrdenadasValidas, peliculasOrdenadasInvalidas)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), emptyList())
        }

    }

    private suspend fun validarPeliculasConcurrente(
        peliculas: List<Movie>,
        maxConcurrentRequests: Int
    ): Pair<List<Movie>, List<Movie>> = coroutineScope {
        val deferred = peliculas.map { movie ->
            async(connectionPool) {
                val isValid = isUrlValid(movie.streamUrl)
                movie.copy(isValid = isValid) to isValid // Simplifica la separación
            }
        }

        val resultados = deferred.awaitAll()
        val peliculasValidas = resultados.filter { it.second }.map { it.first }
        val peliculasInvalidas = resultados.filterNot { it.second }.map { it.first }
        Pair(peliculasValidas, peliculasInvalidas)
    }

    private suspend fun isUrlValid(url: String?): Boolean {
        if (url == null) return false

        return withContext(Dispatchers.IO) { // Usa Dispatchers.IO para E/S
            try {
                (URL(url).openConnection() as HttpURLConnection).run {
                    requestMethod = "HEAD"
                    connectTimeout = 2500 // Ajusta el tiempo de espera según tus necesidades
                    readTimeout = 2500
                    responseCode in 200..299
                }
            } catch (e: Exception) {
                false
            }
        }
    }
    // Función para obtener el nombre de usuario
    suspend fun obtenerNombreUsuario(usuarioId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val doc = firestore.collection("users").document(usuarioId).get().await()
                return@withContext if (doc.exists()) {
                    doc.getString("nombre") ?: "Usuario Desconocido"
                } else {
                    Log.e("FirestoreRepository", "El documento no existe")
                    "Usuario Desconocido"
                }
            } catch (e: Exception) {
                Log.e("FirestoreRepository", "Error obteniendo nombre de usuario", e)
                "Error"
            }
        }
    }

    // Función para obtener la cantidad de Castv
    suspend fun obtenerCantidadCastv(usuarioId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val doc = firestore.collection("users").document(usuarioId).get().await()
                return@withContext if (doc.exists()) {
                    doc.getLong("puntos")?.toInt() ?: 0 // Asegúrate de que el campo sea correcto
                } else {
                    Log.e("FirestoreRepository", "El documento no existe")
                    0
                }
            } catch (e: Exception) {
                Log.e("FirestoreRepository", "Error obteniendo cantidad de Castv", e)
                0
            }
        }
    }

}