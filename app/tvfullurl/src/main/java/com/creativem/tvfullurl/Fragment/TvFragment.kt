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
    private var movieList: MutableList<Movie> = mutableListOf() // Lista de películas

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentTvBinding.inflate(inflater, container, false)

        firestore = FirebaseFirestore.getInstance()

        // Inicializar las vistas
        titleEditText = binding.titleEditText
        imageUrlEditText = binding.imageUrlEditText
        streamUrlEditText = binding.streamUrlEditText
        createButton = binding.createButton
        recyclerView = binding.recyclerViewTV

        // Configurar el RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Crear el adaptador y pasar las acciones de eliminación y edición
        moviesAdapter = MoviesAdapter(movieList,
            onDeleteClick = { movieId -> deleteMovie(movieId) },
            onEditClick = { movie -> editMovie(movie) },
            isEditable = true // Ajustar si deseas habilitar la edición
        )

        recyclerView.adapter = moviesAdapter

        // Establecer el clic del botón para subir los datos a Firebase
        createButton.setOnClickListener {
            uploadDataToFirebase()
        }

        // Cargar los datos desde Firebase
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
                    val documentId = documentReference.id

                    val newMovie = Movie(documentId, title, imageUrl, streamUrl)

                    // Agregar el nuevo item a la lista y notificar al adaptador
                    movieList.add(newMovie)
                    activity?.runOnUiThread {
                        moviesAdapter.notifyItemInserted(movieList.size - 1)  // Notificar la inserción
                    }
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
                if (!documents.isEmpty) {
                    // Mapeamos los documentos a la lista de Movie incluyendo todos los campos
                    val tvList = documents.map { doc ->
                        val documentId = doc.id
                        val title = doc.getString("title") ?: "Título no disponible"
                        val imageUrl = doc.getString("imageUrl") ?: "URL de imagen no disponible"
                        val streamUrl = doc.getString("streamUrl") ?: "URL de stream no disponible"

                        Log.d("loadDataFromFirebase", "Title: $title, ImageUrl: $imageUrl, StreamUrl: $streamUrl")

                        Movie(id = documentId, title = title, imageUrl = imageUrl, streamUrl = streamUrl)

                    }

                    // Verificar si ya existe algún dato en la lista
                    val currentSize = movieList.size

                    // Agregar los nuevos items a la lista existente
                    movieList.addAll(tvList)

                    // Notificar al adaptador de que se han agregado nuevos items
                    moviesAdapter.notifyItemRangeInserted(currentSize, tvList.size)
                } else {
                    Toast.makeText(context, "No hay datos disponibles", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al cargar los datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        clearFields() // Limpiar los campos de texto cuando el fragmento se destruye
    }

    private fun clearFields() {
        titleEditText.text.clear()
        imageUrlEditText.text.clear()
        streamUrlEditText.text.clear()
    }

    private fun deleteMovie(movieId: String) {
        Log.d("deleteMovie", "ID del documento a eliminar: $movieId")

        if (movieId.isEmpty()) {
            Toast.makeText(requireContext(), "ID de documento no válido", Toast.LENGTH_SHORT).show()
            return
        }

        val pedidoRef = firestore.collection("tv").document(movieId)

        pedidoRef.delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Película eliminada correctamente", Toast.LENGTH_SHORT).show()

                val positionToRemove = movieList.indexOfFirst { it.id == movieId }

                if (positionToRemove != -1) {
                    movieList.removeAt(positionToRemove)
                    moviesAdapter.notifyItemRemoved(positionToRemove)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al eliminar la película: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun editMovie(movie: Movie) {
        // Llenar los campos con los datos actuales de la película
        titleEditText.setText(movie.title)
        imageUrlEditText.setText(movie.imageUrl)
        streamUrlEditText.setText(movie.streamUrl)

        // Cambiar el texto del botón de creación a "Guardar cambios" para indicar que estamos en modo de edición
        createButton.text = "Guardar cambios"

        // Establecer un OnClickListener para guardar los cambios cuando el usuario presiona el botón
        createButton.setOnClickListener {
            // Llamar al método para actualizar los datos en Firebase
            updateMovieInFirebase(movie)
        }
    }
    private fun updateMovieInFirebase(movie: Movie) {
        val updatedTitle = titleEditText.text.toString().trim()
        val updatedImageUrl = imageUrlEditText.text.toString().trim()
        val updatedStreamUrl = streamUrlEditText.text.toString().trim()

        if (updatedTitle.isNotEmpty() && updatedImageUrl.isNotEmpty() && updatedStreamUrl.isNotEmpty()) {
            val updatedMovieData = hashMapOf(
                "title" to updatedTitle,
                "imageUrl" to updatedImageUrl,
                "streamUrl" to updatedStreamUrl,
                "createdAt" to com.google.firebase.Timestamp.now() // Mantener la fecha de creación actualizada
            )

            firestore.collection("tv").document(movie.id)
                .set(updatedMovieData)
                .addOnSuccessListener {
                    Toast.makeText(context, "Película actualizada correctamente", Toast.LENGTH_SHORT).show()

                    // Actualizar los datos de la película en la lista local
                    val positionToUpdate = movieList.indexOfFirst { it.id == movie.id }
                    if (positionToUpdate != -1) {
                        movieList[positionToUpdate] = movie.copy(
                            title = updatedTitle,
                            imageUrl = updatedImageUrl,
                            streamUrl = updatedStreamUrl
                        )
                        moviesAdapter.notifyItemChanged(positionToUpdate)
                    }

                    // Limpiar los campos y cambiar el botón de nuevo a "Crear"
                    clearFields()
                    createButton.text = "Crear"
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error al actualizar la película: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
        }
    }
}
