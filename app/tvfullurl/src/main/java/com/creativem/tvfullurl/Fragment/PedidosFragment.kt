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
import com.creativem.tvfullurl.adapter.PedidosAdapter
import com.creativem.tvfullurl.databinding.FragmentPedidosBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot


class PedidosFragment : Fragment() {
    private lateinit var binding: FragmentPedidosBinding

    private lateinit var pedidosAdapter: PedidosAdapter
    private var movieList: MutableList<Movie> = mutableListOf() // Lista de películas

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
                pedidosAdapter.filter(newText.orEmpty())
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
                        val nombre = document.getString("nombre") ?: ""
                        val email = document.getString("email") ?: ""
                        val title = document.getString("title") ?: ""
                        val castv = document.getLong("castv")?.toInt() ?: 0
                        val id = document.id

                        val movie = Movie(
                            id = id,
                            nombre = nombre,
                            email = email,
                            title = title,
                            castv = castv // ya es Int
                        )
                        movieList.add(movie)
                    }
                    pedidosAdapter.updateMovieList(movieList)
                } else {
                    Log.e("PedidosFragment", "Error getting documents: ", task.exception)
                }
            }
            .addOnFailureListener { e ->
                Log.e("PedidosFragment", "Error loading pedidos", e)
            }
    }


    private fun iniciarRecycler() {
        pedidosAdapter = PedidosAdapter(
            movieList,
            onDeleteClick = { movieId ->
                deletePedido(movieId)
            }
        )
        binding.recyclerViewPedidos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pedidosAdapter
        }
    }

    private fun deletePedido(pedidoId: String) {
        val db = FirebaseFirestore.getInstance()

        // Verificar que el ID del pedido no esté vacío
        if (pedidoId.isEmpty()) {
            Toast.makeText(requireContext(), "ID de pedido no válido", Toast.LENGTH_SHORT).show()
            return
        }

        // Referencia al documento específico dentro de la colección "tv"
        val pedidoRef = db.collection("pedidosmovies").document(pedidoId)

        // Eliminar el documento de la colección "pedidosmovies"
        pedidoRef.delete()
            .addOnSuccessListener {
                // Mostrar mensaje de éxito
                Toast.makeText(requireContext(), "Pedido eliminado correctamente", Toast.LENGTH_SHORT).show()

                // Eliminar el item de la lista local (si es necesario)
                val movieToRemove = movieList.find { it.id == pedidoId }
                movieList.remove(movieToRemove)

                // Actualizar el RecyclerView
                pedidosAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                // Mostrar mensaje de error
                Toast.makeText(requireContext(), "Error al eliminar pedido: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
