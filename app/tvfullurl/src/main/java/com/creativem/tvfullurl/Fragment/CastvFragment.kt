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
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
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
                userList.clear()

                if (snapshot.exists()) {
                    for (userSnapshot in snapshot.children) {
                        try {
                            // 1. Cargamos el usuario (con la nueva data class ya no explota)
                            val user = userSnapshot.getValue(User::class.java)

                            if (user != null) {
                                user.userId = userSnapshot.key ?: ""

                                if (user.estado != "eliminado") {
                                    // 2. Leer estado online de forma segura
                                    val enLineaValue = userSnapshot.child("enlinea").value
                                    user.isOnline = when (enLineaValue) {
                                        is Boolean -> enLineaValue
                                        is Long -> enLineaValue == 1L
                                        is String -> enLineaValue.lowercase() == "true"
                                        else -> false
                                    }

                                    userList.add(user)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Usuarios", "Error en usuario ${userSnapshot.key}: ${e.message}")
                        }
                    }

                    // 3. 🔄 ORDENAR (Convertimos a Long para comparar)
                    userList.sortWith(
                        compareByDescending<User> { user ->
                            // Convertimos ultimaConexion a Long de forma segura para ordenar
                            when (val fecha = user.ultimaConexion) {
                                is Long -> fecha
                                is String -> fecha.toLongOrNull() ?: 0L
                                else -> 0L
                            }
                        }.thenBy { it.nombre }
                    )

                } else {
                    Log.d("Usuarios", "La base de datos está vacía.")
                }

                // 4. Refrescar UI
                castvAdapter.filter("")
                Log.d("Usuarios", "✅ Usuarios cargados con éxito: ${userList.size}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Usuarios", "Error: ${error.message}")
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
                        Log.d("Conexion", "Actualizado ${user.nombre} (${user.userId}): ${user.isOnline}")
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
    private fun updateCastv(userId: String, puntosASumar: Int) {
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId).child("castv")

        userRef.runTransaction(object : ValueEventListener, Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                // 1. Obtener los puntos actuales de forma segura
                val puntosActuales = mutableData.getValue(Int::class.java) ?: 0

                // 2. Sumar el nuevo paquete a lo que ya tenía
                mutableData.value = puntosActuales + puntosASumar

                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    Toast.makeText(requireContext(), "¡Paquete sumado con éxito!", Toast.LENGTH_SHORT).show()
                    // cargarUsuarios() no hace falta llamarlo aquí,
                    // el addValueEventListener de cargarUsuarios() se refrescará solo.
                } else {
                    Toast.makeText(requireContext(), "Error al sumar puntos", Toast.LENGTH_SHORT).show()
                }
            }

            // Métodos requeridos por ValueEventListener (pueden ir vacíos)
            override fun onDataChange(snapshot: DataSnapshot) {}
            override fun onCancelled(error: DatabaseError) {}
        })
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
