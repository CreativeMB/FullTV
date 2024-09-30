package com.creativem.fulltv

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class MoviesMenu : BottomSheetDialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MoviesMenuAdapter
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.movies_menu, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_movies)

        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        firestore = Firebase.firestore

        moviesAdapter = MoviesMenuAdapter(mutableListOf()) { url ->
            if (url.isNotEmpty()) {
                val intent = Intent(requireContext(), PlayerActivity::class.java)
                intent.putExtra("EXTRA_STREAM_URL", url)
                startActivity(intent)
            } else {
                Snackbar.make(recyclerView, "La URL del stream está vacía", Snackbar.LENGTH_LONG).show()
            }
        }

        recyclerView.adapter = moviesAdapter
        loadMoviesFromFirestore()

        return view
    }

    private fun loadMoviesFromFirestore() {
        firestore.collection("movies")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    val title = document.getString("title") ?: "Sin título"
                    val synopsis = document.getString("synopsis") ?: "Sin sinopsis"
                    val imageUrl = document.getString("imageUrl") ?: ""
                    val streamUrl = document.getString("streamUrl") ?: ""
                    val createdAt = document.getTimestamp("createdAt")

                    val isValid = isUrlValid(streamUrl) // Llama a isUrlValid

                    val movie = Movie(title, synopsis, imageUrl, streamUrl, createdAt!!, isValid)
                    moviesAdapter.addMovie(movie)
                }
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    // Función para validar la URL (la debes implementar aquí)
    private fun isUrlValid(url: String): Boolean {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).head().build()

        return try {
            val response: Response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            false
        }
    }
}