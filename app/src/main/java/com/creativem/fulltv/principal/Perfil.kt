package com.creativem.fulltv.principal

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PeliculasActivity
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculasvalidas.AlquileresAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Perfil : AppCompatActivity() {

    // 🔥 Firebase
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // 💾 Caché local para evitar consultas repetidas
    private val cachePeliculas = mutableMapOf<String, Modelo>()
    private var peliculasListener: ValueEventListener? = null

    // 📍 Estado del RecyclerView
    private var posicionAlquileresGuardada = 0
    private var adapterAlquileres: AlquileresAdapter? = null

    // 🎯 Views cacheadas (evita findViewById repetido)
    private lateinit var layoutAlquileresContainer: LinearLayout
    private lateinit var recyclerAlquileres: RecyclerView
    private lateinit var progressCarga: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.perfil)

        // 🎬 Pantalla completa inmersiva
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        // 🎯 Cachear views (solo 1 findViewById en toda la Activity)
        layoutAlquileresContainer = findViewById(R.id.layoutAlquileresContainer)
        recyclerAlquileres = findViewById(R.id.recyclerMisAlquileres)
        progressCarga = findViewById(R.id.progressCargaAlquileres) // ⭐ Agrégalo al XML


        // 1. Inicializar el Header mediante el Helper
        // Pasamos el root view (puedes usar findViewById(android.R.id.content) si no tienes un ID raíz)
        val rootView = findViewById<View>(android.R.id.content)
        CastvHelper.inicializarHeader(rootView) { nombre, castv ->
            Log.d("PERFIL", "Header cargado para: $nombre con $castv créditos")
        }
        configurarLayout()
        configurarTextos()
        // ⭐ Cargar películas globales UNA SOLA VEZ (caché)
        cargarPeliculasEnCache()
        // Luego cargar los alquileres del usuario
        cargarPeliculasAlquiladas()
    }

    // ==========================================
    // 1. CONFIGURACIÓN DE UI
    // ==========================================

    private fun configurarLayout() {
        val layoutColumnasExplicativas = findViewById<LinearLayout>(R.id.layoutColumnasExplicativas)

        val cards = listOf(
            findViewById<View>(R.id.cardSoporte),
            findViewById<View>(R.id.cardColumna2),
            findViewById<View>(R.id.cardColumna3)
        )

        val esHorizontal = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        layoutColumnasExplicativas.orientation = if (esHorizontal) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }

        cards.forEach { card ->
            if (card != null) {
                card.layoutParams = if (esHorizontal) {
                    // 🟢 Usamos MATCH_PARENT para obligar a que todas se alineen a la altura de la más larga
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f).apply {
                        setMargins(8, 8, 8, 8)
                    }
                } else {
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 10, 0, 10)
                    }
                }
            }
        }
    }

    private fun configurarTextos() {
        val columnas = listOf(
            findViewById<TextView>(R.id.col1) to R.string.columna_1,
            findViewById<TextView>(R.id.col2) to R.string.columna_2,
            findViewById<TextView>(R.id.col3) to R.string.columna_3
        )

        columnas.forEach { (textView, stringRes) ->
            textView.text = HtmlCompat.fromHtml(
                getString(stringRes),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        }
    }


    // ==========================================
    // 2. CACHÉ DE PELÍCULAS (Optimización clave)
    // ==========================================

    /**
     * ⚡ Carga todas las películas UNA SOLA VEZ y las guarda en memoria
     * Así no tenemos que descargarlas cada vez que consultamos alquileres
     */
    private fun cargarPeliculasEnCache() {
        databaseRef.child("movies").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cachePeliculas.clear()
                for (child in snapshot.children) {
                    val movie = child.getValue(Modelo::class.java) ?: continue
                    movie.id = child.key ?: ""
                    // Guardamos con la clave normalizada para búsqueda O(1)
                    cachePeliculas[normalizarClave(movie.title)] = movie
                }
                Log.d("CACHE", "✅ Películas cacheadas: ${cachePeliculas.size}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CACHE", "❌ Error al cachear películas: ${error.message}")
            }
        })
    }

    // ==========================================
    // 3. CARGA DE ALQUILERES (Optimizada)
    // ==========================================

    private fun cargarPeliculasAlquiladas() {
        val user = auth.currentUser
        val email = user?.email
        if (email == null) {
            ocultarSeccionAlquileres("Usuario no autenticado")
            return
        }

        mostrarCarga(true)

        val correoKey = email.replace(".", "_").replace("@", "_")

        databaseRef.child("usuarios").child(correoKey).child("alquileres")
            .get()
            .addOnSuccessListener { snapshot ->
                procesarAlquileres(snapshot, correoKey)
            }
            .addOnFailureListener { e ->
                mostrarCarga(false)
                ocultarSeccionAlquileres("Error: ${e.message}")
            }
    }

    /**
     * Procesa los alquileres usando la caché local (sin segunda consulta a Firebase)
     */
    /**
     * Procesa los alquileres usando la caché local (sin segunda consulta a Firebase)
     * Filtra los alquileres futuros para que solo aparezcan 1 hora antes de la transmisión.
     */
    private fun procesarAlquileres(snapshot: DataSnapshot, correoKey: String) {
        if (!snapshot.exists()) {
            mostrarCarga(false)
            ocultarSeccionAlquileres("Sin alquileres activos")
            return
        }

        val alquileresVigentes = mutableListOf<Modelo.AlquilerItem>()
        val ahora = System.currentTimeMillis()

        // 1. Filtrar alquileres vigentes y limpiar los expirados
        for (child in snapshot.children) {
            val tituloKey = child.key ?: continue
            val createdAt = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
            val countdownMinutes = (child.child("countdownMinutes").value as? Number)?.toInt() ?: 0

            val tiempoTranscurrido = ahora - createdAt

            // 🟢 CLAVE: El alquiler solo se muestra en el perfil si ya inició su hora de activación (tiempoTranscurrido >= 0)
            if (tiempoTranscurrido >= 0) {
                val durationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                val tiempoRestante = durationMillis - tiempoTranscurrido

                if (tiempoRestante > 0) {
                    // BÚSQUEDA EN CACHÉ (O(1))
                    val movie = cachePeliculas[tituloKey]
                    if (movie != null) {
                        alquileresVigentes.add(
                            Modelo.AlquilerItem(
                                movie = movie,
                                createdAt = createdAt,
                                countdownMinutes = countdownMinutes
                            )
                        )
                    }
                } else {
                    // 🧹 Limpieza pasiva: borrar expirados
                    child.ref.removeValue()
                        .addOnSuccessListener {
                            Log.d("CLEANUP", "🗑️ Alquiler expirado eliminado: $tituloKey")
                        }
                }
            } else {
                // ⏳ El alquiler está programado para el futuro.
                // Se mantiene oculto en el perfil del usuario hasta que falte exactamente 1 hora para su inicio.
                Log.d("PROGRAMADO", "📅 El alquiler de $tituloKey está programado para más adelante. No se muestra todavía.")
            }
        }

        mostrarCarga(false)

        if (alquileresVigentes.isEmpty()) {
            ocultarSeccionAlquileres("Sin alquileres vigentes")
            return
        }

        // 2. Mostrar el RecyclerView
        mostrarSeccionAlquileres(alquileresVigentes, correoKey)
    }

    // ==========================================
    // 4. CONFIGURACIÓN DEL RECYCLERVIEW
    // ==========================================

    private fun mostrarSeccionAlquileres(
        alquileres: List<Modelo.AlquilerItem>,
        correoKey: String
    ) {
        layoutAlquileresContainer.visibility = View.VISIBLE
        configurarRecyclerView()

        val listaMutable = alquileres.toMutableList()

        if (adapterAlquileres == null) {
            adapterAlquileres = AlquileresAdapter(
                items = listaMutable,
                onListEmpty = { ocultarSeccionAlquileres("Lista vacía") },
                onItemExpired = { itemExpirado ->
                    eliminarAlquilerDeFirebase(correoKey, itemExpirado.movie.title)
                },
                onItemClick = { abrirReproductor(it) }
            )
            recyclerAlquileres.adapter = adapterAlquileres
        } else {
            adapterAlquileres?.actualizarLista(listaMutable)
        }

        // ⭐ CLAVE: Forzar recálculo de layout después de cargar datos
        recyclerAlquileres.post {
            recyclerAlquileres.requestLayout()

            // Restaurar posición
            val posicion = posicionAlquileresGuardada.coerceAtMost(alquileres.size - 1)
            recyclerAlquileres.scrollToPosition(posicion)
        }
    }

    /**
     * ⚡ Configuración optimizada del RecyclerView
     */
    private fun configurarRecyclerView() {
        // Guardar posición actual antes de cambiar
        val layoutManagerActual = recyclerAlquileres.layoutManager
        if (layoutManagerActual is LinearLayoutManager) {
            posicionAlquileresGuardada = layoutManagerActual.findFirstVisibleItemPosition()
                .coerceAtLeast(0)
        }

        // ⭐ SIEMPRE usar LinearLayoutManager HORIZONTAL (una sola línea)
        recyclerAlquileres.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        ).apply {
            initialPrefetchItemCount = 8 // Pre-carga para scroll fluido
        }

        // ⚡ Optimizaciones para scroll horizontal
        recyclerAlquileres.isNestedScrollingEnabled = true // Necesario para scroll horizontal
        recyclerAlquileres.setHasFixedSize(true)
        recyclerAlquileres.itemAnimator = null // Sin animaciones (más rápido)
        recyclerAlquileres.setItemViewCacheSize(20)
        recyclerAlquileres.overScrollMode = View.OVER_SCROLL_NEVER
        recyclerAlquileres.isFocusable = true

        // Restaurar posición guardada
        recyclerAlquileres.post {
            val posicion = posicionAlquileresGuardada.coerceAtMost(
                (recyclerAlquileres.adapter?.itemCount ?: 1) - 1
            )
            recyclerAlquileres.scrollToPosition(posicion)
        }
    }

    private fun abrirReproductor(item: Modelo.AlquilerItem) {
        val intent = Intent(this, PlayerPeliculas::class.java).apply {
            putExtra("EXTRA_STREAM_URL", item.movie.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", item.movie.title)
            putExtra("EXTRA_MOVIE_IMAGE_URL", item.movie.imageUrl)
            putExtra("EXTRA_COUNTDOWN", item.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", item.createdAt)
        }
        startActivity(intent)
    }

    // ==========================================
    // 5. UTILIDADES
    // ==========================================

    private fun normalizarClave(texto: String): String {
        return texto.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
    }

    private fun eliminarAlquilerDeFirebase(correoKey: String, titulo: String) {
        val clave = normalizarClave(titulo)
        databaseRef.child("usuarios").child(correoKey)
            .child("alquileres")
            .child(clave)
            .removeValue()
            .addOnSuccessListener {
                Log.d("CLEANUP", "🗑️ Alquiler expirado eliminado: $clave")
            }
    }

    private fun mostrarCarga(mostrar: Boolean) {
        if (::progressCarga.isInitialized) {
            progressCarga.visibility = if (mostrar) View.VISIBLE else View.GONE
        }
    }

    private fun ocultarSeccionAlquileres(motivo: String = "") {
        layoutAlquileresContainer.visibility = View.GONE
        if (motivo.isNotBlank()) {
            Log.d("ALQUILERES", motivo)
        }
    }

    // ==========================================
    // 6. CICLO DE VIDA
    // ==========================================

    /**
     * ⭐ Detecta rotación de pantalla sin reiniciar la Activity
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Reconfigurar layout de tarjetas
        configurarLayout()

        // Reconfigurar RecyclerView (mantiene posición)
        if (layoutAlquileresContainer.visibility == View.VISIBLE) {
            configurarRecyclerView()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiamos los listeners de Firebase y del Header
        CastvHelper.limpiarListeners()
        // 🧹 Limpieza para evitar fugas de memoria
        adapterAlquileres = null
        cachePeliculas.clear()
        peliculasListener?.let {
            databaseRef.child("movies").removeEventListener(it)
        }
        Log.d("PERFIL", "🧹 onDestroy completado")
    }
//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            val intent = Intent(this, PeliculasActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
//            }
//            startActivity(intent)
//            finish()
//            return true
//        }
//        return super.onKeyDown(keyCode, event)
//    }
}