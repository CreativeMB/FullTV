package com.creativem.fulltv.tv

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import com.creativem.fulltv.R
import com.creativem.fulltv.data.Movie
import com.google.firebase.firestore.FirebaseFirestore

class TvFragment : RowsSupportFragment() {

    private val db = FirebaseFirestore.getInstance()
    private val channels = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadTvChannels() // Cargar los canales antes de asignar el adapter
        adapter = channels
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Establecer el color de fondo del fragmento
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
    }

    private fun loadTvChannels() {
        db.collection("tv")
            .orderBy("createdAt")
            .get()
            .addOnSuccessListener { documents ->
                val canales = mutableListOf<Movie>()

                for (document in documents) {
                    val channel = document.toObject(Movie::class.java)
                    canales.add(channel)
                }

                if (canales.isNotEmpty()) {
                    organizarEnFilas(canales)
                } else {
                    Log.e("TvFragment", "No hay canales válidos.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("TvFragment", "Error cargando canales", e)
            }
    }

    private fun calcularElementosPorFila(): Int {
        val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
        val anchoPantalla = displayMetrics.widthPixels
        val anchoTarjeta = 200
        return (anchoPantalla / anchoTarjeta).coerceAtLeast(1)
    }

    private fun organizarEnFilas(canales: List<Movie>) {
        val cardPresenter = CardPresenterTV()
        val elementosPorFila = calcularElementosPorFila()
        val chunkedCanales = canales.chunked(elementosPorFila)

        chunkedCanales.forEachIndexed { index, chunk ->
            val listRowAdapter = ArrayObjectAdapter(cardPresenter).apply {
                addAll(0, chunk)
            }

            val header = if (index == 0) HeaderItem(0, "Canales en Vivo Gratis") else null
            channels.add(ListRow(header, listRowAdapter))
        }
    }
}