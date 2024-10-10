package com.creativem.fulltv.adapter

import android.util.Log
import com.creativem.fulltv.data.Movie
import com.google.firebase.Timestamp
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

            // Obtener las películas y asignar el campo `createdAt`
            val peliculas = snapshot.documents.mapNotNull { document ->
                document.toObject(Movie::class.java)?.copy(
                    id = document.id,
                    createdAt = document.getTimestamp("createdAt") ?: Timestamp.now()
                )
            }

            // Validar las URLs y separar las películas válidas e inválidas
            val (peliculasValidas, peliculasInvalidas) = peliculas.partition { movie ->
                isUrlValid(movie.streamUrl) // Validar la URL de cada película
            }

            // Ordenar las listas por fecha de publicación
            val peliculasOrdenadasValidas = peliculasValidas.sortedByDescending { it.createdAt.seconds }
            val peliculasOrdenadasInvalidas = peliculasInvalidas.sortedByDescending { it.createdAt.seconds }

            // Retornar las listas ordenadas
            Pair(peliculasOrdenadasValidas, peliculasOrdenadasInvalidas)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), emptyList()) // Retornar listas vacías en caso de error
        }
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
}