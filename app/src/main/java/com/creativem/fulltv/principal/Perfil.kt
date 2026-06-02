package com.creativem.fulltv.principal

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.leanback.widget.Presenter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculasvalidas.AlquileresAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class Perfil : AppCompatActivity() {

    // Inicialización de Firebase Database
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.perfil)

        // Pantalla completa inmersiva
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val container = findViewById<LinearLayout>(R.id.containerLayout)
        val card1 = findViewById<View>(R.id.cardSoporte)
        val card2 = findViewById<View>(R.id.cardColumna2)
        val card3 = findViewById<View>(R.id.cardNotificacion)

        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // MODO TV / HORIZONTAL
            container.orientation = LinearLayout.HORIZONTAL

            // Definimos que cada tarjeta ocupe 0dp de ancho pero con peso 1 (reparto equitativo)
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            params.setMargins(10, 10, 10, 10)

            card1.layoutParams = params
            card2.layoutParams = params
            card3.layoutParams = params
        } else {
            // MODO MÓVIL / VERTICAL
            container.orientation = LinearLayout.VERTICAL

            // En vertical cada una ocupa todo el ancho
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 10, 0, 10)

            card1.layoutParams = params
            card2.layoutParams = params
            card3.layoutParams = params
        }

        // Asignar contenido HTML
        val col1: TextView = findViewById(R.id.col1)
        val col2: TextView = findViewById(R.id.col2)
        val col3: TextView = findViewById(R.id.col3)

        col1.text = HtmlCompat.fromHtml(getString(R.string.columna_1), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col2.text = HtmlCompat.fromHtml(getString(R.string.columna_2), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col3.text = HtmlCompat.fromHtml(getString(R.string.columna_3), HtmlCompat.FROM_HTML_MODE_LEGACY)

        setupHeader()

        // Ejecutamos la carga de películas alquiladas
        cargarPeliculasAlquiladas()
    }

    // 🟢 Función auxiliar para normalizar los títulos de la misma forma que al guardar
    private fun normalizarClave(texto: String): String {
        return texto.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
    }

    private fun cargarPeliculasAlquiladas() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) return

        val correoKey = user.email!!.replace(".", "_").replace("@", "_")
        val listadoAlquileres = mutableListOf<Modelo.AlquilerItem>()
        val layoutAlquileresContainer = findViewById<LinearLayout>(R.id.layoutAlquileresContainer)

        // 1. Obtenemos los alquileres del usuario
        databaseRef.child("usuarios").child(correoKey).child("alquileres")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val alquileresMap = mutableMapOf<String, Pair<Long, Int>>()

                    for (child in snapshot.children) {
                        val tituloKey = child.key ?: continue // Ej: "La Empleada (2025)"

                        // 🟢 SOLUCIÓN AL ERROR DE CASTEO: Leemos de forma segura como Number
                        val createdAt = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
                        val countdownMinutes = (child.child("countdownMinutes").value as? Number)?.toInt() ?: 0

                        val durationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                        val timeElapsed = System.currentTimeMillis() - createdAt
                        if (durationMillis - timeElapsed > 0) {
                            alquileresMap[tituloKey] = Pair(createdAt, countdownMinutes)
                        } else {
                            // 🟢 LIMPIEZA PASIVA: Borramos el nodo de Firebase de inmediato porque ya expiró
                            child.ref.removeValue()
                        }
                    }

                    if (alquileresMap.isEmpty()) {
                        layoutAlquileresContainer?.visibility = View.GONE
                        return@addOnSuccessListener
                    }

                    // 2. Buscamos las películas globales correspondientes
                    databaseRef.child("movies").get().addOnSuccessListener { moviesSnapshot ->
                        if (moviesSnapshot.exists()) {
                            for (movieChild in moviesSnapshot.children) {
                                val movieData = movieChild.getValue(Modelo::class.java)
                                if (movieData != null) {
                                    movieData.id = movieChild.key ?: ""

                                    val tituloNormalizado = normalizarClave(movieData.title)

                                    if (alquileresMap.containsKey(tituloNormalizado)) {
                                        val alquilerInfo = alquileresMap[tituloNormalizado]!!
                                        listadoAlquileres.add(
                                            Modelo.AlquilerItem(
                                                movie = movieData,
                                                createdAt = alquilerInfo.first,
                                                countdownMinutes = alquilerInfo.second
                                            )
                                        )
                                    }
                                }
                            }

                            // 3. Inicializamos o actualizamos el RecyclerView si hay resultados vigentes
                            if (listadoAlquileres.isNotEmpty()) {
                                layoutAlquileresContainer?.visibility = View.VISIBLE
                                val recycler = findViewById<RecyclerView>(R.id.recyclerMisAlquileres)

                                val orientation = resources.configuration.orientation
                                if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                    recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                                    recycler.isNestedScrollingEnabled = true
                                } else {
                                    recycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
                                    recycler.isNestedScrollingEnabled = false
                                }

                                // Inicialización del adaptador con los callbacks correspondientes
                                recycler.adapter = AlquileresAdapter(
                                    items = listadoAlquileres,
                                    onListEmpty = {
                                        layoutAlquileresContainer?.visibility = View.GONE
                                    },
                                    onItemExpired = { itemExpirado ->
                                        // 🟢 LIMPIEZA ACTIVA: Borramos de Firebase al llegar a cero en tiempo real
                                        val clavePelicula = normalizarClave(itemExpirado.movie.title)
                                        databaseRef.child("usuarios").child(correoKey)
                                            .child("alquileres")
                                            .child(clavePelicula)
                                            .removeValue()
                                            .addOnSuccessListener {
                                                Log.d("CLEANUP", "Registro de alquiler expirado eliminado: $clavePelicula")
                                            }
                                    },
                                    onItemClick = { itemSeleccionado ->
                                        val intent = Intent(this, PlayerPeliculas::class.java).apply {
                                            putExtra("EXTRA_STREAM_URL", itemSeleccionado.movie.streamUrl)
                                            putExtra("EXTRA_MOVIE_TITLE", itemSeleccionado.movie.title)
                                            putExtra("EXTRA_MOVIE_IMAGE_URL", itemSeleccionado.movie.imageUrl)
                                            putExtra("EXTRA_COUNTDOWN", itemSeleccionado.countdownMinutes)
                                        }
                                        startActivity(intent)
                                    }
                                )
                            } else {
                                layoutAlquileresContainer?.visibility = View.GONE
                            }
                        } else {
                            layoutAlquileresContainer?.visibility = View.GONE
                        }
                    }
                } else {
                    layoutAlquileresContainer?.visibility = View.GONE
                    Log.d("ALQUILERES", "El usuario no cuenta con alquileres activos.")
                }
            }
            .addOnFailureListener { e ->
                layoutAlquileresContainer?.visibility = View.GONE
                Log.e("ALQUILERES", "Error al consultar alquileres: ${e.message}")
            }
    }

    private fun setupHeader() {
        val headerView = findViewById<View>(R.id.headerContainer)
        if (headerView != null) {
            val presenter = HeaderPresenter()
            val viewHolder = Presenter.ViewHolder(headerView)
            presenter.onBindViewHolder(viewHolder, null)
            Log.d("HEADER_LOG", "Header vinculado con éxito")
        }
    }
}