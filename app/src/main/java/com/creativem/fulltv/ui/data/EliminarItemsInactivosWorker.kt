package com.creativem.fulltv.ui.data
import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.creativem.fulltv.ui.otras.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EliminarItemsInactivosWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val firestoreRepository = FirestoreRepository()

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // Obtén todas las películas
                val peliculas = firestoreRepository.obtenerPeliculas() as List<Movie>

                // Filtra las películas que tienen URLs inválidas
                val peliculasInvalidas = peliculas.filter { pelicula ->
                    !isValidUrl(pelicula.imageUrl) || !isValidUrl(pelicula.streamUrl)
                }

                // Elimina las películas inválidas
                firestoreRepository.eliminarPeliculas(peliculasInvalidas)

                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure()
            }
        }
    }

    // Método para validar si una URL es válida
    private fun isValidUrl(url: String?): Boolean {
        return try {
            url?.let {
                val uri: Uri = Uri.parse(it)
                uri.scheme != null && uri.host != null
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}