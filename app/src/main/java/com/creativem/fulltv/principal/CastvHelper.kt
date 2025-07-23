package com.creativem.fulltv.principal

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.creativem.fulltv.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CastvHelper {

    private const val TAG = "CastvHelper"
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private fun codificarCorreo(correo: String): String {
        return correo
            .replace(".", "_")
            .replace("@", "_")
    }


    fun getCorreoKey(correo: String): String {
        return codificarCorreo(correo)
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
                    Log.d(TAG, "🔓 Usuario válido, ya registrado. Continuando...")

                    userRef.child("enlinea").onDisconnect().setValue(false)
                    userRef.child("ultimaConexion").setValue(obtenerFechaActual())
                }
            } else {
                // Solo creamos usuario si sabemos que es nuevo (o fue borrado)
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
                        Log.d(TAG, "✅ Usuario nuevo creado correctamente en Realtime DB.")
                        userRef.child("enlinea").onDisconnect().setValue(false)
                        userRef.child("ultimaConexion").setValue(obtenerFechaActual())


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

    fun existeUsuario(correo: String, callback: (Boolean) -> Unit) {
        val correoKey = getCorreoKey(correo)
        val ref = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)

        ref.get().addOnSuccessListener { snapshot ->
            callback(snapshot.exists())
        }.addOnFailureListener { e ->
            Log.e("CastvHelper", "Error al verificar existencia de usuario: ${e.message}")
            callback(false) // en caso de error, se asume que no existe
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
    fun verificarPuntos(
        context: Context,
        correo: String,
        costo: Int,
        callback: (Boolean) -> Unit
    ) {
        val correoKey = codificarCorreo(correo)
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)

        userRef.child("castv").get().addOnSuccessListener { snapshot ->
            val puntos = snapshot.getValue(Int::class.java) ?: 0
            callback(puntos >= costo)
        }.addOnFailureListener { e ->
            Log.e(TAG, "❌ Error al verificar puntos: ${e.message}")
            Toast.makeText(context, "Error al verificar puntos", Toast.LENGTH_SHORT).show()
            callback(false)
        }
    }
    fun descontarPuntos(
        context: Context,
        correo: String,
        puntosADescontar: Long,
        onSuccess: () -> Unit = {},
        onFailure: (Exception?) -> Unit = {}
    ) {
        val correoKey = codificarCorreo(correo)
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)

        userRef.get().addOnSuccessListener { snapshot ->
            val castvActual = snapshot.child("castv").getValue(Long::class.java) ?: 0

            if (castvActual >= puntosADescontar) {
                val nuevoCastv = castvActual - puntosADescontar
                userRef.child("castv").setValue(nuevoCastv)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Se descontaron $puntosADescontar CasTV", Toast.LENGTH_SHORT).show()
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Error al descontar puntos: ${e.message}")
                        Toast.makeText(context, "Error al descontar CasTV", Toast.LENGTH_SHORT).show()
                        onFailure(e)
                    }
            } else {
                Toast.makeText(context, "Saldo insuficiente de CasTV", Toast.LENGTH_LONG).show()
                onFailure(null)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "❌ Error al obtener usuario: ${e.message}")
            Toast.makeText(context, "Error al obtener usuario", Toast.LENGTH_SHORT).show()
            onFailure(e)
        }
    }
    private fun obtenerFechaActual(): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    }
    fun actualizarUltimaConexion(email: String) {
        val correoKey = codificarCorreo(email)
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)
        userRef.child("ultimaConexion").setValue(obtenerFechaActual())
    }


// -------------------------------
// Glide Helper Methods (CastvHelper)
// -------------------------------

    fun loadImage(context: Context, imageView: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.icono)
            return
        }

        Glide.with(context)
            .load(url)
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.icono)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Guarda original y transformada
            .into(imageView)
    }

    // ✅ Limpieza manual completa (por ejemplo, al cerrar sesión o forzar limpieza)
    fun limpiarCacheGlide(context: Context) {
        Thread {
            try {
                Glide.get(context).clearDiskCache()
                Log.d(TAG, "✅ Caché de disco de Glide limpiada.")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al limpiar caché de disco", e)
            }
        }.start()

        try {
            Glide.get(context).clearMemory()
            Log.d(TAG, "✅ Caché de memoria de Glide limpiada.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al limpiar caché de memoria", e)
        }
    }

    // ✅ Limpieza automática si el tamaño de caché supera 50MB
    fun limpiarCacheGlideSiEsNecesario(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "image_manager_disk_cache")
            if (!cacheDir.exists()) return

            val sizeMB = cacheDir.walkBottomUp()
                .filter { it.isFile }
                .map { it.length() }
                .sum() / (1024 * 1024)

            if (sizeMB >= 50) {
                limpiarCacheGlide(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al verificar caché de Glide", e)
        }
    }

    // ✅ Guardar hora de salida para saber cuándo el usuario dejó la app
    fun guardarHoraSalida(context: Context) {
        val prefs = context.getSharedPreferences("app_cache", Context.MODE_PRIVATE)
        prefs.edit().putLong("ultima_salida", System.currentTimeMillis()).apply()
    }

    // ✅ Verificar si ha pasado mucho tiempo desde la última salida y limpiar la caché
    fun verificarYLimpiarCachePorTiempo(context: Context) {
        val prefs = context.getSharedPreferences("app_cache", Context.MODE_PRIVATE)
        val ultimaSalida = prefs.getLong("ultima_salida", 0L)
        val ahora = System.currentTimeMillis()
        val LIMITE = 6 * 60 * 60 * 1000 // 6 horas

        if (ahora - ultimaSalida > LIMITE) {
            limpiarCacheGlide(context)
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun solicitarAudioFocus(context: Context): Boolean {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* puedes manejar cambios si lo deseas */ }
            .setWillPauseWhenDucked(true)
            .build()

        val result = audioManager.requestAudioFocus(focusRequest!!)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun liberarAudioFocus() {
        if (::audioManager.isInitialized && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        }
    }



}