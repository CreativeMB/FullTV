package com.creativem.fulltv.principal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PeliculasFragment
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
                val intent = Intent(this, Main::class.java)
                startActivity(intent)
                finish()
            } else {
                // El usuario está autenticado con Google u otro método
                val intent = Intent(this, Main::class.java)
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
                    // Inicio de sesión exitoso
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    user?.let {
                        nuevosusuarios(it.uid, it.displayName ?: "", it.email ?: "")
                    }
                    val intent = Intent(this, Main::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Fallo en el inicio de sesión
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
                    // Inicio de sesión como usuario predeterminado exitoso
                    Log.d(TAG, "signInAsDefaultUser:success")
                    Toast.makeText(this, "Ingresaste como invitado.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, Main::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Si falla, intenta crear el usuario predeterminado
                    auth.createUserWithEmailAndPassword(defaultEmail, defaultPassword)
                        .addOnCompleteListener { createTask ->
                            if (createTask.isSuccessful) {
                                Log.d(TAG, "Usuario invitado creado y autenticado con éxito.")
                                Toast.makeText(
                                    this,
                                    "Usuario invitado creado con éxito.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // 🟢 Agregar a Realtime Database con Castv = 10
                                val user = auth.currentUser
                                if (user != null) {
                                    val nombre = "Estas En Invitado"
                                    val email = user.email
                                    val userId = user.uid
                                    nuevosusuarios(userId, nombre, email)
                                }

                                val intent = Intent(this, Main::class.java)
                                startActivity(intent)
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

    private fun nuevosusuarios(userId: String, nombre: String, email: String?) {
        val user = mapOf(
            "nombre" to nombre,
            "correo" to email,
            "castv" to 10,
            "userId" to userId,
            "estado" to "activo",
            "enlinea" to false
        )

        val databaseRef = database.reference.child("usuarios").child(userId)

        databaseRef.setValue(user)
            .addOnSuccessListener {
                Log.d(TAG, "Usuario agregado a Realtime Database correctamente.")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error al agregar usuario a Realtime Database.", e)
                Toast.makeText(this, "Error al agregar usuario a la base de datos.", Toast.LENGTH_SHORT).show()
            }
    }



}

