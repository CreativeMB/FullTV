package com.creativem.fulltv.principal

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

object CastvHelper {

    private const val TAG = "CastvHelper"

    /**
     * 🔐 Codifica el correo para usarlo como clave en Realtime Database.
     * Reemplaza caracteres no permitidos (@ y .) por guiones bajos.
     */
    private fun codificarCorreo(correo: String): String {
        return correo.replace(".", "_").replace("@", "_")
    }

    /**
     * ✅ Gestiona el registro y la validación de presencia del usuario.
     */
    fun nuevosusuarios(
        context: Context,
        nombre: String,
        email: String?
    ) {
        if (email.isNullOrBlank()) {
            Log.e(TAG, "Correo electrónico nulo o vacío")
            return
        }

        val correoKey = codificarCorreo(email)
        val database = FirebaseDatabase.getInstance()
        val userRef = database.reference.child("usuarios").child(correoKey)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val estado = snapshot.child("estado").getValue(String::class.java)

                if (estado == "eliminado") {
                    manejarUsuarioEliminado(context)
                } else {
                    Log.d(TAG, "Usuario existente: Actualizando presencia")
                    configurarPresencia(userRef)
                }
            } else {
                crearNuevoUsuario(context, userRef, nombre, email)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error al acceder a la base de datos", e)
        }
    }

    /**
     * ✍️ Escribe un nuevo usuario en la base de datos por primera vez.
     */
    private fun crearNuevoUsuario(
        context: Context,
        userRef: DatabaseReference,
        nombre: String,
        email: String
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val fechaFormateada = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val user = mapOf(
            "nombre" to nombre,
            "correo" to email,
            "castv" to 250, // Créditos iniciales gratis
            "userId" to userId,
            "estado" to "activo",
            "enlinea" to true,
            "fechaCreacion" to fechaFormateada
        )

        userRef.setValue(user)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Usuario creado exitosamente")
                configurarPresencia(userRef)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error al crear nodo de usuario", e)
            }
    }
    /**
     * 💰 Registra el consumo de créditos de un usuario en Firebase
     */
    fun registrarConsumo(email: String, nombrePelicula: String, costo: Int) {
        val correoKey = codificarCorreo(email)
        val database = FirebaseDatabase.getInstance()

        // 1. Creamos una entrada única en la rama "historial_consumos"
        val consumoRef = database.reference.child("historial_consumos").push()

        val datosConsumo = mapOf(
            "usuarioCorreo" to email,
            "pelicula" to nombrePelicula,
            "creditosGastados" to costo,
            "timestamp" to ServerValue.TIMESTAMP
        )

        consumoRef.setValue(datosConsumo).addOnSuccessListener {
            Log.d(TAG, "Consumo registrado: $costo créditos en $nombrePelicula")
        }

        // 2. Opcional: Actualizamos también un total acumulado en el nodo del usuario
        // Esto facilita ver en la otra app cuánto ha gastado un usuario en total
        val userRef = database.reference.child("usuarios").child(correoKey)
        userRef.child("totalGastado").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val actual = mutableData.getValue(Int::class.java) ?: 0
                mutableData.value = actual + costo
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) Log.e(TAG, "Error actualizando totalGastado", error.toException())
            }
        })
    }
    /**
     * 🟢 Configura el sistema de presencia (Online/Offline) usando onDisconnect.
     */
    private fun configurarPresencia(userRef: DatabaseReference) {
        // Marcamos como conectado ahora
        userRef.child("enlinea").setValue(true)

        // Instrucciones para cuando el usuario pierda la conexión o cierre la app
        userRef.child("enlinea").onDisconnect().setValue(false)
        userRef.child("ultimaConexion").onDisconnect().setValue(ServerValue.TIMESTAMP)
    }

    /**
     * 🚫 Cierra la sesión y finaliza la actividad si el usuario está baneado/eliminado.
     */
    private fun manejarUsuarioEliminado(context: Context) {
        FirebaseAuth.getInstance().signOut()
        if (context is Activity) {
            context.runOnUiThread {
                Toast.makeText(context, "Tu cuenta ha sido inhabilitada.", Toast.LENGTH_LONG).show()
                context.finish()
            }
        }
    }

    /**
     * 📡 Escucha cambios en tiempo real de los datos del usuario.
     * @return ValueEventListener para que pueda ser removido en el onDestroy de la Activity.
     */
    fun obtenerDatosUsuario(
        email: String,
        onSuccess: (nombre: String, correo: String, castv: Int, enlinea: Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ): ValueEventListener {
        val correoKey = codificarCorreo(email)
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(correoKey)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "Los datos del usuario no existen en la ruta")
                    return
                }

                val nombre = snapshot.child("nombre").getValue(String::class.java) ?: "Usuario"
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
        return listener
    }
}