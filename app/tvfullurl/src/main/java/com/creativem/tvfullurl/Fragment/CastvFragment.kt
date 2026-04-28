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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue

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
        verificarEstadosDeConexion()
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

    private fun cargarUsuarios() {
        val databaseRef = FirebaseDatabase.getInstance().reference.child("usuarios")

        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // 1. Limpiamos la lista local para no duplicar datos
                userList.clear()

                if (snapshot.exists()) {
                    for (userSnapshot in snapshot.children) {
                        val user = userSnapshot.getValue(User::class.java)

                        if (user != null) {
                            // Asignamos el ID (la clave del nodo) al objeto usuario
                            user.userId = userSnapshot.key ?: ""

                            // 2. FILTRO:
                            // - Si usaste removeValue(), el usuario ya no sale aquí.
                            // - Si el usuario aún existe pero tiene estado "eliminado", lo ocultamos.
                            if (user.estado != "eliminado") {

                                // 3. ESTADO ONLINE:
                                // Obtenemos el valor de "enlinea" directamente del snapshot
                                val enLineaDB = userSnapshot.child("enlinea").getValue(Boolean::class.java) ?: false
                                user.isOnline = enLineaDB

                                userList.add(user)
                            }
                        }
                    }

                    // 4. ORDENAR:
                    // Primero por fecha de creación (más nuevos arriba)
                    // Luego por correo (alfabético)
                    userList.sortWith(
                        compareByDescending<User> { it.fechaCreacion }
                            .thenBy { it.correo }
                    )

                } else {
                    Log.d("Usuarios", "La base de datos de usuarios está vacía.")
                }

                // 5. ACTUALIZAR UI:
                // Notificamos al adaptador para que refresque la lista en pantalla
                castvAdapter.filter("")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Usuarios", "Error en la base de datos: ${error.message}")
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error al cargar usuarios", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun verificarEstadosDeConexion() {
        val usuariosRef = FirebaseDatabase.getInstance().reference.child("usuarios")

        Log.d("Conexion", "Accediendo al nodo 'usuarios' en Realtime Database para verificar 'enlinea'.")

        usuariosRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val snapshot = task.result
                if (snapshot != null && snapshot.exists()) {
                    val estadosDeConexion = mutableMapOf<String, Boolean>()

                    Log.d("Conexion", "Datos obtenidos: ${snapshot.childrenCount} usuarios.")

                    for (userSnapshot in snapshot.children) {
                        val userId = userSnapshot.key
//                        val isOnline = userSnapshot.child("enlinea").getValue(Boolean::class.java) ?: false

                        val estado = userSnapshot.child("estado").getValue(String::class.java)
                        val isOnline = if (estado == "activo") {
                            userSnapshot.child("enlinea").getValue(Boolean::class.java) ?: false
                        } else {
                            false // Usuario eliminado no debe marcarse como en línea
                        }


                        Log.d("Conexion", "Usuario ID: $userId, Estado de Conexión: $isOnline")

                        if (userId != null) {
                            estadosDeConexion[userId] = isOnline
                        }
                    }

                    // Actualizar `isOnline` para cada usuario en `userList`
                    for (user in userList) {
                        user.isOnline = estadosDeConexion[user.userId] ?: false
                        Log.d("Conexion", "Actualizado ${user.nombre} (${user.id}): ${user.isOnline}")
                    }

                    castvAdapter.notifyDataSetChanged()
                    Log.d("Conexion", "Adaptador notificado: estados 'enlinea' actualizados.")
                } else {
                    Log.e("Conexion", "No se encontraron usuarios.")
                }
            } else {
                Log.e("Conexion", "Error al acceder al nodo 'usuarios'.", task.exception)
            }
        }.addOnFailureListener { e ->
            Log.e("Conexion", "Error al verificar conexión de usuarios.", e)
        }
    }

    // Iniciar el RecyclerView
    private fun iniciarRecycler() {
        // Inicializar el adaptador de usuarios con las acciones de editar y eliminar
        castvAdapter = CastvAdapter(
            userList,
            onEditClick = { userId, newPoints -> updateCastv(userId, newPoints) },
            onDeleteClick = { userId -> deleteUsers(userId) }
        )

        // Configurar el RecyclerView para los usuarios
        binding.recyclerViewUsers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = castvAdapter
        }
    }

    // Actualizar solo el campo de puntos de un usuario
    private fun updateCastv(userId: String, newPoints: Int) {
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId)

        userRef.child("castv").setValue(newPoints)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "CasTV actualizado", Toast.LENGTH_SHORT).show()
                cargarUsuarios() // Recargar usuarios si lo necesitas en pantalla
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al actualizar CasTV", Toast.LENGTH_SHORT).show()
                Log.e("Usuarios", "Error actualizando castv", e)
            }
    }


    private fun deleteUsers(userId: String) {
        // userId es el correo codificado (ej: usuario_gmail_com)
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId)

        // Es mejor pedir una confirmación antes de borrar todo
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar permanentemente")
            .setMessage("¿Estás seguro de borrar a este usuario? Se perderán sus puntos y su historial por completo.")
            .setPositiveButton("Borrar Todo") { _, _ ->

                // .removeValue() elimina el nodo completo de ese usuario
                userRef.removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Datos eliminados por completo", Toast.LENGTH_SHORT).show()

                        // Ya no llamamos a Fly.dev porque no funciona
                        // cargarUsuarios() se activará solo por el ValueEventListener
                    }
                    .addOnFailureListener { e ->
                        Log.e("Usuarios", "Error al borrar nodo", e)
                        Toast.makeText(requireContext(), "Error al eliminar datos", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
