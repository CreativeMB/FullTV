package com.creativem.tvfullurl.Fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.BrowserActivity
import com.creativem.tvfullurl.MovieResponse
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.SugerenciaAdapter
import com.creativem.tvfullurl.TMDbApiService
import com.creativem.tvfullurl.TmdbMovie
import com.creativem.tvfullurl.databinding.FragmentNuevaEditarBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NuevaPeliculaFragment : Fragment() {
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"
    private var isAutoFilling = false
    private lateinit var binding: FragmentNuevaEditarBinding
    private val databaseRef = FirebaseDatabase.getInstance().reference.child("movies")

    private lateinit var sugerenciaAdapter: SugerenciaAdapter
    private var searchJob: Job? = null
    private var movieId: String? = null

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
        movieId?.let { loadMovieData(it) }
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
        binding.titleEditText.setText(movie.title)
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
            }
        }
    }

    private fun saveOrUpdateMovie() {
        val title = binding.titleEditText.text.toString().trim()
        if (title.isEmpty()) return

        val id = movieId ?: databaseRef.push().key ?: return
        val movie = Movie().apply {
            this.id = id
            this.title = title
            this.originalTitle = binding.originalTitleEditText.text.toString().trim()
            this.castv = binding.castvEditText.text.toString().toIntOrNull() ?: 0
            this.imageUrl = binding.imageUrlEditText.text.toString().trim()
            this.streamUrl = binding.streamUrlEditText.text.toString().trim()
            this.trailerUrl = binding.trailerUrlEditText.text.toString().trim()
            this.countdownMinutes = binding.validEditText.text.toString().toIntOrNull() ?: 0
            this.createdAt = System.currentTimeMillis()
        }

        databaseRef.child(id).setValue(movie).addOnSuccessListener {
            Toast.makeText(requireContext(), "Éxito", Toast.LENGTH_SHORT).show()
            if (movieId != null) findNavController().navigateUp() else clearFields()
        }
    }

    private fun clearFields() {
        binding.titleEditText.text.clear()
        binding.originalTitleEditText.text.clear()
        binding.imageUrlEditText.text.clear()
        binding.streamUrlEditText.text.clear()
        binding.previewImageView.setImageResource(R.drawable.icono)
    }
}