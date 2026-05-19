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
    private val databaseRef = FirebaseDatabase.getInstance().reference.child("movies")
    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        iniciarRecycler()
        escucharPeliculasEnTiempoReal() // Cambio de .get() a Escuchador real

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                moviesAdapter.filter(newText.orEmpty())
                return true
            }
        })
    }

    private fun escucharPeliculasEnTiempoReal() {
        databaseRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                movieList.clear()
                for (child in snapshot.children) {
                    val movie = child.getValue(Movie::class.java)
                    movie?.let {
                        it.id = child.key ?: "" // Aseguramos el ID
                        movieList.add(it)
                    }
                }
                moviesAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun iniciarRecycler() {
        moviesAdapter = MoviesAdapter(movieList,
            onDeleteClick = { id -> deleteMovie(id) },
            onEditClick = { movie -> editMovie(movie) },
            isEditable = true
        )
        binding.recyclerViewPedidos.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPedidos.adapter = moviesAdapter
    }

    private fun editMovie(movie: Movie) {
        val bundle = Bundle().apply { putString("movieId", movie.id) }
        findNavController().navigate(R.id.action_editarPeliculaFragment_to_nuevaPeliculaFragment, bundle)
    }

    private fun deleteMovie(movieId: String) {
        databaseRef.child(movieId).removeValue().addOnSuccessListener {
            Toast.makeText(requireContext(), "Eliminada", Toast.LENGTH_SHORT).show()
        }
    }
}