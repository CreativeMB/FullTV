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

                    "Buscar Pelicula" -> {
                        buscarPeliculaDialogo()
                    }
                    "Cerrar Sesión" -> {
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
        // Oculta la interfaz mientras se carga la biblioteca
        binding.linearLayout.visibility = View.GONE // Oculta cualquier vista que quieras ocultar (ej. RecyclerView)

        Glide.with(requireContext())
            .load("https://ejemplo.com/imagen.jpg")
            .apply(RequestOptions.bitmapTransform(BlurTransformation(15, 3)))
            .centerCrop()
            .into(binding.mainBackgroundImage)

        binding.mainBackgroundImage.alpha = 1.0f

        // Mostrar la vista de carga
        mostrarCarga("Actualizando biblioteca en línea...")

        // Lanza una coroutine en el contexto Main
        viewLifecycleOwner.lifecycleScope.launch {
            // Obtén las películas actualizadas dentro de la coroutine
            val (peliculasActivas, peliculasInactivas) = firestoreRepository.obtenerPeliculas()

            // Supongamos que también obtienes el nombre de usuario y la cantidad de Castv
            val usuarioId = FirebaseAuth.getInstance().currentUser?.uid // Obtén el ID del usuario autenticado
            val nombreUsuario: String
            val cantidadCastv: Int

            if (usuarioId != null) {
                nombreUsuario = firestoreRepository.obtenerNombreUsuario(usuarioId) // Obtiene el nombre de usuario
                cantidadCastv = firestoreRepository.obtenerCantidadCastv(usuarioId) // Obtiene la cantidad de Castv
            } else {
                nombreUsuario = "Usuario Desconocido"
                cantidadCastv = 0
            }

            // Ocultar la vista de carga una vez que se cargan los datos
            ocultarCarga()

            // Muestra nuevamente la vista oculta
            binding.linearLayout.visibility = View.VISIBLE // Muestra la vista oculta (ej. RecyclerView)

            // Llama a updateMovieList para agregar las películas a la lista
            updateMovieList(peliculasActivas, peliculasInactivas)

            // Actualiza el nombre de usuario y la cantidad de Castv
            actualizarUsuario(nombreUsuario, cantidadCastv)
        }
    }

    // Actualiza la función updateMovieList()
    private fun updateMovieList(peliculasActivas: List<Movie>, peliculasInactivas: List<Movie>) {
        // Actualiza la lista de películas en la interfaz de usuario
        rowsAdapter.clear() // Limpia la lista actual
        agregarALista(peliculasActivas, "CARTELERA")
        agregarALista(peliculasInactivas, "ALQUILER")
        // Agrega el menú aquí, después de cargar las películas
        val menuAdapter = ArrayObjectAdapter(MenuPresenter())
        val menuItems = listOf("Pago", "Buscar Pelicula", "Cerrar Sesión")
        val menuIcons = listOf(
            R.drawable.pago,
            R.drawable.buscar,
            R.drawable.ic_shuffle
        )

        for (i in menuItems.indices) {
            val menuItem = MenuItem(menuItems[i], menuIcons[i])
            menuAdapter.add(menuItem)
        }

        val headerItem = HeaderItem(3, "MENU")
        val listRow = ListRow(headerItem, menuAdapter) // Crea la fila del menú

        // Calcula la posición para insertar la fila del menú
        val position = rowsAdapter.size() // Obtén la posición usando `rowsAdapter.size`

        // Agrega la fila del menú al adaptador
        rowsAdapter.add(position, listRow)

        // Notifica la inserción de la fila al adaptador de filas
        rowsAdapter.notifyArrayItemRangeChanged(position, 1)

    }

    private fun cargarImagenDeFondo(url: String) {
        Glide.with(requireContext())
            .load(url)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(1000))
            .into(binding.mainBackgroundImage)
    }

    private fun agregarALista(movies: List<Movie>, titulo: String) {
        val cardPresenter = CardPresenter() // Asegúrate de que CardPresenter esté implementado
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)
        listRowAdapter.addAll(0, movies)
        val headerItem = HeaderItem(0, titulo)
        rowsAdapter.add(ListRow(headerItem, listRowAdapter))
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
    // Método para escuchar cambios en Firestore y actualizar la lista de películas
    private fun escucharCambiosEnPeliculas() {
        firestoreRepository.obtenerPeliculasRef().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("MainFragment", "Error al escuchar cambios: ${error.message}")
                Toast.makeText(requireContext(), "Error al cargar películas", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                // Mapeamos los documentos a objetos Movie
                val peliculas = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Movie::class.java)?.copy(id = doc.id)
                }

                // Validamos las URLs de las películas en una corrutina
                lifecycleScope.launch {
                    val peliculasValidas = mutableListOf<Movie>()
                    val peliculasInvalidas = mutableListOf<Movie>()

                    // Validamos cada URL individualmente
                    peliculas.forEach { pelicula ->
                        val isValid = firestoreRepository.isUrlValid(pelicula.streamUrl)
                        if (isValid) {
                            peliculasValidas.add(pelicula)
                        } else {
                            peliculasInvalidas.add(pelicula)
                        }
                    }

                    // Ordenar las listas por fecha de publicación, de más reciente a más antiguo
                    val peliculasOrdenadasValidas = peliculasValidas.sortedByDescending { it.createdAt }
                    val peliculasOrdenadasInvalidas = peliculasInvalidas.sortedByDescending { it.createdAt }

                    // Actualizamos la lista de películas en la interfaz
                    updateMovieList(peliculasOrdenadasValidas, peliculasOrdenadasInvalidas)
                    actualizarUsuarioInfo()
                }

            } else {
                // Si no hay datos en el snapshot
                Log.d("MainFragment", "No se encontraron películas.")
                Toast.makeText(requireContext(), "No hay películas disponibles", Toast.LENGTH_SHORT).show()
                updateMovieList(emptyList(), emptyList()) // Limpia las listas si no hay datos
            }
        }
    }
    private fun buscarPeliculaDialogo() {
        // Creamos el layout para el diálogo usando un EditText, ProgressBar y ListView
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.buscador, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_edit_text)
        val searchResultsView = dialogView.findViewById<ListView>(R.id.list_view)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar) // ProgressBar

        // Lista de películas para la búsqueda
        val movieList = mutableListOf<Movie>()
        val filteredMovieList = mutableListOf<Movie>() // Nueva lista para películas filtradas

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

        // Cargar las películas desde Firestore
        CoroutineScope(Dispatchers.Main).launch {
            val (peliculasValidas, peliculasInvalidas) = firestoreRepository.obtenerPeliculas()
            movieList.clear()
            movieList.addAll(peliculasValidas + peliculasInvalidas)
            filteredMovieList.clear() // Limpiar la lista filtrada
            filteredMovieList.addAll(movieList) // Agregar todas las películas inicialmente
            adapter.clear()
            adapter.addAll(filteredMovieList.map { it.title }) // Actualiza el adaptador con los títulos

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
                filteredMovieList.clear() // Limpiar la lista filtrada
                filteredMovieList.addAll(filteredList) // Agregar solo los filtrados
                adapter.clear()
                adapter.addAll(filteredMovieList.map { it.title }) // Actualiza el adaptador con los títulos filtrados
                adapter.notifyDataSetChanged()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener para detectar clic en los elementos de la lista
        searchResultsView.setOnItemClickListener { _, _, position, _ ->
            val selectedMovie = filteredMovieList[position] // Obtén la película seleccionada de la lista filtrada
            irAlReproductor(selectedMovie) // Llama a la función para ir al reproductor
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