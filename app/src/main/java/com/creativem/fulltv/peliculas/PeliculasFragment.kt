package com.creativem.fulltv.peliculas

// ... (todas tus importaciones necesarias van aquí, he incluido las más importantes)

import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Main
import com.creativem.fulltv.principal.Movie
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class PeliculasFragment : RowsSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val validaciones = Validaciones()
    // --- Firebase & Estado ---
    private val db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }
    private var datosUsuarioListener: ValueEventListener? = null
    private var userStatusListener: ValueEventListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fondoAnimando = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        auth = FirebaseAuth.getInstance()
        return super.onCreateView(inflater, container, savedInstanceState)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        CoroutineScope(Dispatchers.IO).launch {
            Validacioneslista.cargarPeliculas()
            withContext(Dispatchers.Main) {
                actualizarSoloEtiquetas()
                (activity as? Main)?.restaurarFondoAnimado()
            }
        }

        adapter = rowsAdapter

        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Movie) {
                irAlReproductor(item)
            }
        }

        setOnItemViewSelectedListener { _, item, _, _ ->
            val movie = item as? Movie
            if (movie != null && !movie.imageUrl.isNullOrEmpty()) {
                handler.removeCallbacksAndMessages(null)
                fondoAnimando = false

                (activity as? Main)?.setFondoDesdeUrl(movie.imageUrl)
            } else {
                (activity as? Main)?.restaurarFondoAnimado()
            }
        }

        escucharCambiosEnPeliculas()
        CastvHelper.limpiarCacheGlide(requireContext()) // en Fragment

        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            CastvHelper.nuevosusuarios(
                context = requireContext(),
                nombre = currentUser.displayName ?: "Usuario",
                email = currentUser.email
            )
        }
    }

    // --- Métodos de Carga de Películas ---
    private fun calcularElementosPorFila(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        val anchoPantalla = displayMetrics.widthPixels
        val anchoTarjeta = 245 // Define el ancho aproximado de cada tarjeta en píxeles
        return (anchoPantalla / anchoTarjeta).coerceAtLeast(1) // Asegura al menos 1 elemento por fila
    }
    private fun escucharCambiosEnPeliculas() {
//        mostrarCarga("Actualizando biblioteca en línea...")
        validaciones.obtenerPeliculasRef().addSnapshotListener { snapshot, error ->

            if (error != null) {
                Log.e("PeliculasFragment", "Error al escuchar cambios: ${error.message}")
                Toast.makeText(requireContext(), "Error al cargar películas", Toast.LENGTH_SHORT).show()

                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val peliculas = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Movie::class.java)?.run { this.copy(id = doc.id) }
                }
                val peliculasOrdenadas = peliculas.sortedByDescending { it.createdAt }
                updateMovieList(peliculasOrdenadas)
            } else {
                Log.d("PeliculasFragment", "No se encontraron películas.")

                updateMovieList(emptyList())
            }
            //            ocultarCarga()
        }
    }

    private fun updateMovieList(peliculas: List<Movie>) {
        rowsAdapter.clear()
        // 🔁 Esta línea aplica el reinicio de contadores vencidos
        val peliculasProcesadas = resetearContadoresVencidos(peliculas)

        // ✅ Agregar encabezado
        val headerPresenter = HeaderPresenter()
        val headerRowAdapter = ArrayObjectAdapter(headerPresenter)
        headerRowAdapter.add(Object()) // puede ser cualquier objeto

        val headerItem = HeaderItem(" ") // título invisible
        rowsAdapter.add(ListRow(headerItem, headerRowAdapter))


        // Luego, agregamos el contenido de las películas
        agregarALista(peliculas, "")

        // Notificamos el cambio de rango si es necesario
        rowsAdapter.notifyArrayItemRangeChanged(
            rowsAdapter.size() - 1,
            1
        ) // Actualiza el rango para el menú
    }

    private fun resetearContadoresVencidos(peliculas: List<Movie>): List<Movie> {
        val ahora = System.currentTimeMillis()
        val db = FirebaseFirestore.getInstance()

        return peliculas.map { pelicula ->
            val createdAtMillis = pelicula.createdAt.toDate().time
            val duracionMillis = pelicula.countdownMinutes * 60_000L
            val estaVencido = pelicula.countdownMinutes > 0 && ahora >= (createdAtMillis + duracionMillis)

            if (estaVencido) {
                Log.d("ResetContador", "⏰ VENCIDO: ${pelicula.title}")

                if (pelicula.id.isNotBlank()) {
                    db.collection("movies").document(pelicula.id)
                        .update("countdownMinutes", 0)
                        .addOnSuccessListener {
                            Log.d("ResetContador", "✅ Reiniciado countdown para ${pelicula.title}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("ResetContador", "❌ Error al resetear countdown", e)
                        }
                }

                // Devolvemos el objeto ya modificado
                pelicula.copy(countdownMinutes = 0)
            } else {
                pelicula
            }
        }
    }


    private fun actualizarSoloEtiquetas() {
        for (i in 0 until rowsAdapter.size()) {
            val row = rowsAdapter[i]
            if (row is ListRow) {
                val adapter = row.adapter as? ArrayObjectAdapter ?: continue
                for (j in 0 until adapter.size()) {
                    adapter.notifyArrayItemRangeChanged(j, 1) // 🔁 Solo re-bindea el ítem
                }
            }
        }
    }
    private fun agregarALista(peliculas: List<Movie>, titulo: String) {
        val cardPresenter = CardPresenter()
        val elementosPorFila = calcularElementosPorFila()

        // Dividir la lista en sublistas del tamaño calculado
        val chunkedPeliculas = peliculas.chunked(elementosPorFila)

        // Agregar el encabezado solo para la primera sublista
        if (chunkedPeliculas.isNotEmpty()) {
            val listRowAdapter = ArrayObjectAdapter(cardPresenter).apply {
                addAll(0, chunkedPeliculas[0])
            }
            rowsAdapter.add(ListRow(HeaderItem(0, titulo), listRowAdapter))
        }

        // Agregar las sublistas restantes sin encabezado
        chunkedPeliculas.drop(1).forEach { chunk ->
            val listRowAdapter = ArrayObjectAdapter(cardPresenter).apply {
                addAll(0, chunk)
            }
            rowsAdapter.add(ListRow(null, listRowAdapter))
        }
    }


    private fun irAlReproductor(movie: Movie) {
        val intent = Intent(requireContext(), ApiPeliculaActivity::class.java).apply {
            putExtra("EXTRA_STREAM_URL", movie.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", movie.title)
            putExtra("EXTRA_MOVIE_CASTV", movie.castv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
            putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
            putExtra("EXTRA_COUNTDOWN", movie.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", movie.createdAt.seconds)
        }
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()

        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email

        if (email.isNullOrBlank()) {
            Log.e("PeliculasFragment", "Correo del usuario no disponible.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CastvHelper.solicitarAudioFocus(requireContext()) // más seguro
        }
        iniciarEscuchaDeUsuario() // ✅ sin parámetros
    }


    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CastvHelper.liberarAudioFocus()
        }

        eliminarListener()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        eliminarListener()
        handler.removeCallbacksAndMessages(null) // Detener animaciones del fondo

    }

    private fun iniciarEscuchaDeUsuario() {
        if (datosUsuarioListener != null) return // Evitar múltiples listeners

        val currentUser = FirebaseAuth.getInstance().currentUser
        val email = currentUser?.email

        if (email.isNullOrBlank()) {
            Log.e("PeliculasFragment", "Correo del usuario no disponible.")
            return
        }

        datosUsuarioListener = CastvHelper.obtenerDatosUsuario(
            email = email,
            onSuccess = { _, _, _, _ -> },
            onFailure = {
                Log.e("PeliculasFragment", "Error al obtener datos de usuario", it)
            }
        )
    }

    private fun eliminarListener() {
        userStatusListener?.let {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                databaseRef.child("usuarios").child(userId).removeEventListener(it)
            }
        }
        userStatusListener = null

        datosUsuarioListener?.let {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                databaseRef.child("usuarios").child(userId).removeEventListener(it)
            }
        }
        datosUsuarioListener = null
    }


}