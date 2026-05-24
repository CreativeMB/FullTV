package com.creativem.fulltv.principal

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.creativem.fulltv.peliculas.PeliculasActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.creativem.fulltv.R
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

import android.graphics.Typeface

import android.widget.TextView
class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var database: FirebaseDatabase


    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "Login"
    }

    // --- En Login.kt ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val currentUser = auth.currentUser

        // LÓGICA CORREGIDA:
        if (currentUser != null) {
            // Si el usuario es el invitado, cerramos sesión de inmediato y mostramos opciones
            if (currentUser.email == "invitado@fulltv.com") {
                auth.signOut()
                showLoginOptionsDialog()
            } else {
                // Si es un usuario real de Google, entra directo
                irAPeliculas()
            }
        } else {
            // No hay nadie logueado, mostrar opciones
            showLoginOptionsDialog()
        }
    }

    // Mantenemos tu función de navegación limpia
    private fun irAPeliculas() {
        val intent = Intent(this, PeliculasActivity::class.java).apply {
            // Estas flags son excelentes para limpiar el historial,
            // pero requieren una transición instantánea para no parpadear.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)

        // CAMBIO CLAVE: Cambia los fades por 0, 0
        overridePendingTransition(0, 0)

        finish()
    }
    /**
     * Muestra un Dialog moderno programado 100% en Kotlin, adaptable a TV y Celular.
     */
    private fun showLoginOptionsDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        dialog.setCancelable(false)

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        val dialogBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val displayMetrics = resources.displayMetrics
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthPercent = if (isLandscape) 0.45 else 0.85
            val paddingVal = if (isLandscape) 60 else 40
            setPadding(paddingVal, paddingVal, paddingVal, paddingVal)

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141414"))
                cornerRadius = 40f
                setStroke(2, Color.parseColor("#333333"))
            }

            layoutParams = FrameLayout.LayoutParams(
                (displayMetrics.widthPixels * widthPercent).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        val title = TextView(this).apply {
            text = "INICIAR SESIÓN"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        val message = TextView(this).apply {
            text = "¿Cómo deseas ingresar hoy?"
            textSize = 16f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        fun createTvButton(textStr: String, bgColor: String, fColor: String, onClick: () -> Unit): TextView {
            val btn = TextView(this)
            btn.text = textStr
            btn.textSize = 18f
            btn.setTextColor(Color.WHITE)
            btn.typeface = Typeface.DEFAULT_BOLD
            btn.gravity = Gravity.CENTER
            btn.isFocusable = true
            btn.isClickable = true
            btn.setPadding(0, 30, 0, 30)

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = 20f
            }
            btn.background = bg

            btn.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 10)
            }

            btn.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    bg.setColor(Color.parseColor(fColor))
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
                } else {
                    bg.setColor(Color.parseColor(bgColor))
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                }
            }

            btn.setOnClickListener {
                // AJUSTE 1: Evitamos doble clic accidental
                btn.isClickable = false
                dialog.dismiss()
                onClick()
            }
            return btn
        }

        val btnGoogle = createTvButton("Continuar con Google", "#E50914", "#FF3344") {
            signInWithGoogle()
        }

        val btnInvitado = createTvButton("Entrar como Invitado", "#2B2B2B", "#444444") {
            signInAsDefaultUser()
        }

        dialogBox.addView(title)
        dialogBox.addView(message)
        dialogBox.addView(btnGoogle)
        dialogBox.addView(btnInvitado)
        rootLayout.addView(dialogBox)

        dialog.setContentView(rootLayout)

        // AJUSTE 2: Solo damos foco a Google después de que el diálogo esté visible
        dialog.setOnShowListener {
            btnGoogle.requestFocus()
        }

        dialog.show()
    }
    private fun signInWithGoogle() {
        // Limpiar la sesión anterior de Google
        googleSignInClient.signOut()
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Error al iniciar sesión con Google.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val email = user.email
                        if (email.isNullOrBlank()) {
                            Toast.makeText(this, "Correo inválido", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }

                        val correoKey = email.replace(".", "_").replace("@", "_")
                        val userRef = database.reference.child("usuarios").child(correoKey)

                        userRef.get().addOnSuccessListener { snapshot ->
                            val estado = snapshot.child("estado").getValue(String::class.java)

                            if (estado == "eliminado") {
                                Log.w(TAG, "Cuenta eliminada detectada para $email. Cerrando sesión.")
                                FirebaseAuth.getInstance().signOut()

                                Toast.makeText(
                                    this,
                                    "Tu cuenta ha sido eliminada. No puedes volver a ingresar.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@addOnSuccessListener
                            }

                            // Crear el usuario si no existe
                            CastvHelper.nuevosusuarios(
                                context = this,
                                nombre = user.displayName ?: "Estas En Invitado",
                                email = email
                            )

                            // Continuar a la app
                            val intent = Intent(this, PeliculasActivity::class.java).apply {
                                // Limpia todas las actividades anteriores de la memoria del TV
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
// Transición suave para evitar el pantallazo negro en Android TV
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()

                        }.addOnFailureListener {
                            Log.e(TAG, "Error al verificar estado del usuario", it)
                            Toast.makeText(this, "Error al validar tu cuenta.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Autenticación fallida.", Toast.LENGTH_SHORT).show()
                }
            }
    }
    private fun signInAsDefaultUser() {
        val defaultEmail = "invitado@fulltv.com"
        val defaultPassword = "nuevouser"
        val nombreInvitado = "Estas En Invitado" // Definimos el nombre aquí

        auth.signInWithEmailAndPassword(defaultEmail, defaultPassword)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInAsDefaultUser:success")

                    // ✅ CORRECCIÓN: Llamamos a nuevosusuarios aunque ya exista la cuenta
                    // para asegurar que el nombre sea el correcto en la base de datos.
                    CastvHelper.nuevosusuarios(
                        context = this,
                        nombre = nombreInvitado,
                        email = defaultEmail
                    )

                    Toast.makeText(this, "Ingresaste como invitado.", Toast.LENGTH_SHORT).show()
                    irAPeliculas()
                } else {
                    auth.createUserWithEmailAndPassword(defaultEmail, defaultPassword)
                        .addOnCompleteListener { createTask ->
                            if (createTask.isSuccessful) {
                                // ✅ CORRECCIÓN: Al crear la cuenta por primera vez
                                CastvHelper.nuevosusuarios(
                                    context = this,
                                    nombre = nombreInvitado,
                                    email = defaultEmail
                                )
                                irAPeliculas()
                            } else {
                                Toast.makeText(this, "Error al acceder como invitado", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }
    }


}

