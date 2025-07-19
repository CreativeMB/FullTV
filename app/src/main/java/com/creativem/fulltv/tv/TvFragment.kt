package com.creativem.fulltv.tv

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.AudioFocusHelper
import com.creativem.fulltv.principal.Main
import com.creativem.fulltv.principal.Movie
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TvFragment : RowsSupportFragment() {

    private val db = FirebaseFirestore.getInstance()
    private val channels = ArrayObjectAdapter(ListRowPresenter())
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = channels
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = channels

        // Notificar a la Activity que restaure el fondo animado por defecto
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
                val snapshot = db.collection("tv").orderBy("createdAt").get().await()
                val canales = snapshot.toObjects(Movie::class.java)

                withContext(Dispatchers.Main) {

                    if (canales.isNotEmpty()) {
                        organizarEnFilas(canales)
                    } else {
                        Log.e("TvFragment", "No hay canales válidos.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {

                    Log.e("TvFragment", "Error cargando canales", e)
                }
            }
        }
    }


    private var progreso = 0
    private val progresoHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progresoRunnable = object : Runnable {
        override fun run() {
            if (progreso < 95) {
                progreso += 1
                progressBar.progress = progreso
                progresoHandler.postDelayed(this, 100)
            }
        }
    }


 /*   private fun mostrarCargando() {
        progreso = 0
        loadingContainer.visibility = View.VISIBLE
        progressBar.progress = 0
        progresoHandler.post(progresoRunnable)
    }


    private fun ocultarCargando() {
        progresoHandler.removeCallbacks(progresoRunnable)

        CoroutineScope(Dispatchers.Main).launch {
            while (progreso < 100) {
                progreso += 5
                if (progreso > 100) progreso = 100
                progressBar.progress = progreso
                delay(10)
            }

            delay(100)
            loadingContainer.visibility = View.GONE
        }
    }*/



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

        chunkedCanales.forEachIndexed { index, chunk ->
            val listRowAdapter = ArrayObjectAdapter(cardPresenter).apply {
                addAll(0, chunk)
            }

            val header = if (index == 0) HeaderItem(0, "") else null
            channels.add(ListRow(header, listRowAdapter))
        }
    }
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = requireContext()
            val granted = AudioFocusHelper.requestAudioFocus(context)
            if (granted) {
                // Lógica si se obtiene el foco
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.abandonAudioFocus()
        }
    }

}