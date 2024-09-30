package com.creativem.fulltv

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

// Importa la clase Movie
import com.creativem.fulltv.Movie
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class MoviesMenu : BottomSheetDialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MoviesMenuAdapter
    private lateinit var firestore: FirebaseFirestore
    private var dismissListener: (() -> Unit)? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.movies_menu, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_movies)

        recyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        firestore = Firebase.firestore

        moviesAdapter = MoviesMenuAdapter(mutableListOf()) { url ->
            if (url.isNotEmpty()) {
                val intent = Intent(requireContext(), PlayerActivity::class.java)
                intent.putExtra("EXTRA_STREAM_URL", url)
                startActivity(intent)
            } else {
                Snackbar.make(recyclerView, "La URL del stream está vacía", Snackbar.LENGTH_LONG)
                    .show()
            }
        }

        recyclerView.adapter = moviesAdapter
        loadMoviesFromFirestore()

        return view
    }

    override fun onStart() {
        super.onStart()
        // Configurar el BottomSheet para que ocupe todo el espacio
        val dialog = dialog as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun loadMoviesFromFirestore() {
        firestore.collection("movies")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val movies = mutableListOf<Movie>() // Crear una lista temporal para almacenar las películas

                // Cargar las películas
                val jobs = snapshot.documents.map { document ->
                    CoroutineScope(Dispatchers.IO).async {
                        val title = document.getString("title") ?: "Sin título"
                        val synopsis = document.getString("synopsis") ?: "Sin sinopsis"
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val streamUrl = document.getString("streamUrl") ?: ""
                        val createdAt = document.getTimestamp("createdAt") ?: return@async null

                        // Llama a isUrlValid de forma asíncrona
                        val isValid = isUrlValid(streamUrl)

                        // Crea una película y retorna
                        Movie(title, synopsis, imageUrl, streamUrl, createdAt, isValid)
                    }
                }

                // Esperar a que todos los trabajos se completen
                GlobalScope.launch(Dispatchers.Main) {
                    jobs.forEach { job ->
                        job.await()?.let { movie ->
                            movies.add(movie) // Añadir la película a la lista temporal
                        }
                    }

                    // Actualizar el adaptador con la lista de películas
                    moviesAdapter.updateMovies(movies)
                }
            }
    }

    // Cambia la función isUrlValid para que sea suspend
    private suspend fun isUrlValid(url: String): Boolean {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).head().build()

        return try {
            val response: Response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            false
        }
    }
    fun addOnDismissListener(listener: () -> Unit) {
        dismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        dismissListener?.invoke()  // Llama al listener cuando se cierra
    }
}