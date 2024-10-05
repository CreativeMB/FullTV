package com.creativem.fulltv.ui.otras

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
        val peliculasValidas = mutableListOf<Movie>()
        val peliculasInvalidas = mutableListOf<Movie>()

        return try {
            // Obtiene la colección de películas
            val snapshot = peliculasCollection.get().await()
            val peliculas = snapshot.documents.mapNotNull { document ->
                document.toObject(Movie::class.java)?.copy(id = document.id) // Asignar el ID
            }

            // Lanza una corutina para validar cada URL en paralelo
            coroutineScope { // Esto permite el uso de 'async' dentro de este bloque
                val validaciones = peliculas.map { movie ->
                    async(Dispatchers.IO) { // Ejecuta la validación en un contexto IO
                        val isValid = isUrlValid(movie.streamUrl) // Verifica si el URL es válido
                        if (isValid) {
                            peliculasValidas.add(movie.copy(isValid = true))
                        } else {
                            peliculasInvalidas.add(movie.copy(isValid = false))
                        }
                    }
                }

                // Espera a que todas las validaciones se completen
                validaciones.awaitAll()
            }

            // Ordenar las listas por fecha de publicación (createdAt)
            val peliculasOrdenadasValidas = peliculasValidas.sortedByDescending { it.createdAt }
            val peliculasOrdenadasInvalidas = peliculasInvalidas.sortedByDescending { it.createdAt }

            Pair(peliculasOrdenadasValidas, peliculasOrdenadasInvalidas) // Retorna un par de listas
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), emptyList()) // Retorna listas vacías en caso de error
        }
    }

    private suspend fun isUrlValid(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).head().build()
                val response = OkHttpClient().newCall(request).execute()
                response.isSuccessful // Retorna verdadero si el código es 200
            } catch (e: IOException) {
                false // Retorna falso si ocurre un error
            }
        }
    }
    suspend fun eliminarPeliculas(peliculas: List<Movie>) {
        peliculas.forEach { pelicula ->
            firestore.collection("peliculas").document(pelicula.id).delete().await()
        }
    }

}