package com.creativem.fulltv.tv

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import com.creativem.fulltv.principal.AudioFocusHelper
import com.creativem.fulltv.principal.Main
import com.creativem.fulltv.principal.Movie
import com.google.firebase.database.FirebaseDatabase // NUEVO: Import de Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TvFragment : RowsSupportFragment() {

    // NUEVA RUTA: Referencia al nodo "tv" en Realtime Database
    private val databaseRef = FirebaseDatabase.getInstance().getReference("tv")

    private val channels = ArrayObjectAdapter(ListRowPresenter())
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = channels
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restaurar fondo animado por defecto
        (activity as? Main)?.restaurarFondoAnimado()

        // Al seleccionar un canal, actualizar fondo con su imagen
        setOnItemViewSelectedListener { _, item, _, _ ->
            val movie = item as? Movie
            if (movie != null && !movie.imageUrl.isNullOrEmpty()) {
                (activity as? Main)?.setFondoDesdeUrl(movie.imageUrl)
            } else {
                (activity as? Main)?.restaurarFondoAnimado()
            }
        }

        loadTvChannels()
    }

    private fun loadTvChannels() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("TV_DEBUG", "Iniciando consulta a Realtime DB...")

                val snapshot = databaseRef.get().await() // Prueba sin el orderBy primero

                Log.d("TV_DEBUG", "¿Existe el nodo tv?: ${snapshot.exists()}")
                Log.d("TV_DEBUG", "Cantidad de hijos: ${snapshot.childrenCount}")

                val canales = mutableListOf<Movie>()

                for (child in snapshot.children) {
                    val canal = child.getValue(Movie::class.java)
                    Log.d("TV_DEBUG", "Canal encontrado: ${canal?.title}")
                    canal?.let {
                        canales.add(it.copy(id = child.key ?: ""))
                    }
                }

                withContext(Dispatchers.Main) {
                    if (canales.isNotEmpty()) {
                        organizarEnFilas(canales)
                    } else {
                        Log.e("TV_DEBUG", "La lista de canales está vacía.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("TV_DEBUG", "Error crítico: ${e.message}")
                }
            }
        }
    }

    private fun calcularElementosPorFila(): Int {
        val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
        val anchoPantalla = displayMetrics.widthPixels
        val anchoTarjeta = 245
        return (anchoPantalla / anchoTarjeta).coerceAtLeast(1)
    }

    private fun organizarEnFilas(canales: List<Movie>) {
        val cardPresenter = CardPresenterTV()
        val elementosPorFila = calcularElementosPorFila()
        val chunkedCanales = canales.chunked(elementosPorFila)

        // Limpiar canales antes de agregar (por si se llama dos veces)
        channels.clear()

        chunkedCanales.forEachIndexed { index, chunk ->
            val listRowAdapter = ArrayObjectAdapter(cardPresenter).apply {
                addAll(0, chunk)
            }

            val header = if (index == 0) HeaderItem(0, "Canales de TV") else null
            channels.add(ListRow(header, listRowAdapter))
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = requireContext()
            val granted = AudioFocusHelper.requestAudioFocus(context)
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.abandonAudioFocus()
        }
    }
}