package com.creativem.fulltv.principal

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PeliculasActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay

class Login : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    companion object {
        private const val TAG = "Login"
        val ColorDorado = Color(0xFFC5A059)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        val currentUser = auth.currentUser

        // Validación inicial de sesión
        if (currentUser != null) {
            if (currentUser.email == "invitado@fulltv.com") {
                auth.signOut()
            } else {
                irAPeliculas()
                return
            }
        }

        // Obtener cuentas de Google instaladas en el dispositivo/TV
        val cuentasInstaladas = obtenerCuentasGoogleDelTV(this)

        setContent {
            LoginScreen(
                cuentasGoogle = cuentasInstaladas,
                onIniciarGoogle = { emailSeleccionado, launcher ->
                    iniciarGoogleConCuenta(emailSeleccionado, launcher)
                },
                onIniciarInvitado = {
                    signInAsDefaultUser()
                }
            )
        }
    }

    private fun obtenerCuentasGoogleDelTV(context: Context): List<String> {
        return try {
            val accountManager = AccountManager.get(context)
            accountManager.getAccountsByType("com.google")
                .mapNotNull { it.name }
                .distinct()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudieron leer las cuentas del sistema: ${e.message}")
            emptyList()
        }
    }

    private fun iniciarGoogleConCuenta(
        emailEspecifico: String?,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()

        // Si seleccionó una cuenta instalada, la fijamos directo para no volver a preguntar
        if (!emailEspecifico.isNullOrBlank()) {
            gsoBuilder.setAccountName(emailEspecifico)
        }

        val googleClient = GoogleSignIn.getClient(this, gsoBuilder.build())
        googleClient.signOut().addOnCompleteListener {
            launcher.launch(googleClient.signInIntent)
        }
    }

    fun firebaseAuthWithGoogle(idToken: String, onFinalizar: () -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                val email = user?.email
                if (email.isNullOrBlank()) {
                    Toast.makeText(this, "Correo inválido", Toast.LENGTH_SHORT).show()
                    onFinalizar()
                    return@addOnCompleteListener
                }

                val correoKey = email.replace(".", "_").replace("@", "_")
                val userRef = database.reference.child("usuarios").child(correoKey)

                userRef.get().addOnSuccessListener { snapshot ->
                    val estado = snapshot.child("estado").getValue(String::class.java)

                    if (estado == "eliminado") {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(
                            this,
                            "Tu cuenta ha sido eliminada. No puedes volver a ingresar.",
                            Toast.LENGTH_LONG
                        ).show()
                        onFinalizar()
                        return@addOnSuccessListener
                    }

                    CastvHelper.nuevosusuarios(
                        context = this,
                        nombre = user.displayName ?: "Usuario",
                        email = email
                    )

                    irAPeliculas()
                }.addOnFailureListener {
                    Toast.makeText(this, "Error al validar tu cuenta.", Toast.LENGTH_SHORT).show()
                    onFinalizar()
                }
            } else {
                Toast.makeText(this, "Autenticación con Google fallida.", Toast.LENGTH_SHORT).show()
                onFinalizar()
            }
        }
    }

    private fun signInAsDefaultUser() {
        val defaultEmail = "invitado@fulltv.com"
        val defaultPassword = "nuevouser"
        val nombreInvitado = "Estas En Invitado"

        auth.signInWithEmailAndPassword(defaultEmail, defaultPassword)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
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

    private fun irAPeliculas() {
        val intent = Intent(this, PeliculasActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }
}

// =========================================================================
// 🟢 PANTALLA COMPOSABLE ULTRA LIGERA Y RESPONSIVA
// =========================================================================
@Composable
fun LoginScreen(
    cuentasGoogle: List<String>,
    onIniciarGoogle: (email: String?, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) -> Unit,
    onIniciarInvitado: () -> Unit
) {
    val activity = LocalConfiguration.current
    val esTv = activity.screenWidthDp > 600
    var estaCargando by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current as Login
    val firstItemFocusRequester = remember { FocusRequester() }

    // Launcher oficial para autenticación con Google
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrBlank()) {
                context.firebaseAuthWithGoogle(idToken) {
                    estaCargando = false
                }
            } else {
                estaCargando = false
                Toast.makeText(context, "No se pudo obtener el token de Google", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            estaCargando = false
            Toast.makeText(context, "Inicio de sesión cancelado o fallido.", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-foco en la primera cuenta para el control remoto
    LaunchedEffect(Unit) {
        delay(200)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Fondo plano oscuro de sala (0% lag en GPU)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C11)),
        contentAlignment = Alignment.Center
    ) {
        if (estaCargando) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Login.ColorDorado,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Accediendo a CineParche...",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // Cuadro central tipo diálogo TV/Móvil
            Column(
                modifier = Modifier
                    .width(if (esTv) 460.dp else (activity.screenWidthDp * 0.92f).dp)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF14161C))
                    .border(1.5.dp, Color(0xFF262A34), RoundedCornerShape(16.dp))
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "INICIAR SESIÓN",
                    color = Login.ColorDorado,
                    fontSize = if (esTv) 20.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (cuentasGoogle.isNotEmpty()) "Cuentas vinculadas a este televisor:" else "¿Cómo deseas ingresar hoy?",
                    color = Color(0xFFA0A5B1),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.wrapContentHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Mostrar las cuentas instaladas en el TV
                    itemsIndexed(cuentasGoogle) { index, email ->
                        ItemCuentaGoogleTV(
                            email = email,
                            modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                            onClick = {
                                estaCargando = true
                                onIniciarGoogle(email, googleSignInLauncher)
                            }
                        )
                    }

                    // 2. Opción de abrir selector general de Google (o agregar otra)
                    item {
                        ItemAccionLoginTV(
                            texto = if (cuentasGoogle.isNotEmpty()) "+ Usar otra cuenta de Google" else "Continuar con Google",
                            colorBorde = Login.ColorDorado,
                            modifier = if (cuentasGoogle.isEmpty()) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                            onClick = {
                                estaCargando = true
                                onIniciarGoogle(null, googleSignInLauncher)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            color = Color(0xFF262A34),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 3. Opción de Invitado fija al final
                    item {
                        ItemInvitadoTV(
                            onClick = {
                                estaCargando = true
                                onIniciarInvitado()
                            }
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🟢 COMPONENTES DE ITEMS CON ENFOQUE ALTO CONTRASTE (D-PAD TV)
// =========================================================================

@Composable
fun ItemCuentaGoogleTV(
    email: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.03f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF262C3A) else Color(0xFF1B1E26))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Login.ColorDorado else Color(0xFF262A34),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Inicial de usuario
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isFocused) Login.ColorDorado else Color(0xFF333845)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = email.take(1).uppercase(),
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = email,
                color = if (isFocused) Login.ColorDorado else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Cuenta de Google",
                color = Color(0xFF8E94A2),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ItemAccionLoginTV(
    texto: String,
    colorBorde: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.03f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF262C3A) else Color(0xFF1E2129))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) colorBorde else Color(0xFF2F3442),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = if (isFocused) Color.White else Login.ColorDorado,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ItemInvitadoTV(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.03f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF262626) else Color(0xFF191B20))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF2B2F3A),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isFocused) Color.White else Color(0xFF2F3442)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "👤",
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "Entrar como Invitado",
                color = if (isFocused) Color.White else Color(0xFFD4D8E2),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Acceso rápido sin registrar cuenta",
                color = Color(0xFF7F8695),
                fontSize = 11.sp
            )
        }
    }
}