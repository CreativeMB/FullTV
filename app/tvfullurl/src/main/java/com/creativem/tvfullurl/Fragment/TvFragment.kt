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

            // Añadir un nuevo documento con un ID único generado automáticamente por Firebase
            firestore.collection("tv")
                .add(tvData)
                .addOnSuccessListener { documentReference ->
                    val documentId = documentReference.id // Obtienes el ID del documento creado

                    // Ahora, actualiza el documento para agregar el userId (ID del documento generado por Firebase)
                    val updateData = hashMapOf(
                        "userId" to documentId // Agregamos el userId como el ID del documento
                    )

                    // Actualizar el documento con el userId
                    documentReference.update(updateData as Map<String, Any>)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Datos subidos correctamente", Toast.LENGTH_SHORT).show()

                            // Ahora que tienes el ID, puedes almacenar el nuevo Movie
                            val newMovie = Movie(documentId, title, imageUrl, streamUrl)  // Asegúrate de pasar el documentId

                            // Agregar el nuevo item a la lista y notificar al adaptador
                            movieList.add(newMovie)

                            // Usar notifyItemInserted con el índice correcto
                            val position = movieList.size - 1
                            pedidosAdapter.notifyItemInserted(position)

                            // Si usas un método que actualiza la lista completa, puedes usar notifyDataSetChanged()
                            // pedidosAdapter.notifyDataSetChanged()

                            // Limpiar los campos después de agregar
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
                    val tvList = documents.map { doc ->
                        // Obtener el ID del documento
                        val documentId = doc.id

                        // Mapear los datos de cada documento a un objeto (como Movie en tu adaptador)
                        val title = doc.getString("title") ?: "Título no disponible"


                        // Verificación de los datos antes de crear el objeto Movie
                        Log.d("loadData", "Title: $title")

                        // Agregar el ID del documento al objeto Movie
                        Movie(id = documentId, title = title)
                    }

                    // Actualiza el adaptador con la lista de películas
                    pedidosAdapter.updateMovieList(tvList)
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

        // Verificar si el ID del documento está vacío
        if (documentId.isEmpty()) {
            Toast.makeText(requireContext(), "ID de documento no válido", Toast.LENGTH_SHORT).show()
            return
        }

        // Referencia al documento en la colección "tv" utilizando el ID del documento
        val pedidoRef = db.collection("tv").document(documentId)

        // Eliminar el documento
        pedidoRef.delete()
            .addOnSuccessListener {
                // Mostrar mensaje de éxito
                Toast.makeText(requireContext(), "Pedido eliminado correctamente", Toast.LENGTH_SHORT).show()

                // Encontrar el index del item en la lista
                val positionToRemove = movieList.indexOfFirst { it.id == documentId }

                if (positionToRemove != -1) {
                    // Eliminar el item de la lista local
                    movieList.removeAt(positionToRemove)

                    // Notificar al adaptador que se eliminó un item
                    pedidosAdapter.notifyItemRemoved(positionToRemove)
                }

                // Si es necesario, se puede volver a notificar el cambio completo (aunque no es la mejor opción por performance)
                // pedidosAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                // Mostrar mensaje de error si falla
                Toast.makeText(requireContext(), "Error al eliminar pedido: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


}
