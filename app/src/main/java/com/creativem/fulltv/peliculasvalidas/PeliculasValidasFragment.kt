package com.creativem.fulltv.peliculasvalidas

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.peliculas.CardPresenter
import com.creativem.fulltv.peliculas.Validacioneslista
import com.creativem.fulltv.principal.Main
import com.creativem.fulltv.principal.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PeliculasValidasFragment : RowsSupportFragment() {
    private val channels = ArrayObjectAdapter(ListRowPresenter())
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = channels
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Manejador al hacer clic en un ítem (usa la clase que sí tiene navegación)
        setOnItemViewClickedListener(ItemViewClickedListener())
        // Manejador al seleccionar un ítem (para cambiar el fondo)
        setOnItemViewSelectedListener { _, item, _, _ ->
            val movie = item as? Movie
            if (movie != null && !movie.imageUrl.isNullOrEmpty()) {
                // 👉 Muestra la imagen del ítem seleccionado como fondo (detiene la animación)
                (activity as? Main)?.setFondoDesdeUrl(movie.imageUrl)
            } else {
                // 👉 Si no hay imagen, restaurar fondo animado
                (activity as? Main)?.restaurarFondoAnimado()
            }
        }

        // Cargar los canales desde Firestore
        loadMovies()
    }


    private fun loadMovies() {


        CoroutineScope(Dispatchers.Main).launch {
            // 🔄 Esperar a que la carga en segundo plano se complete
            Validacioneslista.esperarCarga()

            // ✅ Obtener las películas válidas ya cargadas
            val peliculasOrdenadasValidas = Validacioneslista.obtenerPeliculasValidas()

            if (peliculasOrdenadasValidas.isNotEmpty()) {
                agregarALista(peliculasOrdenadasValidas, "")
            } else {
                Log.e("PeliculasValidasFragment", "No hay películas válidas.")
            }
        }
    }


    private fun calcularElementosPorFila(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        val anchoPantalla = displayMetrics.widthPixels
        val anchoTarjeta = 245
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
            if (item is Movie) { // Si es una película, abre PlayerPeliculas

                val intent = Intent(requireContext(), ApiPeliculaActivity::class.java).apply {
                    putExtra("EXTRA_STREAM_URL", item.streamUrl)
                    putExtra("EXTRA_MOVIE_TITLE", item.title)
                    putExtra("EXTRA_MOVIE_YEAR", item.castv)
                    putExtra("EXTRA_MOVIE_IMAGE_URL", item.imageUrl)
                    putExtra("EXTRA_ORIGINAL_TITLE", item.originalTitle)
                    putExtra("EXTRA_COUNTDOWN", item.countdownMinutes)
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
