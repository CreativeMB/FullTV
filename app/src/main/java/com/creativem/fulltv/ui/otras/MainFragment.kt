package com.creativem.fulltv.ui.otras

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.creativem.fulltv.PlayerActivity
import com.creativem.fulltv.R
import com.creativem.fulltv.databinding.MainFragmentBinding
import com.creativem.fulltv.ui.data.Movie
import com.creativem.fulltv.ui.data.RelojCuston
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.WindowCompat

class MainFragment : BrowseSupportFragment() {
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val firestoreRepository = FirestoreRepository()
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingContainer: FrameLayout
    private lateinit var relojhora: RelojCuston
    private lateinit var binding: MainFragmentBinding
    private val relojScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflar el layout de BrowseSupportFragment
        val view = super.onCreateView(inflater, container, savedInstanceState)

        binding = MainFragmentBinding.bind(requireActivity().findViewById(R.id.main))
        // Inflar el layout de carga
        loadingContainer =
            inflater.inflate(R.layout.loading_overlay, container, false) as FrameLayout
        progressBar = loadingContainer.findViewById(R.id.progressBar)
        loadingText = loadingContainer.findViewById(R.id.loadingText)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Agregar la vista de carga como superpuesta
        (view as? ViewGroup)?.addView(loadingContainer)

        val textHora = binding.textHora
        val textfecha = binding.textfecha
        val relojCuston = RelojCuston(textHora, textfecha)
        relojCuston.startClock()
        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Cambiar el color de fondo de toda la pantalla
        view.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                R.color.tu_color_fondo
            )
        ) // Reemplaza con tu color

        // Configurar el adaptador
        adapter = rowsAdapter


        cargarPeliculas()

    }

    private fun cargarPeliculas() {
        // Mostrar la vista de carga
        mostrarCarga("Actualizando biblioteca en linea...")

        CoroutineScope(Dispatchers.Main).launch {
            val (peliculasActivas, peliculasInactivas) = firestoreRepository.obtenerPeliculas() // Asegúrate de que la función esté implementada correctamente

            // Ocultar la vista de carga una vez que se cargan los datos
            ocultarCarga()

            if (peliculasActivas.isNotEmpty()) {
                agregarALista(peliculasActivas, "Disponible")
            } else {
                agregarALista(emptyList(), "No hay Disponible")
            }

            if (peliculasInactivas.isNotEmpty()) {
                agregarALista(peliculasInactivas, "Alquiler")
            } else {
                agregarALista(emptyList(), "No hay Alquiler")
            }

            setOnItemViewClickedListener { _, item, _, _ ->
                if (item is Movie) {
                    val intent = Intent(context, PlayerActivity::class.java)
                    intent.putParcelableArrayListExtra(
                        "movies",
                        ArrayList(if (item.isActive) peliculasActivas else peliculasInactivas)
                    )
                    intent.putExtra("EXTRA_STREAM_URL", item.streamUrl)
                    startActivity(intent)
                }
            }
        }
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
}