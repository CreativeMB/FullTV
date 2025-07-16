package com.creativem.fulltv

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object CastvHelper {

    private const val TAG = "CastvHelper"

    // Crear usuario en Realtime DB con castv inicial = 10 si no existe
    fun actualizarCastvSiNoExiste(
        context: Context,
        userId: String,
        nombre: String,
        email: String?
    ) {
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId)

        userRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val user = mapOf(
                        "nombre" to nombre,
                        "correo" to email,
                        "castv" to 10,
                        "userId" to userId,
                        "estado" to "activo",
                        "enlinea" to false
                    )

                    userRef.setValue(user)
                        .addOnSuccessListener {
                            Log.d(TAG, "Usuario creado con castv = 10.")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error al crear usuario en Realtime DB", e)
                            Toast.makeText(context, "Error al crear usuario", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener usuario", e)
                Toast.makeText(context, "Error al verificar usuario", Toast.LENGTH_SHORT).show()
            }
    }

    // Consultar el valor actual del castv
    fun obtenerCastv(
        userId: String,
        onSuccess: (castv: Int) -> Unit,
        onFailure: (error: Exception) -> Unit
    ) {
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId)

        userRef.child("castv").get()
            .addOnSuccessListener { snapshot ->
                val puntos = snapshot.getValue(Int::class.java) ?: 0
                onSuccess(puntos)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    fun obtenerDatosUsuario(
        userId: String,
        onSuccess: (nombre: String, correo: String, castv: Int, enlinea: Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ): ValueEventListener {
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nombre = snapshot.child("nombre").getValue(String::class.java) ?: "Sin nombre"
                val correo = snapshot.child("correo").getValue(String::class.java) ?: ""
                val castv = snapshot.child("castv").getValue(Int::class.java) ?: 0
                val enlinea = snapshot.child("enlinea").getValue(Boolean::class.java) ?: false
                onSuccess(nombre, correo, castv, enlinea)
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        return listener // Devuelve el listener para poder removerlo si se necesita más adelante
    }


}