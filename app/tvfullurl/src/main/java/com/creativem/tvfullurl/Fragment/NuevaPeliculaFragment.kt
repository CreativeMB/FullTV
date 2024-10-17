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
import com.creativem.tvfullurl.databinding.FragmentNuevaPeliculaBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore


class NuevaPeliculaFragment : Fragment() {

    private lateinit var binding: FragmentNuevaPeliculaBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var editTexts: List<EditText>
    private var movieId: String? = null

    // Define variables for movie details at the class level
    private var title: String = ""
    private var year: String = ""
    private var imageUrl: String = ""
    private var streamUrl: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNuevaPeliculaBinding.inflate(inflater, container, false)
        movieId = arguments?.getString("movieId")
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editTexts = listOf(
            binding.titleEditText,
            binding.yearEditText,
            binding.imageUrlEditText,
            binding.streamUrlEditText
        )
        // Si estamos editando (es decir, si tenemos un movieId), cargar los datos de la película
        movieId?.let {
            loadMovieData(it)
        }
        listenerimagen()
        // Llamar a las funciones necesarias
//        binding.uploadText.setOnClickListener {  } // Llama a uploadMovie cuando se hace clic
        binding.url.setOnClickListener { openWebPage("https://castr.com/hlsplayer/") } // Abre la página web

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
                        binding.yearEditText.setText(it.year)
                        binding.imageUrlEditText.setText(it.imageUrl)
                        binding.streamUrlEditText.setText(it.streamUrl)
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

        val newMovie = Movie(
            title = binding.titleEditText.text.toString(),
            year = binding.yearEditText.text.toString(),
            imageUrl = binding.imageUrlEditText.text.toString(),
            streamUrl = binding.streamUrlEditText.text.toString(),
            createdAt = Timestamp.now()
        )

        db.collection("movies").add(newMovie)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Película guardada", Toast.LENGTH_SHORT).show()
                clearFields()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error al guardar la película", Toast.LENGTH_SHORT).show()
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

    private fun openWebPage(url: String) {
        Log.d("openWebPage", "Intentando abrir la URL: $url")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }


    private fun validarCampos(): Boolean {
        // Actualiza las variables globales con los valores de los campos
        title = binding.titleEditText.text.toString().trim()
        year = binding.yearEditText.text.toString().trim()
        imageUrl = binding.imageUrlEditText.text.toString().trim()
        streamUrl = binding.streamUrlEditText.text.toString().trim()

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

        // Crear el mapa con los datos de la película actualizados
        val movie: MutableMap<String, Any> = mutableMapOf(
            "title" to title,  // Asegúrate de que estas variables ya tienen los valores correctos
            "year" to year,
            "imageUrl" to imageUrl,
            "streamUrl" to streamUrl,
            "createdAt" to Timestamp.now()  // Marcar como actualizado
        )

        // Usar update() para modificar el documento existente
        db.collection("movies")
            .document(movieId)
            .update(movie)
            .addOnSuccessListener {
                Log.d("movies", "Película actualizada correctamente con ID: $movieId")
                Toast.makeText(
                    requireContext(),
                    "Película actualizada correctamente",
                    Toast.LENGTH_LONG
                ).show()
// Navegar de vuelta a EditarPeliculaFragment
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                Log.e("movies", "Error al actualizar la película: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Error al actualizar la película: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun clearFields() {
        binding.titleEditText.text.clear()
        binding.yearEditText.text.clear()
        binding.imageUrlEditText.text.clear()
        binding.streamUrlEditText.text.clear()
        binding.previewImageView.setImageResource(R.drawable.icono)
    }

    override fun onPause() {
        super.onPause()
        clearFields() // Limpia los campos al ocultar el Fragment
    }


}