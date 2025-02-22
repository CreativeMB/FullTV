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
import com.creativem.tvfullurl.adapter.PedidosAdapter
import com.creativem.tvfullurl.databinding.FragmentTvBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class TvFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var titleEditText: EditText
    private lateinit var imageUrlEditText: EditText
    private lateinit var streamUrlEditText: EditText
    private lateinit var createButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var pedidosAdapter: PedidosAdapter
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

        // Crear el adaptador y pasar la acción de eliminación
        pedidosAdapter = PedidosAdapter(movieList) { movieId ->
            deletePedido(movieId)  // Llamada a la función de eliminación al pasar el ID de la película
        }

        recyclerView.adapter = pedidosAdapter

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

                    val updateData = hashMapOf(
                        "userId" to documentId
                    )

                    documentReference.update(updateData as Map<String, Any>)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Datos subidos correctamente", Toast.LENGTH_SHORT).show()

                            val newMovie = Movie(documentId, title, imageUrl, streamUrl)

                            // Agregar el nuevo item a la lista y notificar al adaptador
                            movieList.add(newMovie)

                            // Asegúrate de agregar un elemento en la lista correctamente y notificar al adaptador
//                            val newMovie = Movie(documentId, title, imageUrl, streamUrl)
                            movieList.add(newMovie)  // Agregar el nuevo elemento
                            activity?.runOnUiThread {
                                pedidosAdapter.notifyItemInserted(movieList.size - 1)  // Notificar la inserción
                            }
                            clearFields()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error al actualizar el documento: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
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
                    // Obtiene la lista de items mapeada
                    val tvList = documents.map { doc ->
                        val documentId = doc.id
                        val title = doc.getString("title") ?: "Título no disponible"

                        // Crear el objeto Movie
                        Movie(id = documentId, title = title)
                    }

                    // Verificar si ya existe algún dato en la lista
                    val currentSize = movieList.size

                    // Agregar los nuevos items a la lista existente
                    movieList.addAll(tvList)

                    // Notificar al adaptador de que se han agregado nuevos items
                    pedidosAdapter.notifyItemRangeInserted(currentSize, tvList.size)
                } else {
                    Toast.makeText(context, "No hay datos disponibles", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al cargar los datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    private fun clearFields() {
        titleEditText.text.clear()
        imageUrlEditText.text.clear()
        streamUrlEditText.text.clear()
    }

    private fun deletePedido(documentId: String) {
        Log.d("deletePedido", "ID del documento a eliminar: $documentId")

        val db = FirebaseFirestore.getInstance()

        if (documentId.isEmpty()) {
            Toast.makeText(requireContext(), "ID de documento no válido", Toast.LENGTH_SHORT).show()
            return
        }

        val pedidoRef = db.collection("tv").document(documentId)

        pedidoRef.delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Pedido eliminado correctamente", Toast.LENGTH_SHORT).show()

                val positionToRemove = movieList.indexOfFirst { it.id == documentId }

                if (positionToRemove != -1) {
                    movieList.removeAt(positionToRemove)
                    pedidosAdapter.notifyItemRemoved(positionToRemove)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al eliminar pedido: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


}
