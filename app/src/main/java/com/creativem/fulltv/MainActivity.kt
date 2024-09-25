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

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MovieAdapter
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configurar Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Configurar DrawerLayout
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)

        // Configurar RecyclerView con un GridLayoutManager
        recyclerView = findViewById(R.id.recycler_view_movies)
        val numberOfColumns = calculateNoOfColumns()
        recyclerView.layoutManager = GridLayoutManager(this, numberOfColumns)

        // Inicializar FirebaseApp
        FirebaseApp.initializeApp(this)

        // Inicializar Firestore
        firestore = Firebase.firestore

        // Cargar datos desde Firestore
        loadMoviesFromFirestore()
    }

    private fun loadMoviesFromFirestore() {
        firestore.collection("movies")
            .get()
            .addOnSuccessListener { documents ->
                val moviesList = ArrayList<Movie>()
                for (document in documents) {
                    val title = document.getString("title") ?: "Sin título"
                    val synopsis = document.getString("synopsis") ?: "Sin sinopsis"
                    val imageUrl = document.getString("imageUrl") ?: ""
                    val streamUrl = document.getString("streamUrl") ?: ""
                    Log.d("MainActivity", "URL del stream: $streamUrl")
                    Log.d("MainActivity", "Title: $title, Synopsis: $synopsis, Image URL: $imageUrl, Stream URL: $streamUrl")

                    moviesList.add(Movie(title, synopsis, imageUrl, streamUrl))
                }

                // Configurar MovieAdapter
                moviesAdapter = MovieAdapter(moviesList) { url ->
                    if (url.isNotEmpty()) {
                        val intent = Intent(this@MainActivity, PlayerActivity::class.java)
                        intent.putExtra("EXTRA_STREAM_URL", url)
                        startActivity(intent)
                    } else {
                        // Maneja el caso en que la URL esté vacía
                        Log.e("MainActivity", "Error: la URL del stream está vacía.")
                        // Muestra un mensaje de error al usuario, por ejemplo, con un Snackbar
                    }
                }
                recyclerView.adapter = moviesAdapter
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error loading movies: ", e)
                showErrorSnackbar("Error al cargar las películas.")
            }
    }

    // Método para calcular el número de columnas basadas en el ancho de la pantalla
    private fun calculateNoOfColumns(): Int {
        val displayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        val scalingFactor = 150  // Ancho aproximado de cada ítem en dp
        return (dpWidth / scalingFactor).toInt()
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(findViewById(R.id.drawer_layout), message, Snackbar.LENGTH_LONG).show()
    }
}