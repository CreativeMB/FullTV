package com.creativem.fulltv.home

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.lifecycleScope
import com.creativem.fulltv.R
import com.creativem.fulltv.databinding.MainFragmentBinding
import com.creativem.fulltv.data.Movie
import com.creativem.fulltv.data.RelojCuston
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import jp.wasabeef.glide.transformations.BlurTransformation
import com.creativem.fulltv.menu.MenuItem
import com.creativem.fulltv.adapter.CardPresenter
import com.creativem.fulltv.adapter.FirestoreRepository
import com.creativem.fulltv.menu.MenuPresenter
import com.google.firebase.auth.FirebaseAuth
import android.content.res.Resources
class MainFragment : BrowseSupportFragment() {
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val firestoreRepository = FirestoreRepository()
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingContainer: FrameLayout
    private lateinit var binding: MainFragmentBinding
    private val defaultBackgroundColor by lazy {
        ContextCompat.getColor(
            requireContext(),
            R.color.tu_color_fondo
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflar el layout de BrowseSupportFragment
        val view = super.onCreateView(inflater, container, savedInstanceState)

        // Inflar el layout principal
        binding = MainFragmentBinding.bind(requireActivity().findViewById(R.id.main))
        // Inflar el layout de carga (loading overlay)
        loadingContainer =
            inflater.inflate(R.layout.loading_overlay, container, false) as FrameLayout
        progressBar = loadingContainer.findViewById(R.id.progressBar)
        loadingText = loadingContainer.findViewById(R.id.loadingText)

        // Establecer valores iniciales para nombre de usuario y cantidad de Castv
        binding.textUsuario.text = "users" // Cambia [Usuario] por el valor real
        binding.textCastv.text = "Castv" // Cambia el valor según corresponda

        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Iniciar el reloj
        val textHora = binding.textHora
        val textfecha = binding.textfecha
        val relojCuston = RelojCuston(textHora, textfecha)
        relojCuston.startClock()
        // Agregar la vista de entrada a la vista principal
        (view as? ViewGroup)?.addView(loadingContainer)

        // Configura el listener de clics mrnu
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is MenuItem) {
                Log.d("MainFragment", "Menu item clicked: ${item.name}")
                when (item.name) {
                    "Pago" -> {
                        val intent = Intent(requireContext(), Nosotros::class.java)
                        startActivity(intent)
                    }

                    "Buscar" -> {
                        buscarPeliculaDialogo()
                    }
                    "Cerrar" -> {
                        cerrarSesion() // Llama al método de cerrar sesión
                    }
                    else -> {
                        Toast.makeText(requireContext(), "${item.name} seleccionado", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (item is Movie) {
                val intent = Intent(context, PlayerActivity::class.java)
                intent.putExtra("EXTRA_STREAM_URL", item.streamUrl)
                intent.putExtra("EXTRA_MOVIE_TITLE", item.title) // Título de la película
                intent.putExtra("EXTRA_MOVIE_YEAR", item.year) // Año de la película
                startActivity(intent)
            }
        }

        return view
    }
    // Agrega este método para cerrar sesión
    private fun cerrarSesion() {
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()

        // Aquí puedes redirigir al usuario a la pantalla de inicio de sesión o cualquier otra actividad
        val intent = Intent(requireContext(), LoginActivity::class.java) // Cambia a tu actividad de inicio de sesión
        startActivity(intent)
        requireActivity().finish() // Finaliza la actividad actual si es necesario
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setBackgroundColor(defaultBackgroundColor)

        adapter = rowsAdapter // Inicializa el adaptador

        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item is Movie) {
                cargarImagenDeFondo(item.imageUrl)
            } else {
                restablecerColorFondo()
            }
        }
        escucharCambiosEnPeliculas()
        cargarPeliculas()
        actualizarUsuarioInfo()

        // Cargar información del usuario
        val usuarioId = FirebaseAuth.getInstance().currentUser?.uid // Obtén el ID del usuario autenticado

        // Llama a obtenerNombreUsuario y obtenerCantidadCastv dentro de una coroutine
        if (usuarioId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val nombreUsuario = firestoreRepository.obtenerNombreUsuario(usuarioId)
                val cantidadCastv = firestoreRepository.obtenerCantidadCastv(usuarioId)
                actualizarUsuario(nombreUsuario, cantidadCastv) // Actualiza la UI con la información del usuario
            }
        } else {
            // Manejo de usuario no autenticado
            Log.e("MainFragment", "No hay usuario autenticado")
            actualizarUsuario("Usuario Desconocido", 0) // Actualiza la UI con información predeterminada
        }
    }
    private fun actualizarUsuarioInfo() {
        val usuarioId = FirebaseAuth.getInstance().currentUser?.uid // Obtén el ID del usuario autenticado

        // Llama a obtenerNombreUsuario y obtenerCantidadCastv dentro de una coroutine
        if (usuarioId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val nombreUsuario = firestoreRepository.obtenerNombreUsuario(usuarioId)
                val cantidadCastv = firestoreRepository.obtenerCantidadCastv(usuarioId)
                actualizarUsuario(nombreUsuario, cantidadCastv) // Actualiza la UI con la información del usuario
            }
        } else {
            // Manejo de usuario no autenticado
            Log.e("MainFragment", "No hay usuario autenticado")
            actualizarUsuario("Usuario Desconocido", 0) // Actualiza la UI con información predeterminada
        }
    }
    // Sobrescribir el método onResume para actualizar la información del usuario
    override fun onResume() {
        super.onResume()
        actualizarUsuarioInfo() // Actualiza la información del usuario cada vez que el fragmento se vuelve visible
    }
    // Función para actualizar el nombre de usuario y la cantidad de Castv
    fun actualizarUsuario(usuario: String, cantidadCastv: Int) {
        binding.textUsuario.text = "$usuario"
        binding.textCastv.text = "Castv: $:$cantidadCastv"
    }

