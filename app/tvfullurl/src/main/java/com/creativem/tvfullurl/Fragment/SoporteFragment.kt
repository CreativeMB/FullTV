package com.creativem.tvfullurl.Fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.adapter.MoviesAdapter
import com.creativem.tvfullurl.databinding.FragmentSoporteBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot


class SoporteFragment : Fragment() {
    private lateinit var binding: FragmentSoporteBinding

    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()

    private lateinit var db: FirebaseFirestore


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSoporteBinding.inflate(layoutInflater)
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
                    for (document in task.result!!) {
                        val movie: Movie =
                            document.toObject(Movie::class.java).copy(id = document.id)
                        movieList.add(movie)
                    }
                    moviesAdapter.notifyDataSetChanged()
                } else {
                    Log.e("PedidosMovies", "Error getting documents: ", task.exception)
                }
            }
            .addOnFailureListener { e ->
                Log.e("PedidosMovies", "Error loading vermovies", e)
            }
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