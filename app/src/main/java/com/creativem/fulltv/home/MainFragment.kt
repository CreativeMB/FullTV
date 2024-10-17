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
                    "Nodotros" -> {
                        val intent = Intent(requireContext(), PedidosActivity::class.java)
                        startActivity(intent)
                    }
                    "Menu 2" -> {
                        Toast.makeText(requireContext(), "Menu 2 seleccionado", Toast.LENGTH_SHORT).show()
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

    }

    private fun cargarPeliculas() {
        Glide.with(requireContext())
            .load("https://ejemplo.com/imagen.jpg")
            .apply(RequestOptions.bitmapTransform(BlurTransformation(15, 3)))
            .centerCrop()
            .into(binding.mainBackgroundImage)

        binding.mainBackgroundImage.alpha = 1.0f

        // Mostrar la vista de carga
        mostrarCarga("Actualizando biblioteca en línea...")

        CoroutineScope(Dispatchers.Main).launch {
            // Obtén las películas actualizadas dentro del coroutine
            val (peliculasActivas, peliculasInactivas) = firestoreRepository.obtenerPeliculas()

            // Ocultar la vista de carga una vez que se cargan los datos
            ocultarCarga()

            // Llama a updateMovieList para agregar las películas a la lista
            updateMovieList(peliculasActivas, peliculasInactivas)
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
        val menuItems = listOf("Nosotros", "Menu 2", "Menu 3", "Menu 4", "Menu 5")
        val menuIcons = listOf(
            R.drawable.pedidos,
            R.drawable.ic_play,
            R.drawable.ic_play,
            R.drawable.ic_play,
            R.drawable.ic_play
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