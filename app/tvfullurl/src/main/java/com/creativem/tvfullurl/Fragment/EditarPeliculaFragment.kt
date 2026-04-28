package com.creativem.tvfullurl.Fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.adapter.MoviesAdapter
import com.creativem.tvfullurl.databinding.FragmentPedidosBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class EditarPeliculaFragment : Fragment() {
    private lateinit var binding: FragmentPedidosBinding

    // NUEVA RUTA: Usamos DatabaseReference en lugar de FirebaseFirestore
    private lateinit var databaseRef: DatabaseReference

    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPedidosBinding.inflate(inflater, container, false)

        // Inicializamos Realtime Database
        databaseRef = FirebaseDatabase.getInstance().reference

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        iniciarRecycler()
        loadMovies()

        // Configurar el SearchView (Lógica original conservada)
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                moviesAdapter.filter(newText.orEmpty())
                return true
            }
        })
    }

    private fun loadMovies() {
        movieList.clear() // Limpiar la lista

        // NUEVA RUTA: Consultamos el nodo "movies"
        databaseRef.child("movies").get()
            .addOnSuccessListener { snapshot ->
                for (child in snapshot.children) {
                    // 1. Obtenemos el objeto Movie usando el sistema automático de Firebase
                    val movie = child.getValue(Movie::class.java)

                    if (movie != null) {
                        // 2. Asignamos el ID manualmente (ya no usamos .copy)
                        movie.id = child.key ?: ""

                        movieList.add(movie)
                    }
                }
                moviesAdapter.notifyDataSetChanged() // Notificar al adaptador
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Error al cargar las películas desde la nueva ruta",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun iniciarRecycler() {
        moviesAdapter = MoviesAdapter(
            movieList,
            onDeleteClick = { movieId ->
                deleteMovie(movieId)
            },
            onEditClick = { movie ->
                editMovie(movie)
            },
            isEditable = true
        )
        binding.recyclerViewPedidos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moviesAdapter
        }
    }

    private fun editMovie(movie: Movie) {
        // Lógica original conservada
        val bundle = Bundle().apply {
            putString("movieId", movie.id)
        }
        findNavController().navigate(
            R.id.action_editarPeliculaFragment_to_nuevaPeliculaFragment,
            bundle
        )
    }

    private fun deleteMovie(movieId: String) {
        // NUEVA RUTA: Eliminamos el nodo específico por su ID
        databaseRef.child("movies").child(movieId).removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Película eliminada de la nueva ruta", Toast.LENGTH_SHORT).show()
                loadMovies() // Recargar películas
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error al eliminar la película",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("EditarPeliculaFragment", "Error al eliminar en Realtime DB", e)
            }
    }
}