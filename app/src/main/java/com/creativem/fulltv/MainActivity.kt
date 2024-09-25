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
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MovieAdapter
    private lateinit var remoteConfig: FirebaseRemoteConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
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

        // Inicializar Firebase Remote Config

        remoteConfig = Firebase.remoteConfig

        // Configurar ajustes de Remote Config
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 10  // Tiempo mínimo entre solicitudes de actualización
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Establecer valores predeterminados para las URLs de transmisión
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        // Obtener configuraciones de Firebase Remote Config y luego inicializar el RecyclerView
        fetchRemoteConfig()
    }

    // Método para obtener y activar las configuraciones desde Firebase Remote Config
    private fun fetchRemoteConfig() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d("MainActivity", "Config params updated: $updated")

                    // Obtener la lista de películas con las URLs de transmisión actualizadas desde Firebase Remote Config
                    moviesAdapter = MovieAdapter(getMoviesList()) { streamUrls ->
                        val intent = Intent(this, PlayerActivity::class.java)
                        intent.putStringArrayListExtra("EXTRA_STREAM_URLS", ArrayList(streamUrls)) // Pasar la lista de URLs
                        startActivity(intent)
                    }
                    recyclerView.adapter = moviesAdapter
                } else {
                    Log.e("MainActivity", "Error fetching Remote Config")
                }
            }
    }

    // Método para calcular el número de columnas basadas en el ancho de la pantalla
    private fun calculateNoOfColumns(): Int {
        val displayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        val scalingFactor = 150  // Ancho aproximado de cada ítem en dp
        return (dpWidth / scalingFactor).toInt()
    }

    // Método para obtener una lista de películas con URLs dinámicas desde Firebase Remote Config
    private fun getMoviesList(): List<Movie> {
        // Obtener las URLs de transmisión desde Firebase Remote Config
        val distritoComediaUrl = remoteConfig.getString("adn40Url")
        val cbnEspanolUrl = remoteConfig.getString("cbnEspanolUrl")
        val fallbackUrl = "https://fallback-url.com/stream1"

        return listOf(
            Movie(
                title = "ADN 40 Noticias",
                imageUrl = "https://play-lh.googleusercontent.com/A20hNlH9G83dpMuG3AkFH58E2A6ChzBrNEY5Qpiec4rzjJ6RqGQEyMsXldKNkD6Uwuo=w240-h480-rw",
                streamUrls = listOf(
                    "#",
                    distritoComediaUrl,  // URL de Firebase Remote Config
                    fallbackUrl
                )
            ),
            Movie(
                title = "CBN Español",
                imageUrl = "https://play-lh.googleusercontent.com/sP0gCCywjsZsSOIcQjoUTos0mIlLonUu3j1aCFpcti5WJjJz1630qVamAhbpXsh-WA",
                streamUrls = listOf(
                    "#",
                    cbnEspanolUrl,  // URL de Firebase Remote Config
                    fallbackUrl
                )
            ),
            Movie(
                title = "Película 3",
                imageUrl = "https://url-to-image.com/movie3.jpg",
                streamUrls = listOf(
                    "#",
                    fallbackUrl,  // URL de respaldo
                    fallbackUrl
                )
            ),
            Movie(
                title = "Película 4",
                imageUrl = "https://url-to-image.com/movie4.jpg",
                streamUrls = listOf(
                    "#",
                    fallbackUrl,
                    fallbackUrl
                )
            ),
            Movie(
                title = "Película 5",
                imageUrl = "https://url-to-image.com/movie5.jpg",
                streamUrls = listOf(
                    "#",
                    fallbackUrl,
                    fallbackUrl
                )
            )
        )
    }
}