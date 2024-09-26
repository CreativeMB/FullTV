package com.creativem.fulltv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MovieAdapter
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recycler_view_movies)
        val numberOfColumns = calculateNoOfColumns()
        recyclerView.layoutManager = GridLayoutManager(this, numberOfColumns)

        FirebaseApp.initializeApp(this)
        firestore = Firebase.firestore

        moviesAdapter = MovieAdapter(mutableListOf(), { url ->
            if (url.isNotEmpty()) {
                val intent = Intent(this@MainActivity, PlayerActivity::class.java)
                intent.putExtra("EXTRA_STREAM_URL", url)
                startActivity(intent)
            } else {
                Snackbar.make(recyclerView, "La URL del stream está vacía", Snackbar.LENGTH_LONG).show()
            }
        }, applicationContext)

        recyclerView.adapter = moviesAdapter

        loadMoviesFromFirestore()
    }

    private fun loadMoviesFromFirestore() {
        firestore.collection("movies")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING) // Cambiar timestamp a createdAt
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MainActivity", "Error al cargar las películas: ", e)
                    showErrorSnackbar("Error al cargar las películas.")
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    for (document in snapshot.documents) {
                        val title = document.getString("title") ?: "Sin título"
                        val synopsis = document.getString("synopsis") ?: "Sin sinopsis"
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val streamUrl = document.getString("streamUrl") ?: ""
                        val createdAt = document.getTimestamp("createdAt") ?: Timestamp.now() // Obtener createdAt

                        // Crear una nueva instancia de Movie con createdAt
                        val movie = Movie(title, synopsis, imageUrl, streamUrl, createdAt)

                        if (!moviesAdapter.containsMovie(movie)) {
                            moviesAdapter.addMovie(movie)
                        }
                    }
                } else {
                    Log.d("MainActivity", "No se encontraron documentos.")
                    showErrorSnackbar("No se encontraron películas.")
                }
            }
    }

    private fun calculateNoOfColumns(): Int {
        val displayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        val scalingFactor = 150
        return (dpWidth / scalingFactor).toInt()
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show()
    }
}