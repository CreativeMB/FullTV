package com.creativem.fulltv.enlinea

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object UsuarioEstadoManager {

    private const val TAG = "UsuarioEstadoManager"
    private var isOnlineAlreadySet = false

    // 🔐 Codifica el correo como clave segura para Firebase
    private fun codificarCorreo(correo: String): String {
        return correo.replace(".", "_").replace("@", "_")
    }

    fun marcarUsuarioEnLineaDesdeApp(enLinea: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val correo = user.email ?: return
        val correoKey = codificarCorreo(correo)

        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)

        Log.d(TAG, "🔄 marcarUsuarioEnLinea llamado con enLinea=$enLinea para usuario: $correoKey")

        userRef.get().addOnSuccessListener { snapshot ->
            val estado = snapshot.child("estado").getValue(String::class.java)
            if (!snapshot.exists() || estado == "eliminado") {
                Log.w(TAG, "⚠️ Usuario no existe o está eliminado. No se actualiza 'enlinea'.")
                return@addOnSuccessListener
            }

            if (enLinea) {
                if (isOnlineAlreadySet) {
                    Log.d(TAG, "🟡 Ya estaba en línea. No se repite escritura.")
                    return@addOnSuccessListener
                }

                isOnlineAlreadySet = true

                // Configura la desconexión automática
                userRef.child("enlinea").onDisconnect().setValue(false)
                userRef.child("ultimaConexion").onDisconnect().setValue(ServerValue.TIMESTAMP)

                // Marca como en línea ahora
                userRef.child("enlinea").setValue(true)
                Log.d(TAG, "✅ Usuario marcado EN LÍNEA y onDisconnect configurado")

            } else {
                isOnlineAlreadySet = false
                userRef.child("enlinea").setValue(false)
                userRef.child("ultimaConexion").setValue(ServerValue.TIMESTAMP)
                Log.d(TAG, "⛔ Usuario marcado FUERA DE LÍNEA")
            }

        }.addOnFailureListener {
            Log.e(TAG, "❌ Error al verificar usuario: ${it.message}")
        }
    }

    fun cerrarSesion() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val correo = user.email ?: return
        val correoKey = codificarCorreo(correo)

        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                userRef.child("enlinea").setValue(false).addOnCompleteListener {
                    userRef.child("ultimaConexion").setValue(ServerValue.TIMESTAMP)
                    Log.d(TAG, "🔒 Usuario marcado fuera de línea al cerrar sesión.")
                    finalizarCierreDeSesion()
                }
            } else {
                Log.w(TAG, "⚠️ Usuario no encontrado en la base de datos.")
                finalizarCierreDeSesion()
            }
        }.addOnFailureListener {
            Log.e(TAG, "❌ Error al acceder al usuario al cerrar sesión", it)
            finalizarCierreDeSesion()
        }
    }

    private fun finalizarCierreDeSesion() {
        isOnlineAlreadySet = false
        FirebaseAuth.getInstance().signOut()
        Log.d(TAG, "👋 Sesión cerrada correctamente.")
    }
}