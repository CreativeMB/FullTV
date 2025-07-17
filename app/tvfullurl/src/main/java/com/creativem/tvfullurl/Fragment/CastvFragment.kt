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

    // Cargar usuarios desde Firebase Realtime Database
    private fun cargarUsuarios() {
        userList.clear() // Limpiar lista antes de cargar

        val databaseRef = FirebaseDatabase.getInstance().reference.child("usuarios")

        databaseRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                for (userSnapshot in snapshot.children) {
                    // Obtiene el objeto User de los datos del snapshot
                    val user = userSnapshot.getValue(User::class.java)

                    user?.let {
                        // ¡AQUÍ ESTÁ LA CORRECCIÓN!
                        // Asignamos el UID (userSnapshot.key) al campo 'userId' de nuestro objeto
                        it.userId = userSnapshot.key ?: ""

                        // Añadimos el objeto 'it' (que ahora tiene el ID correcto) a la lista
                        userList.add(it)
                    }
                }
                castvAdapter.notifyDataSetChanged() // Notificar cambios
            } else {
                Log.d("Users", "No se encontraron usuarios.")
            }
        }.addOnFailureListener { e ->
            Log.e("Users", "Error al cargar usuarios", e)
        }
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
                        user.isOnline = estadosDeConexion[user.id] ?: false
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
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId)

        userRef.child("estado").setValue("eliminado")
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Usuario marcado como eliminado", Toast.LENGTH_SHORT).show()
                llamarEliminacionEnFly(userId)
            }
            .addOnFailureListener { e ->
                Log.e("Usuarios", "Error al actualizar estado", e)
                Toast.makeText(requireContext(), "Error al marcar como eliminado", Toast.LENGTH_SHORT).show()
            }
    }

    private fun llamarEliminacionEnFly(userId: String) {
        val url = "https://server-csks8w.fly.dev/eliminar-usuario"
        Log.d("FlyServer", "Preparando solicitud a $url con UID: $userId")

        val json = JSONObject().apply {
            put("uid", userId)
        }

        Log.d("FlyServer", "Cuerpo JSON a enviar: $json")

        val request = object : JsonObjectRequest(
            Request.Method.POST, url, json,
            { response ->
                Log.d("FlyServer", "Respuesta recibida del servidor: $response")
                val mensaje = response.optString("mensaje", "Usuario eliminado desde servidor")
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
            },
            { error ->
                Log.e("FlyServer", "❌ Error en la solicitud: ${error.message}")
                error.networkResponse?.let { networkResponse ->
                    val statusCode = networkResponse.statusCode
                    val data = networkResponse.data?.decodeToString()
                    Log.e("FlyServer", "Código HTTP: $statusCode, Respuesta: $data")
                } ?: Log.e("FlyServer", "No hay respuesta del servidor (puede ser problema de red o TLS)")

                Toast.makeText(requireContext(), "Error al comunicar con el servidor", Toast.LENGTH_LONG).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = hashMapOf("Content-Type" to "application/json")
                Log.d("FlyServer", "Encabezados de la solicitud: $headers")
                return headers
            }
        }

        Log.d("FlyServer", "Enviando solicitud POST a $url...")
        Volley.newRequestQueue(requireContext()).add(request)
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
