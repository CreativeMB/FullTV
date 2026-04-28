package com.creativem.fulltv.principal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.creativem.fulltv.PeliculasActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.creativem.fulltv.R
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firestore: FirebaseFirestore
    private lateinit var database: FirebaseDatabase


    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "Login"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        // Configurar Firebase Auth
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance()

        // Configurar Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // ID desde Firebase Console
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Verificar si el usuario ya está autenticado
        val currentUser = auth.currentUser

        if (currentUser == null) {
            // Si no hay usuario autenticado, mostrar el diálogo de opciones de inicio de sesión
            showLoginOptionsDialog()
        } else {
            // Si el usuario está autenticado, verificar si es el usuario invitado
            if (currentUser.email == "invitado@fulltv.com") {
                // El usuario ya está autenticado como invitado
                val intent = Intent(this, PeliculasActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // El usuario está autenticado con Google u otro método
                val intent = Intent(this, PeliculasActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

    }
    /**
     * Muestra un AlertDialog para que el usuario elija el método de inicio de sesión.
     */
    private fun showLoginOptionsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Selecciona una opción")
        builder.setMessage("¿Cómo deseas continuar?")
        builder.setPositiveButton("Iniciar sesión con Google") { _, _ ->
            signInWithGoogle()
        }
        builder.setNegativeButton("Continuar como invitado") { _, _ ->
            signInAsDefaultUser()
        }
        builder.setCancelable(false)
        builder.create().show()
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
                                nombre = user.displayName ?: "Usuario",
                                email = email
                            )

                            // Continuar a la app
                            startActivity(Intent(this, PeliculasActivity::class.java))
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

        auth.signInWithEmailAndPassword(defaultEmail, defaultPassword)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInAsDefaultUser:success")
                    Toast.makeText(this, "Ingresaste como invitado.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, Main::class.java))
                    finish()
                } else {
                    // Si falla el login, intentar crear la cuenta
                    auth.createUserWithEmailAndPassword(defaultEmail, defaultPassword)
                        .addOnCompleteListener { createTask ->
                            if (createTask.isSuccessful) {
                                Log.d(TAG, "Usuario invitado creado y autenticado con éxito.")
                                Toast.makeText(
                                    this,
                                    "Usuario invitado creado con éxito.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // ✅ Agregar a Realtime Database como invitado
                                val user = auth.currentUser
                                if (user != null) {
                                    CastvHelper.nuevosusuarios(
                                        context = this,
                                        nombre = "Estas En Invitado",
                                        email = defaultEmail
                                    )
                                }

                                startActivity(Intent(this, Main::class.java))
                                finish()
                            } else {
                                Log.w(TAG, "Error al crear usuario invitado.", createTask.exception)
                                Toast.makeText(
                                    this,
                                    "Error al crear usuario invitado.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                }
            }
    }



}

