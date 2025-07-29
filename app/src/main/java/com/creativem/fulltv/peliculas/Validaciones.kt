package com.creativem.fulltv.peliculas

import android.util.Log
import com.creativem.fulltv.principal.Movie
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
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
    private val firestore = FirebaseFirestore.getInstance()
    private val peliculasCollection = firestore.collection("movies")
    private val db = FirebaseFirestore.getInstance()
    // Pool de conexiones para reutilizar conexiones HTTP
    private val connectionPool = Executors.newFixedThreadPool(8).asCoroutineDispatcher()

    // Función para obtener todas las películas y validar las URLs
    suspend fun obtenerPeliculas(): Pair<List<Movie>, List<Movie>> = withContext(connectionPool) {
        try {
            val snapshot = peliculasCollection.get().await()
            val peliculas = snapshot.documents.mapNotNull { document ->
                document.toObject(Movie::class.java)?.copy(id = document.id)
            }

            // Validación de URLs para todas las películas
            val (peliculasValidas, peliculasInvalidas) = peliculas.partition { movie ->
                isUrlValid(movie.streamUrl)
            }

            // Ordenar las listas por fecha de publicación
            val peliculasOrdenadasValidas = peliculasValidas.sortedByDescending { it.createdAt }
            val peliculasOrdenadasInvalidas = peliculasInvalidas.sortedByDescending { it.createdAt }

            Pair(peliculasOrdenadasValidas, peliculasOrdenadasInvalidas)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), emptyList())
        }
    }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)  // Reducir tiempo de conexión
        .readTimeout(500, TimeUnit.MILLISECONDS)    // Reducir tiempo de lectura
        .writeTimeout(500, TimeUnit.MILLISECONDS)   // Reducir tiempo de escritura
        .connectionPool(ConnectionPool(50, 1, TimeUnit.MINUTES)) // Reducir el tamaño del pool de conexiones
        .dispatcher(Dispatcher(Executors.newFixedThreadPool(8))) // Aumentar los hilos para validar en paralelo
        .retryOnConnectionFailure(true)       // Permitir reintentos
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d("OkHttp", "Sending request to ${request.url}")
            chain.proceed(request)
        }
        .build()

    suspend fun isUrlValid(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false

        val validUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(validUrl)
                    .head() // Usar HEAD en lugar de GET para solo verificar disponibilidad
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful && response.code in 200..299
                }
            } catch (e: IOException) {
                Log.e("Validaciones", "Error de conexión: $validUrl", e)
                false
            } catch (e: IllegalArgumentException) {
                Log.e("Validaciones", "URL malformada: $validUrl", e)
                false
            }
        }
    }

    // Función para obtener la referencia de la colección de películas
    fun obtenerPeliculasRef(): CollectionReference {
        return db.collection("movies") // Asegúrate de que este nombre coincida con tu colección en Firestore
    }

}
