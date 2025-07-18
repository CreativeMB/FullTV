package com.creativem.fulltv.enlinea

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object UsuarioEstadoManager {

    private const val TAG = "UsuarioEstadoManager"
    private var isOnlineAlreadySet = false

    // ✅ Se llama desde ActivityLifecycleHandler para marcar en línea o fuera de línea
    fun marcarUsuarioEnLineaDesdeApp(enLinea: Boolean) {
        marcarUsuarioEnLinea(enLinea)
    }

    private fun marcarUsuarioEnLinea(enLinea: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(userId)

        Log.d(TAG, "🔄 marcarUsuarioEnLinea llamado con enLinea=$enLinea para UID=$userId")

        userRef.get().addOnSuccessListener { snapshot ->
            val estado = snapshot.child("estado").getValue(String::class.java)
            if (!snapshot.exists() || estado == "eliminado") {
                Log.w(TAG, "Usuario no existe o está eliminado. No se actualiza enlinea.")
                return@addOnSuccessListener
            }

            if (enLinea) {
                if (isOnlineAlreadySet) {
                    Log.d(TAG, "🟡 Ya estaba en línea. No se repite escritura.")
                    return@addOnSuccessListener
                }
                isOnlineAlreadySet = true
                userRef.child("enlinea").setValue(true)
                Log.d(TAG, "✅ Usuario marcado EN LÍNEA")
            } else {
                isOnlineAlreadySet = false
                userRef.child("enlinea").setValue(false)
                Log.d(TAG, "⛔ Usuario marcado FUERA DE LÍNEA")
            }

        }.addOnFailureListener {
            Log.e(TAG, "❌ Error al verificar usuario: ${it.message}")
        }
    }


    // 🔴 Llamar esta función desde tu opción de cerrar sesión
    fun cerrarSesion() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(user.uid)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                userRef.child("enlinea").setValue(false).addOnCompleteListener {
                    Log.d(TAG, "Cerrar sesión: usuario ${user.uid} marcado como 'false' en 'enlinea'.")
                    finalizarCierreDeSesion()
                }
            } else {
                Log.w(TAG, "Cerrar sesión: El usuario ${user.uid} no existe en la DB.")
                finalizarCierreDeSesion()
            }
        }.addOnFailureListener {
            Log.e(TAG, "Cerrar sesión: Error al comprobar la existencia del usuario.", it)
            finalizarCierreDeSesion()
        }
    }

    private fun finalizarCierreDeSesion() {
        isOnlineAlreadySet = false
        FirebaseAuth.getInstance().signOut()
        Log.d(TAG, "Sesión cerrada correctamente.")
    }
}
