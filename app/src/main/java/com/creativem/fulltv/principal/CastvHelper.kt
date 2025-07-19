package com.creativem.fulltv.principal

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object CastvHelper {

    private const val TAG = "CastvHelper"

    // 🔐 Función para codificar el correo y usarlo como clave válida
    private fun codificarCorreo(correo: String): String {
        return correo.replace(".", "_").replace("@", "_")
    }

    // ✅ Crear el usuario solo si no existe (o verificar si fue eliminado)
    fun nuevosusuarios(
        context: Context,
        nombre: String,
        email: String?
    ) {
        if (email.isNullOrBlank()) {
            Toast.makeText(context, "Correo inválido", Toast.LENGTH_SHORT).show()
            return
        }

        val correoKey = codificarCorreo(email)
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(correoKey)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val estado = snapshot.child("estado").getValue(String::class.java)

                if (estado == "eliminado") {
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(context, "Tu cuenta ha sido eliminada. No puedes ingresar.", Toast.LENGTH_LONG).show()
                    if (context is Activity) context.finish()
                } else {
                    Log.d(TAG, "Usuario ya existe, accediendo normalmente.")
                    userRef.child("enlinea").setValue(true)
                }
            } else {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val fechaFormateada = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

                val user = mapOf(
                    "nombre" to nombre,
                    "correo" to email,
                    "castv" to 10,
                    "userId" to userId,
                    "estado" to "activo",
                    "enlinea" to true,
                    "fechaCreacion" to fechaFormateada
                )

                userRef.setValue(user)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Usuario creado correctamente en Realtime DB.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Error al crear usuario", e)
                        Toast.makeText(context, "Error al crear usuario", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "❌ Error al verificar usuario", e)
            Toast.makeText(context, "Error al verificar usuario", Toast.LENGTH_SHORT).show()
        }
    }


    // ✅ Obtener todos los datos del usuario desde el correo
    fun obtenerDatosUsuario(
        email: String,
        onSuccess: (nombre: String, correo: String, castv: Int, enlinea: Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ): ValueEventListener {
        val correoKey = codificarCorreo(email)
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(correoKey)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nombre = snapshot.child("nombre").getValue(String::class.java) ?: "Sin nombre"
                val correo = snapshot.child("correo").getValue(String::class.java) ?: ""
                val castv = snapshot.child("castv").getValue(Int::class.java) ?: 0
                val enlinea = snapshot.child("enlinea").getValue(Boolean::class.java) ?: true
                onSuccess(nombre, correo, castv, enlinea)
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        return listener
    }


}