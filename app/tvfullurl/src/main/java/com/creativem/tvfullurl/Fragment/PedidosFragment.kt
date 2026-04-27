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
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class PedidosFragment : Fragment() {
    private lateinit var binding: FragmentPedidosBinding

    private lateinit var pedidosAdapter: PedidosAdapter
    private var movieList: MutableList<Movie> = mutableListOf()

    // NUEVA RUTA: Usamos DatabaseReference en lugar de Firestore
    private lateinit var databaseRef: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPedidosBinding.inflate(layoutInflater)

        // Inicializamos Realtime Database
        databaseRef = FirebaseDatabase.getInstance().reference

        iniciarRecycler()
        cargarPedidos()

        // Configurar el SearchView (Lógica original conservada)
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

        // NUEVA RUTA: Consultamos el nodo "pedidosmovies"
        databaseRef.child("pedidosmovies").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        // ✅ FORMA CORRECTA Y SEGURA:
                        // Dejamos que Firebase rellene el objeto automáticamente
                        val movie = child.getValue(Movie::class.java)

                        if (movie != null) {
                            // Asignamos el ID de la llave del nodo
                            movie.id = child.key ?: ""

                            movieList.add(movie)
                        }
                    }
                    pedidosAdapter.updateMovieList(movieList)
                } else {
                    Log.d("PedidosFragment", "No hay pedidos en la nueva ruta.")
                    pedidosAdapter.updateMovieList(mutableListOf())
                }
            }
            .addOnFailureListener { e ->
                Log.e("PedidosFragment", "Error loading pedidos de Realtime DB", e)
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
        if (pedidoId.isEmpty()) {
            Toast.makeText(requireContext(), "ID de pedido no válido", Toast.LENGTH_SHORT).show()
            return
        }

        // NUEVA RUTA: Referencia al documento específico en Realtime Database
        databaseRef.child("pedidosmovies").child(pedidoId).removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Pedido eliminado correctamente de la nueva ruta", Toast.LENGTH_SHORT).show()

                // Eliminar el item de la lista local (Lógica original conservada)
                val movieToRemove = movieList.find { it.id == pedidoId }
                movieList.remove(movieToRemove)

                // Actualizar el RecyclerView
                pedidosAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al eliminar pedido: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}