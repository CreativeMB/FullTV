package com.creativem.fulltv.ui.otras

import android.util.Log
import com.creativem.fulltv.ui.data.Movie
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FirestoreRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val peliculasCollection = firestore.collection("movies")

    // Función suspendida para obtener películas
    suspend fun obtenerPeliculas(): Pair<List<Movie>, List<Movie>> {
        return try {
            // Obtiene la colección de películas
            val snapshot = peliculasCollection.get().await()
            val peliculas = snapshot.documents.mapNotNull { document ->
                document.toObject(Movie::class.java)?.copy(id = document.id) // Asignar el ID
            }

            // Validar las URLs de las películas
            val (peliculasValidas, peliculasInvalidas) = validarPeliculas(peliculas)

            // Ordenar las listas por fecha de publicación (createdAt)
            val peliculasOrdenadasValidas = peliculasValidas.sortedByDescending { it.createdAt }
            val peliculasOrdenadasInvalidas = peliculasInvalidas.sortedByDescending { it.createdAt }

            Pair(peliculasOrdenadasValidas, peliculasOrdenadasInvalidas) // Retorna un par de listas
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), emptyList()) // Retorna listas vacías en caso de error
        }
    }

    // Función que valida las películas y retorna dos listas: válidas e inválidas
    private suspend fun validarPeliculas(peliculas: List<Movie>): Pair<List<Movie>, List<Movie>> {
        return withContext(Dispatchers.IO) {
            // Ejecuta la validación de cada URL en paralelo
            val validaciones = peliculas.map { movie ->
                async {
                    val isValid = isUrlValid(movie.streamUrl) // Verifica si el URL es válido
                    movie to isValid
                }
            }

            // Espera a que todas las validaciones se completen y separa las válidas e inválidas
            val resultados = validaciones.awaitAll()
            val peliculasValidas = resultados.filter { it.second }.map { it.first.copy(isValid = true) }
            val peliculasInvalidas = resultados.filterNot { it.second }.map { it.first.copy(isValid = false) }

            Pair(peliculasValidas, peliculasInvalidas)
        }
    }

    private suspend fun isUrlValid(url: String?): Boolean {
        return if (url.isNullOrBlank()) {
            false
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)  // Tiempo de espera de conexión
                        .readTimeout(10, TimeUnit.SECONDS)     // Tiempo de espera de lectura
                        .build()

                    val request = Request.Builder().url(url).head().build()
                    val response = client.newCall(request).execute()
                    response.isSuccessful // Retorna verdadero si el código es 200
                } catch (e: IOException) {
                    false // Retorna falso si ocurre un error
                }
            }
        }
    }

    // Esta función elimina las películas de la colección en Firestore.
    suspend fun eliminarPeliculas(peliculas: List<Movie>) {
        peliculas.forEach { pelicula ->
            try {
                peliculasCollection.document(pelicula.id).delete().await()
                Log.d("FirestoreRepository", "Película eliminada: ${pelicula.title}")
            } catch (e: Exception) {
                Log.e("FirestoreRepository", "Error al eliminar película: ${pelicula.title}", e)
            }
        }
    }
}