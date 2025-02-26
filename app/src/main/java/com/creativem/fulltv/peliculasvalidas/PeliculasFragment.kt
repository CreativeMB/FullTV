package com.creativem.fulltv.peliculasvalidas

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.FirestoreRepository
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.peliculas.CardPresenter  // Cambio aquí
import com.creativem.fulltv.peliculas.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PeliculasFragment : RowsSupportFragment() {
    private val channels = ArrayObjectAdapter(ListRowPresenter())
    private lateinit var progressBar: View
    private lateinit var loadingText: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        adapter = channels
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))

        setOnItemViewClickedListener(ItemViewClickedListener())

        // Referencias a los elementos de carga
        progressBar = requireActivity().findViewById(R.id.progressBar)
        loadingText = requireActivity().findViewById(R.id.loadingText)

        loadMovies()
    }

    private fun loadMovies() {
        mostrarCargando() // Mostrar la barra de progreso

        CoroutineScope(Dispatchers.IO).launch {
            val firestoreRepository = FirestoreRepository()
            val (peliculasOrdenadasValidas, _) = firestoreRepository.obtenerPeliculas()

            withContext(Dispatchers.Main) {
                ocultarCargando() // Ocultar la barra de progreso

                if (peliculasOrdenadasValidas.isNotEmpty()) {
                    agregarALista(peliculasOrdenadasValidas, "Películas en Línea Gratis disponibles para ver de forma ilimitada")
                } else {
                    Log.e("PeliculasFragment", "No hay películas válidas.")
                }
            }
        }
    }

    private fun mostrarCargando() {
        progressBar.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
    }

    private fun ocultarCargando() {
        progressBar.visibility = View.GONE
        loadingText.visibility = View.GONE
    }

    private fun calcularElementosPorFila(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        val anchoPantalla = displayMetrics.widthPixels
        val anchoTarjeta = 200
        return (anchoPantalla / anchoTarjeta).coerceAtLeast(1)
    }

    private fun agregarALista(peliculas: List<Movie>, titulo: String) {
        val cardPresenter = CardPresenter() // Cambio aquí
        val elementosPorFila = calcularElementosPorFila()
        val chunkedPeliculas = peliculas.chunked(elementosPorFila)

        chunkedPeliculas.forEachIndexed { index, chunk ->
            val listRowAdapter = ArrayObjectAdapter(cardPresenter).apply {
                addAll(0, chunk)
            }

            val header = if (index == 0) HeaderItem(0, titulo) else null
            channels.add(ListRow(header, listRowAdapter))
        }
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?
        ) {
            if (item is Movie) { // Si es una película, abre PlayerActivity
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("EXTRA_STREAM_URL", item.streamUrl)
                    putExtra("EXTRA_MOVIE_TITLE", item.title) // Título de la película
                    putExtra("EXTRA_MOVIE_YEAR", item.year) // Año de la película
                }
                startActivity(intent)
            } else { // Si es otro tipo de elemento, muestra un mensaje
                Toast.makeText(
                    requireContext(),
                    "Elemento seleccionado: $item",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


}
