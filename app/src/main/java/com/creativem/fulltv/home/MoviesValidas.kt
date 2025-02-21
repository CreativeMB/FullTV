//package com.creativem.fulltv.home
//import android.content.Intent
//import android.os.Bundle
//import android.util.DisplayMetrics
//import android.util.Log
//import android.view.View
//import android.widget.ImageView
//import android.widget.ProgressBar
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.recyclerview.widget.GridLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.creativem.fulltv.R
//import com.creativem.fulltv.adapter.FirestoreRepository
//import com.creativem.fulltv.adapter.MoviesMenuAdapter
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//class MoviesValidas : AppCompatActivity() {
//
//    private lateinit var recyclerMoviesMenu: RecyclerView
//    private lateinit var moviesMenuAdapter: MoviesMenuAdapter
//    private lateinit var progressBar: ProgressBar
//    private lateinit var loadingGif: ImageView
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.movies_validas)
//
//        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//        recyclerMoviesMenu = findViewById(R.id.recycler_movies_menu)
//        loadingGif = findViewById(R.id.loading_gif)
//        progressBar = findViewById(R.id.progress_bar)
//
//        val numberOfColumns = calculateNumberOfColumns(100)
//        recyclerMoviesMenu.layoutManager = GridLayoutManager(this, numberOfColumns)
//
//        initializeRecyclerView()
//        loadMovies()
//    }
//
//    private fun calculateNumberOfColumns(columnWidthDp: Int): Int {
//        val displayMetrics: DisplayMetrics = resources.displayMetrics
//        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
//        return (screenWidthDp / columnWidthDp).toInt().coerceAtLeast(2)
//    }
//
//    private fun initializeRecyclerView() {
//        moviesMenuAdapter = MoviesMenuAdapter(mutableListOf()) { movie ->
//            startMoviePlayback(movie.streamUrl, movie.title, movie.year)
//        }
//        recyclerMoviesMenu.adapter = moviesMenuAdapter
//    }
//
//    private fun loadMovies() {
//        progressBar.visibility = View.VISIBLE // Mostrar ProgressBar al inicio
//        loadingGif.visibility = View.VISIBLE   // Mostrar GIF al inicio
//        CoroutineScope(Dispatchers.IO).launch {
//            val firestoreRepository = FirestoreRepository()
//            val (peliculasOrdenadasValidas, _) = firestoreRepository.obtenerPeliculas()
//
//            withContext(Dispatchers.Main) {
//                progressBar.visibility = View.GONE // Ocultar ProgressBar siempre al final
//                loadingGif.visibility = View.GONE // Ocultar GIF si hay películas
//                if (peliculasOrdenadasValidas.isNotEmpty()) {
//                    recyclerMoviesMenu.visibility = View.VISIBLE
//                    moviesMenuAdapter.updateMovies(peliculasOrdenadasValidas)
//
//                } else {
//                    recyclerMoviesMenu.visibility = View.GONE
//                    Toast.makeText(this@MoviesValidas, "No hay películas válidas.", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    private fun startMoviePlayback(streamUrl: String, movieTitle: String, movieYear: String) {
//        Log.d("MoviesValidas", "streamUrl: $streamUrl, movieTitle: $movieTitle, movieYear: $movieYear") // Verificar datos
//        val intent = Intent(this, PlayerActivity::class.java).apply {
//            putExtra("EXTRA_STREAM_URL", streamUrl)
//            putExtra("EXTRA_MOVIE_TITLE", movieTitle)
//            putExtra("EXTRA_MOVIE_YEAR", movieYear)
//        }
//        startActivity(intent)
//    }
//
//}