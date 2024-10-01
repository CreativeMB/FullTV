package com.creativem.fulltv

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.ui.data.Movie
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
class MoviesPrincipal : AppCompatActivity() {
    private lateinit var recyclerViewValid: RecyclerView
    private lateinit var recyclerViewInvalid: RecyclerView
    private lateinit var validMoviesAdapter: MovieAdapter
    private lateinit var invalidMoviesAdapter: MovieAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var dimView: View
    private lateinit var loadingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.movies_principal)

        recyclerViewValid = findViewById(R.id.recycler_view_movies_valid)
        recyclerViewInvalid = findViewById(R.id.recycler_view_movies_invalid)
        progressBar = findViewById(R.id.progress_bar)
        dimView = findViewById(R.id.dim_view)
        loadingText = findViewById(R.id.loading_text)

        val numberOfColumns = calculateNoOfColumns()
        recyclerViewValid.layoutManager = GridLayoutManager(this, numberOfColumns)
        recyclerViewInvalid.layoutManager = GridLayoutManager(this, numberOfColumns)

        firestore = Firebase.firestore

        validMoviesAdapter = MovieAdapter(mutableListOf()) { url ->
            if (url.isNotEmpty()) {
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra("EXTRA_STREAM_URL", url)
                startActivity(intent)
            } else {
                Snackbar.make(recyclerViewValid, "La URL del stream está vacía", Snackbar.LENGTH_LONG).show()
            }
        }

        invalidMoviesAdapter = MovieAdapter(mutableListOf()) { url ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("EXTRA_STREAM_URL", url)
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        }

        recyclerViewValid.adapter = validMoviesAdapter
        recyclerViewInvalid.adapter = invalidMoviesAdapter

        loadMoviesFromFirestore()
    }

    private fun loadMoviesFromFirestore() {
        // Muestra el ProgressBar y el fondo oscuro al inicio de la carga
        progressBar.visibility = View.VISIBLE
        dimView.visibility = View.VISIBLE
        loadingText.setVisibility(View.VISIBLE);

        Log.d("MoviesPrincipal", "Cargando películas...") // Log para depuración

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val movies = firestore.collection("movies")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .documents.map { document ->
                        val title = document.getString("title") ?: "Sin título"
                        val synopsis = document.getString("synopsis") ?: "Sin sinopsis"
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val streamUrl = document.getString("streamUrl") ?: ""
                        val createdAt = document.getTimestamp("createdAt")!!
                        val isValid = isUrlValid(streamUrl)
                        Movie(title, synopsis, imageUrl, streamUrl, createdAt, isValid)
                    }

                val validMovies = movies.filter { it.isValid }
                val invalidMovies = movies.filterNot { it.isValid }

                withContext(Dispatchers.Main) {
                    validMoviesAdapter.addMovies(validMovies)
                    invalidMoviesAdapter.addMovies(invalidMovies)

                    Log.d("MoviesPrincipal", "Carga completa.") // Log para depuración

                    // Oculta el ProgressBar y el fondo oscuro después de la carga
                    progressBar.visibility = View.GONE
                    dimView.visibility = View.GONE
                    loadingText.setVisibility(View.GONE);
                }
            } catch (e: Exception) {
                Log.e("MoviesPrincipal", "Error al cargar películas: ${e.message}") // Log de errores
                withContext(Dispatchers.Main) {
                    // Si hay un error, oculta el ProgressBar y el fondo oscuro
                    progressBar.visibility = View.GONE
                    dimView.visibility = View.GONE
                    Snackbar.make(recyclerViewValid, "Error al cargar películas", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun calculateNoOfColumns(): Int {
        val displayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        val scalingFactor = 150
        return (dpWidth / scalingFactor).toInt()
    }

    private suspend fun isUrlValid(url: String): Boolean {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).head().build()

        return withContext(Dispatchers.IO) {
            try {
                val response: Response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: IOException) {
                false
            }
        }
    }
}