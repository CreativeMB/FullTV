package com.creativem.tvfullurl.Fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore


class NuevaPeliculaFragment : Fragment() {

    private lateinit var binding: FragmentNuevaEditarBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var editTexts: List<EditText>
    private var movieId: String? = null

    // Define variables for movie details at the class level
    private var title: String = ""
    private var year: String = ""
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
            binding.yearEditText,
            binding.imageUrlEditText,
            binding.streamUrlEditText,
            binding.validEditText,
            binding.trailerUrlEditText
        )
        // Si estamos editando (es decir, si tenemos un movieId), cargar los datos de la película
        movieId?.let {
            loadMovieData(it)
        }
        listenerimagen()


        binding.uploadText.setOnClickListener {
            if (movieId == null) {
                // Guardar una nueva película
                saveNewMovie()
            } else {
                // Actualizar la película existente
                editarMovie(movieId!!)
            }
        }

    }


    private fun loadMovieData(movieId: String) {
        db.collection("movies").document(movieId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val movie = document.toObject(Movie::class.java)
                    movie?.let {
                        // Cargar los datos en los campos
                        binding.titleEditText.setText(it.title)
                        binding.originalTitleEditText.setText(it.originalTitle)
                        binding.yearEditText.setText(it.year)
                        binding.imageUrlEditText.setText(it.imageUrl)
                        binding.streamUrlEditText.setText(it.streamUrl)
                        binding.trailerUrlEditText.setText(it.trailerUrl)
                        binding.validEditText.setText(it.countdownMinutes.toString())
                        // Puedes cargar otros campos aquí
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al cargar los datos", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun saveNewMovie() {
        if (!validarCampos()) return

        val db = FirebaseFirestore.getInstance()
        val collection = db.collection("movies")

        // Generar ID automático con document()
        val documentRef = collection.document()
        val generatedId = documentRef.id

        val newMovie = Movie(
            id = generatedId, // Guardamos el ID generado en el campo "id"
            title = binding.titleEditText.text.toString(),
            originalTitle = binding.originalTitleEditText.text.toString(),
            year = binding.yearEditText.text.toString(),
            imageUrl = binding.imageUrlEditText.text.toString(),
            streamUrl = binding.streamUrlEditText.text.toString(),
            trailerUrl = binding.trailerUrlEditText.text.toString(),
            createdAt = Timestamp.now(),
            countdownMinutes = binding.validEditText.text.toString().toIntOrNull() ?: 0
        )

        // Guardar usando set() para conservar el ID generado
        documentRef.set(newMovie)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Película guardada", Toast.LENGTH_SHORT).show()
                clearFields()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al guardar la película", Toast.LENGTH_SHORT).show()
                clearFields()
            }
    }


    private fun listenerimagen() {
        binding.imageUrlEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val imageUrl = s.toString().trim()
                if (URLUtil.isValidUrl(imageUrl)) {
                    Glide.with(this@NuevaPeliculaFragment)
                        .load(imageUrl)
                        .into(binding.previewImageView)
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })
    }

    private fun validarCampos(): Boolean {
        // Actualiza las variables globales con los valores de los campos
        title = binding.titleEditText.text.toString().trim()
        year = binding.yearEditText.text.toString().trim()
        imageUrl = binding.imageUrlEditText.text.toString().trim()
        streamUrl = binding.streamUrlEditText.text.toString().trim()
        trailerUrl = binding.trailerUrlEditText.text.toString().trim()
        originalTitle = binding.originalTitleEditText.text.toString().trim()

        // Verificar si los campos están completos
        if (title.isEmpty() || year.isEmpty() || imageUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Todos los campos son obligatorios", Toast.LENGTH_LONG)
                .show()
            return false
        }

        // Validar las URLs
        if (!URLUtil.isValidUrl(imageUrl)) {
            Toast.makeText(requireContext(), "URL de imagen inválida", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun editarMovie(movieId: String) {
        if (!validarCampos()) return

        val db = FirebaseFirestore.getInstance()
        val movieRef = db.collection("movies").document(movieId)

        movieRef.get().addOnSuccessListener { snapshot ->
            val idActual = snapshot.getString("id")

            val movie: MutableMap<String, Any> = mutableMapOf(
                "title" to title,
                "originalTitle" to originalTitle,
                "year" to year,
                "imageUrl" to imageUrl,
                "streamUrl" to streamUrl,
                "trailerUrl" to trailerUrl,
                "createdAt" to Timestamp.now(),
                "countdownMinutes" to (binding.validEditText.text.toString().toIntOrNull() ?: 0)
            )

            // Solo agregamos el campo "id" si no existe o está vacío
            if (idActual.isNullOrEmpty()) {
                movie["id"] = movieId
            }

            movieRef.update(movie)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Película actualizada correctamente", Toast.LENGTH_LONG).show()
                    clearFields()
                    findNavController().navigateUp()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
                }

        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error al obtener la película: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun clearFields() {
        binding.titleEditText.text.clear()
        binding.originalTitleEditText.text.clear()
        binding.yearEditText.text.clear()
        binding.imageUrlEditText.text.clear()
        binding.streamUrlEditText.text.clear()
        binding.trailerUrlEditText.text.clear()
        binding.previewImageView.setImageResource(R.drawable.icono)
        binding.validEditText.text.clear()
    }

}