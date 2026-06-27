package com.creativem.tvfullurl.Fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.BrowserActivity
import com.creativem.tvfullurl.MovieResponse
import com.creativem.tvfullurl.NotificationMonitorService
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.SugerenciaAdapter
import com.creativem.tvfullurl.TMDbApiService
import com.creativem.tvfullurl.TmdbMovie
import com.creativem.tvfullurl.databinding.FragmentNuevaEditarBinding
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NuevaPeliculaFragment : Fragment() {
    private var originalCreatedAt: Long = 0L
    private var tieneSolicitudesPendientes: Boolean = false

    private val apiKey = "678193d2c735c6f37840cee035f4d69a"
    private var isAutoFilling = false
    private lateinit var binding: FragmentNuevaEditarBinding
    private val databaseRef = FirebaseDatabase.getInstance().reference.child("movies")

    private lateinit var sugerenciaAdapter: SugerenciaAdapter
    private var searchJob: Job? = null
    private var movieId: String? = null

    // Variable para registrar temporalmente la fecha de la película seleccionada
    private var selectedYear: String = ""

    private val apiService: TMDbApiService by lazy {
        Retrofit.Builder().baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(TMDbApiService::class.java)
    }

    private val startBrowserForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val url = result.data?.getStringExtra("URL_CAPTURADA")
            binding.streamUrlEditText.setText(url)
        }
    }



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNuevaEditarBinding.inflate(inflater, container, false)
        movieId = arguments?.getString("movieId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupRecyclerView()
        setupListeners()

        if (movieId != null) {
            loadMovieData(movieId!!)
        } else {
            binding.validEditText.setText("0")   // Contador por defecto
            binding.castvEditText.setText("10")   // Castv por defecto
        }
    }

    private fun setupRecyclerView() {
        sugerenciaAdapter = SugerenciaAdapter(emptyList()) { peli -> rellenarCampos(peli) }
        binding.rvSugerencias.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSugerencias.adapter = sugerenciaAdapter
    }

    private fun setupListeners() {
        binding.uploadText.setOnClickListener { saveOrUpdateMovie() }

        binding.browser.setOnClickListener {
            val intent = Intent(requireContext(), BrowserActivity::class.java)
            startBrowserForResult.launch(intent)
        }

        binding.originalTitleEditText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isAutoFilling) { isAutoFilling = false; return }
                val texto = s.toString().trim()
                if (texto.length > 2) iniciarBusqueda(texto) else binding.rvSugerencias.visibility = View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun iniciarBusqueda(query: String) {
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.Main).launch {
            delay(600)
            apiService.searchMovie(apiKey, "es-MX", query).enqueue(object : Callback<MovieResponse> {
                override fun onResponse(call: Call<MovieResponse>, response: Response<MovieResponse>) {
                    val lista = response.body()?.results ?: emptyList()
                    sugerenciaAdapter.updateData(lista.take(5))
                    binding.rvSugerencias.visibility = if (lista.isNotEmpty()) View.VISIBLE else View.GONE
                }
                override fun onFailure(call: Call<MovieResponse>, t: Throwable) {}
            })
        }
    }

    private fun rellenarCampos(movie: TmdbMovie) {
        isAutoFilling = true
        binding.originalTitleEditText.setText(movie.original_title)

        val fechaOriginal = movie.release_date ?: ""
        selectedYear = fechaOriginal

        val tituloFormateado = if (fechaOriginal.isNotEmpty()) {
            "${movie.title} ($fechaOriginal)"
        } else {
            movie.title
        }

        binding.titleEditText.setText(tituloFormateado)

        val url = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
        binding.imageUrlEditText.setText(url)
        binding.rvSugerencias.visibility = View.GONE
        Glide.with(this).load(url).into(binding.previewImageView)
    }

    private fun loadMovieData(id: String) {
        databaseRef.child(id).get().addOnSuccessListener { snapshot ->
            val m = snapshot.getValue(Movie::class.java)
            m?.let {
                binding.titleEditText.setText(it.title)
                binding.originalTitleEditText.setText(it.originalTitle)
                binding.castvEditText.setText(it.castv.toString())
                binding.imageUrlEditText.setText(it.imageUrl)
                binding.streamUrlEditText.setText(it.streamUrl)
                binding.trailerUrlEditText.setText(it.trailerUrl)
                binding.validEditText.setText(it.countdownMinutes.toString())
                Glide.with(this).load(it.imageUrl).into(binding.previewImageView)

                selectedYear = it.year
                originalCreatedAt = it.createdAt

                val solicitudesNode = snapshot.child("solicitudes")
                tieneSolicitudesPendientes = solicitudesNode.exists() && solicitudesNode.hasChildren()
            }
        }
    }

    private fun saveOrUpdateMovie() {
        val title = binding.titleEditText.text.toString().trim()
        if (title.isEmpty()) return

        val id = movieId ?: databaseRef.push().key ?: return

        if (movieId != null) {
            // 🟢 SOLUCIÓN: Al editar, actualizamos "createdAt" al tiempo actual de forma incondicional.
            // Esto garantiza que aparezca siempre como recién creado/colocado en el catálogo de los clientes.
            val camposEditados = mapOf<String, Any>(
                "title" to title,
                "originalTitle" to binding.originalTitleEditText.text.toString().trim(),
                "castv" to (binding.castvEditText.text.toString().toIntOrNull() ?: 10),
                "imageUrl" to binding.imageUrlEditText.text.toString().trim(),
                "streamUrl" to binding.streamUrlEditText.text.toString().trim(),
                "trailerUrl" to binding.trailerUrlEditText.text.toString().trim(),
                "countdownMinutes" to (binding.validEditText.text.toString().toIntOrNull() ?: 0),
                "year" to selectedYear,
                "createdAt" to System.currentTimeMillis() // Fecha actualizada directamente al editar
            )

            databaseRef.child(id).updateChildren(camposEditados).addOnSuccessListener {
                Toast.makeText(requireContext(), "Éxito al actualizar", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al actualizar: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        } else {
            // Creación por primera vez
            val movie = Movie().apply {
                this.id = id
                this.title = title
                this.originalTitle = binding.originalTitleEditText.text.toString().trim()
                this.castv = binding.castvEditText.text.toString().toIntOrNull() ?: 10
                this.imageUrl = binding.imageUrlEditText.text.toString().trim()
                this.streamUrl = binding.streamUrlEditText.text.toString().trim()
                this.trailerUrl = binding.trailerUrlEditText.text.toString().trim()
                this.countdownMinutes = binding.validEditText.text.toString().toIntOrNull() ?: 0
                this.createdAt = System.currentTimeMillis()
                this.year = selectedYear
            }

            databaseRef.child(id).setValue(movie).addOnSuccessListener {
                Toast.makeText(requireContext(), "Éxito al crear", Toast.LENGTH_SHORT).show()
                clearFields()
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al crear: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearFields() {
        binding.titleEditText.text.clear()
        binding.originalTitleEditText.text.clear()
        binding.imageUrlEditText.text.clear()
        binding.streamUrlEditText.text.clear()
        binding.previewImageView.setImageResource(R.drawable.icono)

        binding.validEditText.setText("0")
        binding.castvEditText.setText("10")

        selectedYear = ""
        originalCreatedAt = 0L
        tieneSolicitudesPendientes = false
    }
}