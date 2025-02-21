package com.creativem.fulltv.enlinea

import android.content.res.Resources
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import com.creativem.fulltv.R
import com.creativem.fulltv.adapter.FirestoreRepository
import com.creativem.fulltv.data.Movie
import com.creativem.fulltv.tv.CardPresenterTV
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
        val cardPresenter = CardPresenterTV()
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
}