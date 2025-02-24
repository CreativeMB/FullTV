package com.creativem.tvfullurl.Fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.adapter.MoviesAdapter
import com.creativem.tvfullurl.databinding.FragmentTvBinding
import com.google.firebase.firestore.FirebaseFirestore

class TvFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var titleEditText: EditText
    private lateinit var imageUrlEditText: EditText
    private lateinit var streamUrlEditText: EditText
    private lateinit var createButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()
    private var isEditing = false
    private var currentEditingMovieId: String? = null // Para rastrear la edición

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTvBinding.inflate(inflater, container, false)

        firestore = FirebaseFirestore.getInstance()

        // Inicializar vistas
        titleEditText = binding.titleEditText
        imageUrlEditText = binding.imageUrlEditText
        streamUrlEditText = binding.streamUrlEditText
        createButton = binding.createButton
        recyclerView = binding.recyclerViewTV

        // Configurar RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Crear adaptador con funciones de edición y eliminación
        moviesAdapter = MoviesAdapter(
            movieList,
            onDeleteClick = { movieId -> deleteMovie(movieId) },
            onEditClick = { movie -> editMovie(movie) },
            isEditable = true
        )

        recyclerView.adapter = moviesAdapter

        // Botón de agregar/editar
        createButton.setOnClickListener {
            if (isEditing) {
                currentEditingMovieId?.let { movieId -> updateMovieInFirebase(movieId) }
            } else {
                uploadDataToFirebase()
            }
        }

        // Cargar datos de Firestore
        loadDataFromFirebase()

        return binding.root
    }

    private fun uploadDataToFirebase() {
        val title = titleEditText.text.toString().trim()
        val imageUrl = imageUrlEditText.text.toString().trim()
        val streamUrl = streamUrlEditText.text.toString().trim()

        if (title.isNotEmpty() && imageUrl.isNotEmpty() && streamUrl.isNotEmpty()) {
            val tvData = hashMapOf(
                "title" to title,
                "imageUrl" to imageUrl,
                "streamUrl" to streamUrl,
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            firestore.collection("tv")
                .add(tvData)
                .addOnSuccessListener { documentReference ->
                    val newMovie = Movie(
                        id = documentReference.id,
                        title = title,
                        imageUrl = imageUrl,
                        streamUrl = streamUrl
                    )
                    Toast.makeText(context, "Nuevo Canal Cargado", Toast.LENGTH_SHORT).show()
                    movieList.add(newMovie)
                    moviesAdapter.notifyItemInserted(movieList.size - 1)

                    clearFields()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error al subir los datos: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDataFromFirebase() {
        firestore.collection("tv")
            .get()
            .addOnSuccessListener { documents ->
                movieList.clear() // Limpiar lista antes de agregar nuevos datos
                for (doc in documents) {
                    val movie = Movie(
                        id = doc.id,
                        title = doc.getString("title") ?: "Título no disponible",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        streamUrl = doc.getString("streamUrl") ?: ""
                    )
                    movieList.add(movie)
                }

                moviesAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al cargar los datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteMovie(movieId: String) {
        firestore.collection("tv").document(movieId)
            .delete()
            .addOnSuccessListener {
                val positionToRemove = movieList.indexOfFirst { it.id == movieId }
                if (positionToRemove != -1) {
                    movieList.removeAt(positionToRemove)
                    moviesAdapter.notifyItemRemoved(positionToRemove)
                }
                Toast.makeText(context, "Canal eliminado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun editMovie(movie: Movie) {
        titleEditText.setText(movie.title)
        imageUrlEditText.setText(movie.imageUrl)
        streamUrlEditText.setText(movie.streamUrl)

        createButton.text = "Guardar cambios"
        isEditing = true
        currentEditingMovieId = movie.id
    }

    private fun updateMovieInFirebase(movieId: String) {
        val updatedTitle = titleEditText.text.toString().trim()
        val updatedImageUrl = imageUrlEditText.text.toString().trim()
        val updatedStreamUrl = streamUrlEditText.text.toString().trim()

        if (updatedTitle.isNotEmpty() && updatedImageUrl.isNotEmpty() && updatedStreamUrl.isNotEmpty()) {
            val updatedMovieData = hashMapOf(
                "title" to updatedTitle,
                "imageUrl" to updatedImageUrl,
                "streamUrl" to updatedStreamUrl
            )

            firestore.collection("tv").document(movieId)
                .update(updatedMovieData as Map<String, Any>)
                .addOnSuccessListener {
                    val positionToUpdate = movieList.indexOfFirst { it.id == movieId }
                    if (positionToUpdate != -1) {
                        movieList[positionToUpdate] = movieList[positionToUpdate].copy(
                            title = updatedTitle,
                            imageUrl = updatedImageUrl,
                            streamUrl = updatedStreamUrl
                        )
                        moviesAdapter.notifyItemChanged(positionToUpdate)
                    }
                    resetEditingMode()
                    Toast.makeText(context, "Canal actualizado", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error al actualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Llena todos los campos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetEditingMode() {
        isEditing = false
        currentEditingMovieId = null
        createButton.text = "Crear"
        clearFields()
    }

    private fun clearFields() {
        titleEditText.text.clear()
        imageUrlEditText.text.clear()
        streamUrlEditText.text.clear()
    }

    override fun onResume() {
        super.onResume()
        clearFields()
        resetEditingMode()
    }
    override fun onStop() {
        super.onStop()
        clearFields()
        resetEditingMode()
    }

}
