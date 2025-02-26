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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TvFragment : RowsSupportFragment() {

    private val db = FirebaseFirestore.getInstance()
    private val channels = ArrayObjectAdapter(ListRowPresenter())
    private lateinit var progressBar: View
    private lateinit var loadingText: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        adapter = channels
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Establecer el color de fondo del fragmento
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        // Inicializar referencias
        progressBar = requireActivity().findViewById(R.id.progressBar)
        loadingText = requireActivity().findViewById(R.id.loadingText)

        loadTvChannels() // Cargar los canales antes de asignar el adapter

    }

    private fun loadTvChannels() {
        mostrarCargando()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = db.collection("tv").orderBy("createdAt").get().await()
                val canales = snapshot.toObjects(Movie::class.java)

                withContext(Dispatchers.Main) {
                    ocultarCargando()
                    if (canales.isNotEmpty()) {
                        organizarEnFilas(canales)
                    } else {
                        Log.e("TvFragment", "No hay canales válidos.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    ocultarCargando()
                    Log.e("TvFragment", "Error cargando canales", e)
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