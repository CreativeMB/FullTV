package com.creativem.fulltv.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.creativem.fulltv.R
import com.creativem.fulltv.adapter.Main
import com.google.firebase.firestore.FirebaseFirestore
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        // Configurar Firebase Auth
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Configurar Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Este ID lo obtienes desde Firebase Console
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Verifica si hay un usuario autenticado
        if (auth.currentUser == null) {
            // Si no hay usuario autenticado, iniciar el flujo de inicio de sesión
            signInWithGoogle()
        } else {
            // Si ya hay un usuario, redirigir a MainActivity directamente
            val intent = Intent(this, Main::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun signInWithGoogle() {
        // Limpiar el caché de Google Sign-In para que el usuario pueda seleccionar la cuenta nuevamente
        googleSignInClient.signOut() // Esto elimina la cuenta de la sesión anterior
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
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Inicio de sesión exitoso, guardar o actualizar los puntos del usuario
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    user?.let {

                        updateUserPoints(it.uid, it.displayName ?: "", it.email ?: "")

                    }
                    val intent = Intent(this, Main::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Si falla, mostrar mensaje
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Autenticación fallida.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateUserPoints(userId: String, nombre: String, email: String?) {
        val userRef = firestore.collection("users").document(userId)

        // Guardar o actualizar el documento del usuario
        userRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Si el documento existe, actualizamos los puntos
                    userRef.update("puntos", document.getLong("puntos") ?: 0)
                        .addOnSuccessListener {
                            // Puntos actualizados correctamente
                        }
                } else {
                    // Si el documento no existe, lo creamos con puntos iniciales
                    val user = hashMapOf(
                        "nombre" to nombre,
                        "email" to email,
                        "puntos" to 10, // Valor inicial de puntos
                    )
                    userRef.set(user)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Usuario registrado con éxito.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error al obtener documento: ", e)
                Toast.makeText(this, "Error al obtener usuario.", Toast.LENGTH_SHORT).show()
            }
    }
}