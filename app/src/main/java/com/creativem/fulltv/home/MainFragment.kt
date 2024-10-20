package com.creativem.fulltv.home


import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
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
                    "Configuracion" -> {
                        Toast.makeText(requireContext(), "Configuracion", Toast.LENGTH_SHORT).show()
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

        cargarPeliculas()

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

    // Función para actualizar el nombre de usuario y la cantidad de Castv
    fun actualizarUsuario(usuario: String, cantidadCastv: Int) {
        binding.textUsuario.text = "$usuario"
        binding.textCastv.text = "$:$cantidadCastv"
    }

    private fun cargarPeliculas() {
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
        val menuItems = listOf("Pago", "Configuracion", "Cerrar Sesión")
        val menuIcons = listOf(
            R.drawable.pago,
            R.drawable.ic_settings,
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
}