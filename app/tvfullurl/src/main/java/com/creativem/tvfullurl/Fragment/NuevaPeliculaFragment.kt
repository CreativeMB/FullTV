package com.creativem.tvfullurl.Fragment

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
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.databinding.FragmentNuevaEditarBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class NuevaPeliculaFragment : Fragment() {

    private lateinit var binding: FragmentNuevaEditarBinding

    // NUEVA RUTA: Referencia a Realtime Database
    private val databaseRef = FirebaseDatabase.getInstance().reference.child("movies")

    private lateinit var editTexts: List<EditText>
    private var movieId: String? = null

    private var title: String = ""
    private var castv: Int = 0
    private var imageUrl: String = ""
    private var streamUrl: String = ""
    private var trailerUrl: String = ""
    private var originalTitle: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNuevaEditarBinding.inflate(inflater, container, false)
        movieId = arguments?.getString("movieId")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editTexts = listOf(
            binding.titleEditText,
            binding.originalTitleEditText,
            binding.castvEditText,
            binding.imageUrlEditText,
            binding.streamUrlEditText,
            binding.validEditText,
            binding.trailerUrlEditText
        )

        movieId?.let {
            loadMovieData(it)
        }
        listenerimagen()

        binding.uploadText.setOnClickListener {
            if (movieId == null) {
                saveNewMovie()
            } else {
                editarMovie(movieId!!)
            }
        }
    }

    // --- CARGAR DATOS (Nueva Ruta) ---
    private fun loadMovieData(movieId: String) {
        databaseRef.child(movieId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val movie = snapshot.getValue(Movie::class.java)
                    movie?.let {
                        binding.titleEditText.setText(it.title)
                        binding.originalTitleEditText.setText(it.originalTitle)
                        binding.castvEditText.setText(it.castv.toString())
                        binding.imageUrlEditText.setText(it.imageUrl)
                        binding.streamUrlEditText.setText(it.streamUrl)
                        binding.trailerUrlEditText.setText(it.trailerUrl)
                        binding.validEditText.setText(it.countdownMinutes.toString())
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al cargar los datos", Toast.LENGTH_SHORT).show()
            }
    }

    // --- GUARDAR NUEVA PELÍCULA (Nueva Ruta) ---
    private fun saveNewMovie() {
        if (!validarCampos()) return

        // Generar ID automático en Realtime Database usando push()
        val movieKey = databaseRef.push().key ?: return

        // ✅ USAMOS .apply PARA RELLENAR LOS DATOS
        // Esto funciona con la nueva clase Movie y evita errores de constructor
        val newMovie = Movie().apply {
            id = movieKey
            title = binding.titleEditText.text.toString()
            originalTitle = binding.originalTitleEditText.text.toString()
            castv = binding.castvEditText.text.toString().toIntOrNull() ?: 0
            imageUrl = binding.imageUrlEditText.text.toString()
            streamUrl = binding.streamUrlEditText.text.toString()
            trailerUrl = binding.trailerUrlEditText.text.toString()
            createdAt = System.currentTimeMillis() // Long (milisegundos)
            countdownMinutes = binding.validEditText.text.toString().toIntOrNull() ?: 0
        }

        databaseRef.child(movieKey).setValue(newMovie)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Película guardada en la nueva ruta", Toast.LENGTH_SHORT).show()
                clearFields()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al guardar la película", Toast.LENGTH_SHORT).show()
            }
    }

    // --- EDITAR PELÍCULA (Nueva Ruta) ---
    private fun editarMovie(movieId: String) {
        if (!validarCampos()) return

        val updates = Movie().apply {
            this.id = movieId
            this.title = title
            this.originalTitle = originalTitle
            this.castv = castv
            this.imageUrl = imageUrl
            this.streamUrl = streamUrl
            this.trailerUrl = trailerUrl
            this.createdAt = System.currentTimeMillis()
            this.countdownMinutes = binding.validEditText.text.toString().toIntOrNull() ?: 0
        }

        databaseRef.child(movieId).setValue(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Película actualizada correctamente", Toast.LENGTH_LONG).show()
                clearFields()
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    // --- LÓGICA DE VALIDACIÓN E IMAGEN (Se mantiene idéntica) ---
    private fun listenerimagen() {
        binding.imageUrlEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val imageUrl = s.toString().trim()
                if (URLUtil.isValidUrl(imageUrl)) {
                    Glide.with(this@NuevaPeliculaFragment).load(imageUrl).into(binding.previewImageView)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun validarCampos(): Boolean {
        title = binding.titleEditText.text.toString().trim()
        castv = binding.castvEditText.text.toString().toIntOrNull() ?: 0
        imageUrl = binding.imageUrlEditText.text.toString().trim()
        streamUrl = binding.streamUrlEditText.text.toString().trim()
        trailerUrl = binding.trailerUrlEditText.text.toString().trim()
        originalTitle = binding.originalTitleEditText.text.toString().trim()

        if (title.isEmpty() || castv <= 0 || imageUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Todos los campos son obligatorios", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun clearFields() {
        binding.titleEditText.text.clear()
        binding.originalTitleEditText.text.clear()
        binding.castvEditText.text.clear()
        binding.imageUrlEditText.text.clear()
        binding.streamUrlEditText.text.clear()
        binding.trailerUrlEditText.text.clear()
        binding.previewImageView.setImageResource(R.drawable.icono)
        binding.validEditText.text.clear()
    }
}