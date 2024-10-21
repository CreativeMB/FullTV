package com.creativem.tvfullurl.Fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.tvfullurl.adapter.CastvAdapter
import com.creativem.tvfullurl.databinding.FragmentCastvBinding
import com.creativem.tvfullurl.modelo.User
import com.google.firebase.firestore.FirebaseFirestore

class CastvFragment : Fragment() {

    private var _binding: FragmentCastvBinding? = null
    private val binding get() = _binding!!
    private lateinit var castvAdapter: CastvAdapter
    private var userList: MutableList<User> = mutableListOf()

    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflar el layout usando ViewBinding
        _binding = FragmentCastvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        iniciarRecycler()
        cargarUsuarios()

        // Configurar el SearchView para filtrar usuarios
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Filtrar la lista de usuarios según el texto ingresado
                castvAdapter.filter(newText.orEmpty())
                return true
            }
        })
    }

    // Cargar usuarios desde Firestore
    private fun cargarUsuarios() {
        userList.clear() // Limpiar la lista antes de cargar nuevos datos
        db.collection("users").get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    for (document in task.result!!) {
                        val user: User = document.toObject(User::class.java).copy(id = document.id)
                        userList.add(user)
                    }
                    castvAdapter.notifyDataSetChanged() // Notificar al adaptador que los datos han cambiado
                } else {
                    Log.e("Users", "Error getting documents: ", task.exception)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Users", "Error loading users", e)
            }
    }

    // Iniciar el RecyclerView
    private fun iniciarRecycler() {
        // Inicializar el adaptador de usuarios con las acciones de editar y eliminar
        castvAdapter = CastvAdapter(
            userList,
            onEditClick = { userId, newPoints -> updatePoints(userId, newPoints) },
            onDeleteClick = { userId -> deleteUsers(userId) }
        )

        // Configurar el RecyclerView para los usuarios
        binding.recyclerViewUsers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = castvAdapter
        }
    }

    // Actualizar solo el campo de puntos de un usuario
    private fun updatePoints(userId: String, newPoints: Int) {
        db.collection("users").document(userId)
            .update("puntos", newPoints)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Puntos actualizados", Toast.LENGTH_SHORT).show()
                cargarUsuarios() // Recargar los usuarios después de la actualización
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al actualizar los puntos", Toast.LENGTH_SHORT).show()
                Log.e("Usuarios", "Error actualizando puntos", e)
            }
    }

    // Eliminar un usuario de Firestore
    private fun deleteUsers(userId: String) {
        db.collection("users").document(userId).delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Usuario eliminado", Toast.LENGTH_SHORT).show()
                cargarUsuarios() // Recargar usuarios después de eliminar
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al eliminar el usuario", Toast.LENGTH_SHORT).show()
                Log.e("Usuarios", "Error eliminando usuario", e)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
