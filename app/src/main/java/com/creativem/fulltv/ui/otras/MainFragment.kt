package com.creativem.fulltv.ui.otras

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.creativem.fulltv.PlayerActivity
import com.creativem.fulltv.ui.data.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFragment : BrowseSupportFragment() {
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val firestoreRepository = FirestoreRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = rowsAdapter

        cargarPeliculas()
    }

    private fun cargarPeliculas() {
        CoroutineScope(Dispatchers.Main).launch {
            val (peliculasActivas, peliculasInactivas) = firestoreRepository.obtenerPeliculas()

            if (peliculasActivas.isNotEmpty()) {
                agregarALista(peliculasActivas, "Películas Activas")
            } else {
                agregarALista(emptyList(), "No hay Películas Activas")
            }

            if (peliculasInactivas.isNotEmpty()) {
                agregarALista(peliculasInactivas, "Películas Inactivas")
            } else {
                agregarALista(emptyList(), "No hay Películas Inactivas")
            }

            setOnItemViewClickedListener { _, item, _, _ ->
                if (item is Movie) {
                    val intent = Intent(context, PlayerActivity::class.java)
                    intent.putParcelableArrayListExtra("movies", ArrayList(if (item.isActive) peliculasActivas else peliculasInactivas))
                    intent.putExtra("EXTRA_STREAM_URL", item.streamUrl)
                    startActivity(intent)
                }
            }
        }
    }

    private fun agregarALista(movies: List<Movie>, titulo: String) {
        val cardPresenter = CardPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)
        listRowAdapter.addAll(0, movies)
        val headerItem = HeaderItem(0, titulo)
        rowsAdapter.add(ListRow(headerItem, listRowAdapter))
    }
}