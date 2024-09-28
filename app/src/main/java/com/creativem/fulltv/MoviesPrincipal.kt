package com.creativem.fulltv

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MoviesPrincipal : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MovieAdapter
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.movies_principal)

        recyclerView = findViewById(R.id.recycler_view_movies)
        val numberOfColumns = calculateNoOfColumns()
        recyclerView.layoutManager = GridLayoutManager(this, numberOfColumns)

        firestore = Firebase.firestore

        moviesAdapter = MovieAdapter(mutableListOf()) { url ->
            if (url.isNotEmpty()) {
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra("EXTRA_STREAM_URL", url)
                startActivity(intent)
            } else {
                Snackbar.make(recyclerView, "La URL del stream está vacía", Snackbar.LENGTH_LONG).show()
            }
        }

        recyclerView.adapter = moviesAdapter

        loadMoviesFromFirestore()
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
                    val createdAt = document.getTimestamp("createdAt")!!

                    val movie = Movie(title, synopsis, imageUrl, streamUrl, createdAt)
                    moviesAdapter.addMovie(movie)
                }
            }
    }

    private fun calculateNoOfColumns(): Int {
        val displayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        val scalingFactor = 150
        return (dpWidth / scalingFactor).toInt()
    }
}