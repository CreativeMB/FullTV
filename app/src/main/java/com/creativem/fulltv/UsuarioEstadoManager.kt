package com.creativem.fulltv

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object UsuarioEstadoManager : DefaultLifecycleObserver {

    private const val TAG = "UsuarioEstadoManager"
    private var conectadoListener: ValueEventListener? = null
    private var isOnlineAlreadySet = false

    fun iniciar() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        marcarUsuarioEnLinea(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        marcarUsuarioEnLinea(false)
    }

    private fun marcarUsuarioEnLinea(enLinea: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            val estado = snapshot.child("estado").getValue(String::class.java)
            if (!snapshot.exists() || estado == "eliminado") {
                Log.w(TAG, "Usuario no existe o está eliminado. No se actualiza enlinea.")
                return@addOnSuccessListener
            }

            // Marcar en línea
            if (enLinea) {
                if (isOnlineAlreadySet) return@addOnSuccessListener // evitar repetir
                isOnlineAlreadySet = true
                val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
                conectadoListener?.let { connectedRef.removeEventListener(it) }

                conectadoListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        if (connected) {
                            userRef.child("enlinea").setValue(true)
                            userRef.child("enlinea").onDisconnect().setValue(false)
                            Log.d(TAG, "Usuario marcado en línea.")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error al verificar conexión: ${error.message}")
                    }
                }
                connectedRef.addValueEventListener(conectadoListener!!)
            } else {
                isOnlineAlreadySet = false
             userRef.child("enlinea").setValue(false)
                Log.d(TAG, "Usuario marcado fuera de línea.")
            }

        }.addOnFailureListener {
            Log.e(TAG, "Error al verificar usuario: ${it.message}")
        }
    }
    // 🔴 Nueva función para limpiar el estado al cerrar sesión
    fun cerrarSesion() {
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(user.uid)

            // --- INICIO DE LA CORRECCIÓN ---
            // 1. ANTES de intentar escribir, comprueba si el nodo del usuario existe.
            userRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // 2. SI EXISTE: Procede a marcarlo como fuera de línea.
                    //    Esta es la operación segura.
                    userRef.child("enlinea").setValue(false).addOnCompleteListener {
                        // Este bloque se ejecuta DESPUÉS de que se ha intentado marcar como offline.
                        // Ahora podemos cerrar sesión de forma segura.
                        Log.d(TAG, "Cerrar sesión: usuario ${user.uid} marcado como 'false' en 'enlinea'.")
                        finalizarCierreDeSesion()
                    }
                } else {
                    // 3. SI NO EXISTE: Simplemente informa en el log y procede a cerrar sesión.
                    //    No se intenta escribir nada en la base de datos.
                    Log.w(TAG, "Cerrar sesión: El usuario ${user.uid} no existe en la DB. No se actualizó 'enlinea'.")
                    finalizarCierreDeSesion()
                }
            }.addOnFailureListener {
                // 4. MANEJO DE ERRORES: Si hubo un error al leer, igual procede a cerrar sesión.
                //    Es mejor dejar que el usuario cierre sesión a que se quede atascado.
                Log.e(TAG, "Cerrar sesión: Error al comprobar la existencia del usuario.", it)
                finalizarCierreDeSesion()
            }
            // --- FIN DE LA CORRECCIÓN ---

        } else {
            Log.w(TAG, "Se intentó cerrar sesión, pero no había ningún usuario activo.")
        }
    }
    private fun finalizarCierreDeSesion() {
        // 1. Elimina el listener de conexión, si es que existe.
        conectadoListener?.let {
            FirebaseDatabase.getInstance().getReference(".info/connected").removeEventListener(it)
            Log.d(TAG, "Listener de conexión de .info/connected eliminado.")
        }
        conectadoListener = null
        isOnlineAlreadySet = false

        // 2. Cierra la sesión de autenticación de Firebase.
        FirebaseAuth.getInstance().signOut()
        Log.d(TAG, "Sesión cerrada correctamente.")

    }

}
