package com.creativem.tvfullurl.Fragment

import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.adapter.MoviesAdapter
import com.creativem.tvfullurl.databinding.FragmentPedidosBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot


class PedidosFragment : Fragment() {
    private lateinit var binding: FragmentPedidosBinding

    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()

    private lateinit var db: FirebaseFirestore


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPedidosBinding.inflate(layoutInflater)
        db = FirebaseFirestore.getInstance()

        iniciarRecycler()
        cargarPedidos()
        // Configurar el SearchView
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                moviesAdapter.filter(newText.orEmpty())
                return true
            }
        })

        return binding.root
    }

    private fun cargarPedidos() {
        movieList.clear()

        db.collection("pedidosmovies").get()
            .addOnCompleteListener { task: Task<QuerySnapshot> ->
                if (task.isSuccessful) {
                    val userIds = mutableListOf<String>()
                    for (document in task.result!!) {
                        val movie: Movie = document.toObject(Movie::class.java).copy(id = document.id)
                        userIds.add(movie.userId)
                        movieList.add(movie) // Aquí solo agregamos la película sin el usuario de prueba

                    }
                    Log.d("PedidosFragment", "Películas cargadas: ${movieList.size}") // Log de verificación
                    cargarNombresUsuarios(userIds) // Cargar nombres de usuario después de cargar las películas

                    // Notificar al adaptador aquí
                    moviesAdapter.notifyDataSetChanged() // Aquí puedes notificar el cambio también
                } else {
                    Log.e("PedidosMovies", "Error getting documents: ", task.exception)
                }
            }
            .addOnFailureListener { e ->
                Log.e("PedidosMovies", "Error loading movies", e)
            }
    }

    private fun cargarNombresUsuarios(userIds: List<String>) {
        val filteredUserIds = userIds.filter { it.isNotEmpty() }

        if (filteredUserIds.isEmpty()) return
        db.collection("users")
            .whereIn(FieldPath.documentId(), filteredUserIds)
            .get()
            .addOnSuccessListener { documents ->
                val userNamesMap = mutableMapOf<String, String>()
                for (document in documents) {
                    val userId = document.id
                    val userName = document.getString("nombre") ?: "Usuario desconocido"
                    userNamesMap[userId] = userName
                }

                actualizarNombresUsuariosEnPeliculas(userNamesMap)

                // Actualiza la lista del adaptador con la nueva lista de películas
                moviesAdapter.updateMovieList(movieList) // Actualiza el adaptador
            }
            .addOnFailureListener { e ->
                Log.e("users", "Error al cargar nombres de usuarios", e)
            }
    }

    private fun actualizarNombresUsuariosEnPeliculas(userNamesMap: Map<String, String>) {
        // Actualizar los nombres de usuario en la lista de películas
        for (movie in movieList) {
            val userName = userNamesMap[movie.userId] ?: "Usuario desconocido"
            movie.userName = userName // Asigna el nombre de usuario correspondiente
        }

        // Notificar al adaptador que los datos han cambiado
        moviesAdapter.notifyDataSetChanged()
    }

    private fun iniciarRecycler() {
        moviesAdapter = MoviesAdapter(
            movieList,
            onDeleteClick = { movieId ->
                deletePedido(movieId)
            },
            onEditClick = { movieId ->
            },
            isEditable = false
        )
        binding.recyclerViewPedidos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moviesAdapter
        }

    }


    private fun deletePedido(movieId: String) {
        db.collection("pedidosmovies").document(movieId).delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Pedido eliminado", Toast.LENGTH_SHORT).show()
                cargarPedidos() // Recargar películas después de eliminar
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error al eliminar la película",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("PedidosMovies", "Error deleting movie", e)
            }
    }

}