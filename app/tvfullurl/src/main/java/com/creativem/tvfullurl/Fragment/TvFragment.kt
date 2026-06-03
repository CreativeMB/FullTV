package com.creativem.tvfullurl.Fragment

import android.os.Bundle
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
// CAMBIO: Importamos Realtime Database en lugar de Firestore
import com.google.firebase.database.*

class TvFragment : Fragment() {

    // CAMBIO: Usamos DatabaseReference
    private lateinit var database: DatabaseReference
    private lateinit var titleEditText: EditText
    private lateinit var imageUrlEditText: EditText
    private lateinit var streamUrlEditText: EditText
    private lateinit var createButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()
    private var isEditing = false
    private var currentEditingMovieId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTvBinding.inflate(inflater, container, false)

        // CAMBIO: Inicializamos la referencia a la tabla "tv"
        database = FirebaseDatabase.getInstance().getReference("tv")

        titleEditText = binding.titleEditText
        imageUrlEditText = binding.imageUrlEditText
        streamUrlEditText = binding.streamUrlEditText
        createButton = binding.createButton
        recyclerView = binding.recyclerViewTV

        recyclerView.layoutManager = LinearLayoutManager(context)

        moviesAdapter = MoviesAdapter(
            movieList,
            onDeleteClick = { id -> deleteMovie(id) },
            onEditClick = { movie -> editMovie(movie) },
            onAssignClick = { movie ->(movie) }, // Se muestra el botón
            isEditable = true
        )

        recyclerView.adapter = moviesAdapter

        createButton.setOnClickListener {
            if (isEditing) {
                currentEditingMovieId?.let { movieId -> updateMovieInFirebase(movieId) }
            } else {
                uploadDataToFirebase()
            }
        }

        loadDataFromFirebase()

        return binding.root
    }

    private fun uploadDataToFirebase() {
        val title = titleEditText.text.toString().trim()
        val imageUrl = imageUrlEditText.text.toString().trim()
        val streamUrl = streamUrlEditText.text.toString().trim()

        if (title.isNotEmpty() && imageUrl.isNotEmpty() && streamUrl.isNotEmpty()) {
            // Generamos un ID único (push)
            val movieId = database.push().key ?: return

            val tvData = hashMapOf(
                "id" to movieId,
                "title" to title,
                "imageUrl" to imageUrl,
                "streamUrl" to streamUrl,
                "createdAt" to ServerValue.TIMESTAMP // Firebase gestiona el tiempo
            )

            // CAMBIO: .setValue en lugar de .add
            database.child(movieId).setValue(tvData)
                .addOnSuccessListener {
                    val newMovie = Movie(
                        id = movieId,
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
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Llena todos los campos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDataFromFirebase() {
        // CAMBIO: Usamos addValueEventListener o get() para Realtime
        database.get().addOnSuccessListener { snapshot ->
            movieList.clear()
            for (doc in snapshot.children) {
                // Aquí es donde ocurría el error. Mapeamos manualmente o con getValue
                val movie = Movie(
                    id = doc.key ?: "",
                    title = doc.child("title").value.toString(),
                    imageUrl = doc.child("imageUrl").value.toString(),
                    streamUrl = doc.child("streamUrl").value.toString()
                )
                movieList.add(movie)
            }
            moviesAdapter.notifyDataSetChanged()
        }.addOnFailureListener { e ->
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteMovie(movieId: String) {
        // CAMBIO: .removeValue() en lugar de .delete()
        database.child(movieId).removeValue()
            .addOnSuccessListener {
                val positionToRemove = movieList.indexOfFirst { it.id == movieId }
                if (positionToRemove != -1) {
                    movieList.removeAt(positionToRemove)
                    moviesAdapter.notifyItemRemoved(positionToRemove)
                }
                Toast.makeText(context, "Canal eliminado", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateMovieInFirebase(movieId: String) {
        val updatedTitle = titleEditText.text.toString().trim()
        val updatedImageUrl = imageUrlEditText.text.toString().trim()
        val updatedStreamUrl = streamUrlEditText.text.toString().trim()

        if (updatedTitle.isNotEmpty() && updatedImageUrl.isNotEmpty() && updatedStreamUrl.isNotEmpty()) {
            val updatedMovieData = hashMapOf<String, Any>(
                "title" to updatedTitle,
                "imageUrl" to updatedImageUrl,
                "streamUrl" to updatedStreamUrl
            )

            // CAMBIO: .updateChildren()
            database.child(movieId).updateChildren(updatedMovieData)
                .addOnSuccessListener {
                    val positionToUpdate = movieList.indexOfFirst { it.id == movieId }
                    if (positionToUpdate != -1) {
                        movieList[positionToUpdate] = movieList[positionToUpdate].clona(
                            title = updatedTitle,
                            imageUrl = updatedImageUrl,
                            streamUrl = updatedStreamUrl
                        )
                        moviesAdapter.notifyItemChanged(positionToUpdate)
                    }
                    resetEditingMode()
                    Toast.makeText(context, "Canal actualizado", Toast.LENGTH_SHORT).show()
                }
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

    private fun resetEditingMode() {
        isEditing = false
        currentEditingMovieId = null
        createButton.text = "Crear"
        clearFields()
    }

    private fun clearFields() {
        titleEditText.text?.clear()
        imageUrlEditText.text?.clear()
        streamUrlEditText.text?.clear()
    }

    // Mantengo tus onResume/onStop como los tenías
    override fun onResume() {
        super.onResume()
        clearFields()
        resetEditingMode()
    }
}