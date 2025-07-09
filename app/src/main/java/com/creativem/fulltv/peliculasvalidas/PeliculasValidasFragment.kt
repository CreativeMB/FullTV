package com.creativem.fulltv.peliculasvalidas

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.Validacioneslista
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.peliculas.CardPresenter  // Cambio aquí
import com.creativem.fulltv.peliculas.PlayerPeliculas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PeliculasValidasFragment : RowsSupportFragment() {
    private val channels = ArrayObjectAdapter(ListRowPresenter())
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: View
    private var backgroundImageView: ImageView? = null
    private lateinit var loadingContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        adapter = channels
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Referencias a los elementos de carga
        progressBar = requireActivity().findViewById(R.id.progressBar)
        loadingText = requireActivity().findViewById(R.id.loadingText)
        backgroundImageView = requireActivity().findViewById(R.id.backgroundImageView)
        loadingContainer = requireActivity().findViewById(R.id.layoutCargando)

        setOnItemViewClickedListener(ItemViewClickedListener())
        setOnItemViewSelectedListener(ItemViewSelectedListener())
        loadMovies()
    }

    private fun loadMovies() {
        mostrarCargando() // Mostrar la barra de progreso

        CoroutineScope(Dispatchers.Main).launch {
            // 🔄 Esperar a que la carga en segundo plano se complete
            Validacioneslista.esperarCarga()

            // ✅ Obtener las películas válidas ya cargadas
            val peliculasOrdenadasValidas = Validacioneslista.obtenerPeliculasValidas()

            ocultarCargando() // Ocultar la barra de progreso

            if (peliculasOrdenadasValidas.isNotEmpty()) {
                agregarALista(peliculasOrdenadasValidas, "")
            } else {
                Log.e("PeliculasValidasFragment", "No hay películas válidas.")
            }
        }
    }

    private var progreso = 0
    private val progresoHandler = Handler(Looper.getMainLooper())
    private val progresoRunnable = object : Runnable {
        override fun run() {
            if (progreso < 95) { // Simula solo hasta el 95%
                progreso += 1     // Avanza más lento
                progressBar.progress = progreso
                progresoHandler.postDelayed(this, 100) // Cada 100 ms
            }
        }
    }

    private fun mostrarCargando() {
        progreso = 0
        progressBar.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
        progressBar.progress = 0
        progresoHandler.post(progresoRunnable) // Inicia simulación
    }


    private fun ocultarCargando() {
        progresoHandler.removeCallbacks(progresoRunnable)

        CoroutineScope(Dispatchers.Main).launch {
            // Llenar hasta 100% más rápido (en ~100ms)
            while (progreso < 100) {
                progreso += 5
                if (progreso > 100) progreso = 100
                progressBar.progress = progreso
                delay(10) // velocidad rápida
            }

            delay(100) // Pequeña pausa para que se vea completa
            loadingContainer.visibility = View.GONE

        }
    }



    private fun calcularElementosPorFila(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        val anchoPantalla = displayMetrics.widthPixels
        val anchoTarjeta = 240
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

                val intent = Intent(context, PlayerPeliculas::class.java).apply {
                    putExtra("EXTRA_STREAM_URL", item.streamUrl)
                    putExtra("EXTRA_MOVIE_TITLE", item.title)
                    putExtra("EXTRA_MOVIE_YEAR", item.year)
                    putExtra("EXTRA_MOVIE_IMAGE_URL", item.imageUrl)
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
    private fun cargarImagenDeFondo(url: String?) {
        if (url.isNullOrEmpty()) return

        backgroundImageView?.let { imageView ->
            Glide.with(requireContext())
                .load(url)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(1000))
                .error(R.drawable.icono)
                .into(imageView)

            imageView.alpha = 0.6f
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        }

    }
    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?
        ) {
            if (item is Movie) {
                cargarImagenDeFondo(item.imageUrl)
            }
        }
    }


}
