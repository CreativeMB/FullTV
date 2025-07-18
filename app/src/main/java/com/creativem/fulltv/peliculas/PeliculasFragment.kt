package com.creativem.fulltv.peliculas

// ... (todas tus importaciones necesarias van aquí, he incluido las más importantes)
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.creativem.fulltv.BuildConfig
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.principal.Main
import com.creativem.fulltv.principal.Movie
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File


class PeliculasFragment : RowsSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val validaciones = Validaciones()




    private lateinit var mainBackgroundImage: ImageView

    // --- Firebase & Estado ---
    private val db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }
    private var datosUsuarioListener: ValueEventListener? = null
    private var userStatusListener: ValueEventListener? = null
    private var isLoggingOut = false
    private var versionRemotaGlobal: String? = null


    // --- Fondo Animado ---
    private var fondoActual: GradientDrawable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fondoAnimando = false
    private var matrizX = 0f
    private var direccion = 1
    private var colorIndex = 0



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        CoroutineScope(Dispatchers.IO).launch {
            Validacioneslista.cargarPeliculas()
            withContext(Dispatchers.Main) {
                actualizarSoloEtiquetas()
            }
        }

        adapter = rowsAdapter
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Movie) {
                irAlReproductor(item)
            }
        }

        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item is Movie) {
                // Llama al método de la Activity para la imagen de la película
                (activity as? Main)?.updateBackground(item.imageUrl)
            } else {
                // Llama al método de la Activity para el fondo por defecto
                (activity as? Main)?.setDefaultBackground()
            }

        }
        (activity as? Main)?.setDefaultBackground()
        escucharCambiosEnPeliculas()
        // Carga inicial
        val currentUser = auth.currentUser
        if (currentUser != null) {
            CastvHelper.actualizarCastvSiNoExiste(requireContext(), currentUser.uid, currentUser.displayName ?: "Usuario", currentUser.email!!)
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

    // --- Métodos de UI (Fondo y Carga) ---

    /*private fun mostrarCarga(mensaje: String = "Cargando...") {
        (activity as? Main)?.showLoading(mensaje)
    }

    private fun ocultarCarga() {
        (activity as? Main)?.hideLoading()
    }*/



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

    // --- Lógica de Pedidos y Pagos (Reintegrada) ---

    private fun subirPedidoAFirestore(pedido: String) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = databaseRef.child("usuarios").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val nombreUsuario = snapshot.child("nombre").getValue(String::class.java) ?: "N/A"
            val emailUsuario = snapshot.child("correo").getValue(String::class.java) ?: "N/A"
            val castvActual = snapshot.child("castv").getValue(Int::class.java) ?: 0
            val puntosDescontar = 20

            if (castvActual >= puntosDescontar) {
                val mensaje = "Confirmas el pedido de '$pedido' por $puntosDescontar CasTV?"
                AlertDialog.Builder(requireContext())
                    .setTitle("Confirmar Pedido")
                    .setMessage(mensaje)
                    .setPositiveButton("Confirmar") { _, _ ->
                        val pedidoData = hashMapOf(
                            "title" to pedido, "userId" to userId, "email" to emailUsuario,
                            "nombre" to nombreUsuario, "CasTV" to puntosDescontar.toString()
                        )
                        db.collection("pedidosmovies").add(pedidoData)
                            .addOnSuccessListener {
                                descontarPuntos(userId, puntosDescontar)
                                enviarCorreoNuevoPedido(pedido)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("Cancelar", null).show()
            } else {
                Toast.makeText(requireContext(), "No tienes suficientes puntos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun descontarPuntos(userId: String, puntosADescontar: Int) {
        val userRef = databaseRef.child("usuarios").child(userId)
        userRef.child("castv").setValue(ServerValue.increment(-puntosADescontar.toLong()))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Pedido enviado y CasTV descontado", Toast.LENGTH_SHORT).show()
            }
    }

    private fun enviarCorreoNuevoPedido(pedido: String) {
        val url = "https://server-csks8w.fly.dev/correo"
        val jsonBody = JSONObject().put("titulo", pedido)
        val requestQueue = Volley.newRequestQueue(requireContext())
        val jsonRequest = object : JsonObjectRequest(Method.POST, url, jsonBody,
            Response.Listener { Log.d("Email", "Correo enviado: $it") },
            Response.ErrorListener { Log.e("Email", "Error correo: ${it.message}") }
        ) { override fun getBodyContentType() = "application/json; charset=utf-8" }
        requestQueue.add(jsonRequest)
    }


    // --- Ciclo de Vida y Estado de Usuario ---

    override fun onStart() {
        super.onStart()
        val user = auth.currentUser ?: return
        iniciarEscuchaDeUsuario(user.uid)
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

    private fun iniciarEscuchaDeUsuario(userId: String) {
        if (datosUsuarioListener != null) return // Evitar múltiples listeners
        datosUsuarioListener = CastvHelper.obtenerDatosUsuario(
            userId = userId,
            onSuccess = { _, _, _, _ -> },
            onFailure = { Log.e("PeliculasFragment", "Error al obtener datos de usuario", it) }
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