package com.creativem.fulltv.peliculas

// ... (todas tus importaciones necesarias van aquí, he incluido las más importantes)

import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
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


    // --- Fondo Animado ---

    private val handler = Handler(Looper.getMainLooper())
    private var fondoAnimando = false
    private var colorIndex = 0
    private lateinit var fondoDinamico: ImageView

    private val coloresFluorescentes = listOf(
        intArrayOf(0x66FF5E3A.toInt(), 0x66FF2D55.toInt()), // verde claro a fucsia
        intArrayOf(0x6690EE90.toInt(), 0x66DA70D6.toInt()), // verde pastel a violeta claro
        intArrayOf(0x66FFD700.toInt(), 0x66FF69B4.toInt()), // dorado a rosa
        intArrayOf(0x6640E0D0.toInt(), 0x66FF1493.toInt()), // turquesa a fucsia
        intArrayOf(0x66ADD8E6.toInt(), 0x668A2BE2.toInt()), // celeste a violeta
        intArrayOf(0x66FF4500.toInt(), 0x66DAA520.toInt()), // naranja fuerte a dorado suave
        intArrayOf(0x664682B4.toInt(), 0x66E6E6FA.toInt()), // azul acero a lavanda
        intArrayOf(0x66FF7F50.toInt(), 0x6600CED1.toInt()), // coral a azul claro
        intArrayOf(0x66DC143C.toInt(), 0x669370DB.toInt()), // rojo rubí a lila
        intArrayOf(0x66B0E0E6.toInt(), 0x66BA55D3.toInt())

    )


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val fragmentRoot = FrameLayout(requireContext())
        auth = FirebaseAuth.getInstance()
        // Creamos el fondo dinámico
        fondoDinamico = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.6f
        }

        // Inflamos el contenido del fragmento
        val rowsView = super.onCreateView(inflater, container, savedInstanceState)

        // Agregamos primero el fondo, luego el contenido encima
        fragmentRoot.addView(fondoDinamico)
        fragmentRoot.addView(rowsView)

        setDefaultBackground()


        return fragmentRoot
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        CoroutineScope(Dispatchers.IO).launch {
            Validacioneslista.cargarPeliculas()
            withContext(Dispatchers.Main) {
                actualizarSoloEtiquetas()
                setDefaultBackground() // Para que siempre inicie con fondo animado

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

                Glide.with(requireContext())
                    .load(movie.imageUrl)
                    .error(R.drawable.icono)
                    .into(fondoDinamico)

                fondoDinamico.alpha = 0.6f
                fondoDinamico.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                setDefaultBackground()
            }
        }

        escucharCambiosEnPeliculas()

        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            CastvHelper.nuevosusuarios(
                context = requireContext(),
                nombre = currentUser.displayName ?: "Usuario",
                email = currentUser.email
            )
        }


    }
    fun setDefaultBackground() {
        if (fondoAnimando) return
        fondoAnimando = true
        handler.removeCallbacksAndMessages(null)

        fun cambiarColores() {
            val colores = coloresFluorescentes[colorIndex % coloresFluorescentes.size]
            colorIndex++
            val nuevoFondo = GradientDrawable(GradientDrawable.Orientation.TL_BR, colores).apply {
                gradientType = GradientDrawable.LINEAR_GRADIENT
            }
            fondoDinamico.setImageDrawable(nuevoFondo)
            fondoDinamico.apply {
                alpha = 0.9f
                scaleType = ImageView.ScaleType.MATRIX
            }
        }

        cambiarColores()

        handler.postDelayed(object : Runnable {
            override fun run() {
                cambiarColores()
                handler.postDelayed(this, 1500)
            }
        }, 1500)
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

        iniciarEscuchaDeUsuario() // ✅ sin parámetros
    }


    override fun onStop() {
        super.onStop()
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