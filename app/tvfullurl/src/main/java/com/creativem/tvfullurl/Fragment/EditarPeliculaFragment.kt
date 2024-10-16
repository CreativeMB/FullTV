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
import com.creativem.tvfullurl.databinding.FragmentSoporteBinding
import com.google.firebase.firestore.FirebaseFirestore

class EditarPeliculaFragment : Fragment() {

    private lateinit var binding: FragmentSoporteBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSoporteBinding.inflate(inflater, container, false)
        db = FirebaseFirestore.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        iniciarRecycler()
        loadMovies()
    }

    private fun loadMovies() {
        movieList.clear() // Limpiar la lista antes de agregar nuevas películas
        db.collection("movies").get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val movie =
                        document.toObject(Movie::class.java).copy(id = document.id) // Agregar el ID
                    movieList.add(movie)
                }
                moviesAdapter.notifyDataSetChanged() // Notificar al adaptador de cambios
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Error al cargar las películas",
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
                editMovie(movie) // Aquí pasas el objeto Movie en lugar del ID
            },
            isEditable = true
        )
        binding.recyclerViewPedidos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moviesAdapter
        }

    }

    private fun editMovie(movie: Movie){
        val bundle = Bundle().apply {
            putString("movieId", movie.id)
        }
        findNavController().navigate(
            R.id.action_editarPeliculaFragment_to_nuevaPeliculaFragment,
            bundle
        )
    }

    private fun deleteMovie(movieId: String) {
        db.collection("movies").document(movieId).delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Película eliminada", Toast.LENGTH_SHORT).show()
                loadMovies() // Recargar películas
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error al eliminar la película",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("ListaPeliculasFragment", "Error al eliminar la película", e)
            }
    }
}