package com.creativem.fulltv.ui.data


import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.creativem.fulltv.ui.otras.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.concurrent.TimeUnit

class EliminarItemsInactivosWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val firestoreRepository = FirestoreRepository()

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("EliminarItemsInactivosWorker", "Inicio del Worker")

                // Obtén todas las películas
                val (peliculasValidas, peliculasInvalidas) = firestoreRepository.obtenerPeliculas()
                Log.d(
                    "EliminarItemsInactivosWorker",
                    "Total de películas válidas: ${peliculasValidas.size}, inválidas: ${peliculasInvalidas.size}"
                )

                // Filtra las películas inválidas más viejas de 24 horas
                val peliculasAEliminar = peliculasInvalidas.filter { pelicula ->
                    val edadEnMilisegundos =
                        System.currentTimeMillis() - pelicula.createdAt.toDate().time
                    val esInvalidaYAntigua = (edadEnMilisegundos > TimeUnit.HOURS.toMillis(24))
                    Log.d(
                        "EliminarItemsInactivosWorker",
                        "Pelicula ID: ${pelicula.id} - ¿Es inválida y antigua? $esInvalidaYAntigua"
                    )
                    esInvalidaYAntigua
                }

                Log.d(
                    "EliminarItemsInactivosWorker",
                    "Peliculas inválidas a eliminar: ${peliculasAEliminar.size}"
                )
                peliculasAEliminar.forEach { pelicula ->
                    Log.d(
                        "EliminarItemsInactivosWorker",
                        "Eliminando película inválida: ID=${pelicula.id}, Título=${pelicula.title}"
                    )
                }

                if (peliculasAEliminar.isNotEmpty()) {
                    Log.d("EliminarItemsInactivosWorker", "Eliminando películas inválidas")
                    firestoreRepository.eliminarPeliculas(peliculasAEliminar)
                }

                Log.d("EliminarItemsInactivosWorker", "Worker finalizado correctamente")
                Result.success()
            } catch (e: Exception) {
                Log.e("EliminarItemsInactivosWorker", "Error al eliminar items", e)
                Result.failure()
            }
        }
    }
}