fun cargarPeliculas() {
    binding.linearLayout.visibility = View.GONE
    Glide.with(requireContext())
        .load("https://png.pngtree.com/thumb_back/fh260/background/20230328/pngtree-stage-shining-lights-background-image_2118261.jpg")
        .apply(RequestOptions.bitmapTransform(BlurTransformation(15, 3)))
        .centerCrop()
        .into(binding.mainBackgroundImage)
    binding.mainBackgroundImage.alpha = 1.0f

    mostrarCarga("Actualizando biblioteca en línea...")

    viewLifecycleOwner.lifecycleScope.launch {
        val peliculas = firestoreRepository.obtenerPeliculasCompleta() // Obtenemos toda la colección
        // Ordenamos por fecha de publicación, siendo la primera la última actualizada
        val peliculasOrdenadas = peliculas.sortedByDescending { it.createdAt }
        ocultarCarga()
        binding.linearLayout.visibility = View.VISIBLE
        updateMovieList(peliculasOrdenadas)
    }
}
    private fun updateMovieList(peliculas: List<Movie>) {
        rowsAdapter.clear()

        // Primero, agregamos el menú
        val menuAdapter = ArrayObjectAdapter(MenuPresenter())
        val menuItems = listOf("Pago", "Buscar", "Cerrar")
        val menuIcons = listOf(R.drawable.pago, R.drawable.buscar, R.drawable.cerrrar)

        menuItems.forEachIndexed { i, item ->
            menuAdapter.add(MenuItem(item, menuIcons[i]))
        }

        // Agregamos el menú al rowsAdapter
        rowsAdapter.add(ListRow(HeaderItem(3, "Menu"), menuAdapter))

        // Luego, agregamos el contenido de las películas
        agregarALista(peliculas, "Contenido")

        // Notificamos el cambio de rango si es necesario
        rowsAdapter.notifyArrayItemRangeChanged(rowsAdapter.size() - 1, 1) // Actualiza el rango para el menú
    }

    // Nueva función para calcular el número de elementos por fila basado en el ancho de pantalla
    private fun calcularElementosPorFila(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        val anchoPantalla = displayMetrics.widthPixels
        val anchoTarjeta = 240 // Define el ancho aproximado de cada tarjeta en píxeles
        return (anchoPantalla / anchoTarjeta).coerceAtLeast(1) // Asegura al menos 1 elemento por fila
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

    private fun cargarImagenDeFondo(url: String) {
        Glide.with(requireContext())
            .load(url)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(1000))
            .into(binding.mainBackgroundImage)
    }

    private fun mostrarCarga(mensaje: String = "Cargando...") {
        loadingText.text = mensaje
        loadingContainer.visibility = View.VISIBLE
    }

    private fun ocultarCarga() {
        loadingContainer.visibility = View.GONE
    }

    private fun restablecerColorFondo() {
        binding.mainBackgroundImage.setImageDrawable(null)
        view?.setBackgroundColor(defaultBackgroundColor)
    }

    private fun escucharCambiosEnPeliculas() {
        firestoreRepository.obtenerPeliculasRef().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("MainFragment", "Error al escuchar cambios: ${error.message}")
                Toast.makeText(requireContext(), "Error al cargar películas", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val peliculas = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Movie::class.java)?.run {
                        this.copy(id = doc.id)
                    }
                }

                val peliculasOrdenadas = peliculas.sortedByDescending { it.createdAt }
                updateMovieList(peliculasOrdenadas) // Elimina el segundo parámetro
                actualizarUsuarioInfo()

            } else {
                Log.d("MainFragment", "No se encontraron películas.")
                Toast.makeText(requireContext(), "No hay películas disponibles", Toast.LENGTH_SHORT).show()
                updateMovieList(emptyList()) // Llama con solo una lista vacía si no hay datos
            }
        }
    }
    private fun buscarPeliculaDialogo() {
        // Creamos el layout para el diálogo usando un EditText, ProgressBar y ListView
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.buscador, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_edit_text)
        val searchResultsView = dialogView.findViewById<ListView>(R.id.list_view)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        // Lista de películas para la búsqueda
        val movieList = mutableListOf<Movie>()
        val filteredMovieList = mutableListOf<Movie>() // Lista para almacenar películas filtradas

        // Adaptador para los resultados de búsqueda
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, filteredMovieList.map { it.title })
        searchResultsView.adapter = adapter

        // Crear el AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()

        // Muestra el ProgressBar y oculta el EditText y la lista al principio
        progressBar.visibility = View.VISIBLE
        searchEditText.visibility = View.GONE
        searchResultsView.visibility = View.GONE

        // Cargar todas las películas desde Firestore sin validaciones
        CoroutineScope(Dispatchers.Main).launch {
            val peliculas = firestoreRepository.obtenerPeliculasCompleta() // Obtenemos todas las películas sin filtrar
            movieList.clear()
            movieList.addAll(peliculas)
            filteredMovieList.clear()
            filteredMovieList.addAll(movieList)

            adapter.clear()
            adapter.addAll(filteredMovieList.map { it.title })

            // Oculta el ProgressBar y muestra el EditText y el ListView cuando los datos estén listos
            progressBar.visibility = View.GONE
            searchEditText.visibility = View.VISIBLE
            searchResultsView.visibility = View.VISIBLE

            adapter.notifyDataSetChanged()
        }
        // Listener para la entrada en el EditText
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                val filteredList = movieList.filter { movie ->
                    movie.title.contains(query, ignoreCase = true)
                }
                filteredMovieList.clear()
                filteredMovieList.addAll(filteredList)
                adapter.clear()
                adapter.addAll(filteredMovieList.map { it.title })
                adapter.notifyDataSetChanged()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener para detectar clic en los elementos de la lista
        searchResultsView.setOnItemClickListener { _, _, position, _ ->
            val selectedMovie = filteredMovieList[position]
            irAlReproductor(selectedMovie)
            dialog.dismiss() // Cierra el diálogo después de seleccionar
        }
    }
    private fun irAlReproductor(movie: Movie) {
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("EXTRA_STREAM_URL", movie.streamUrl)  // Pasa el URL del stream
            putExtra("EXTRA_MOVIE_TITLE", movie.title)     // Pasa el título de la película
            putExtra("EXTRA_MOVIE_YEAR", movie.year)       // Pasa el año de la película
        }
        startActivity(intent) // Inicia la actividad del reproductor
    }

}