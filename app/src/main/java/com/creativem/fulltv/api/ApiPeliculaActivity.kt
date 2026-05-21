package com.creativem.fulltv.api

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.peliculasvalidas.PelisCarteleraAdapter
import com.creativem.fulltv.peliculasvalidas.Validaciones
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.principal.Nosotros
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL

class ApiPeliculaActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private var isProcessingOrder = false
    private val validaciones = Validaciones()
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }

    private lateinit var ivPoster: ImageView
    private lateinit var tvTitulo: TextView
    private lateinit var tvFecha: TextView
    private lateinit var tvCalificacion: TextView
    private lateinit var tvSinopsis: TextView
    private lateinit var tvReproducir: TextView
    private lateinit var backgroundImageView: ImageView
    private lateinit var tvInfoAdicional: TextView
    private lateinit var recyclerActores: RecyclerView
    private lateinit var recyclerCartelera: RecyclerView
    private lateinit var carteleraAdapter: PelisCarteleraAdapter
    private lateinit var progressBar: ProgressBar

    private var progreso = 0
    private val progresoHandler = Handler(Looper.getMainLooper())
    private val progresoRunnable = object : Runnable {
        override fun run() {
            if (progreso < 95) {
                progreso += 1
                if (::progressBar.isInitialized) progressBar.progress = progreso
                progresoHandler.postDelayed(this, 100)
            }
        }
    }

    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"

    private var streamUrlGuardado = ""
    private var movieTitle = ""
    private var movieCastv: Int = 0
    private var movieImageUrl = ""
    private var movieCountdown = 0
    private var movieActual: Movie? = null
    private var movieReleaseDate: String = ""

    private var movieCreatedAt: Long = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_pelicula)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()

        ivPoster = findViewById(R.id.ivPoster)
        tvTitulo = findViewById(R.id.tvTitulo)
        tvFecha = findViewById(R.id.tvFecha)
        tvCalificacion = findViewById(R.id.tvCalificacion)
        tvSinopsis = findViewById(R.id.tvSinopsis)
        tvReproducir = findViewById(R.id.tvReproducir)
        tvInfoAdicional = findViewById(R.id.tvInfoAdicional)
        recyclerActores = findViewById(R.id.recyclerActores)
        backgroundImageView = findViewById(R.id.backgroundImageView)

        // Asumiendo que tienes un progressBar en tu layout. Si no, comenta las líneas del handler.
        // progressBar = findViewById(R.id.progressBar)

        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        apiService = retrofit.create(TMDbApiService::class.java)

        val movieOriginalTitle = intent.getStringExtra("EXTRA_ORIGINAL_TITLE") ?: ""
        streamUrlGuardado = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        movieTitle = intent.getStringExtra("EXTRA_MOVIE_TITLE") ?: ""
        movieCastv = intent.getIntExtra("EXTRA_MOVIE_CASTV", 0)
        movieImageUrl = intent.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""
        movieCountdown = intent.getIntExtra("EXTRA_COUNTDOWN", 0)
        movieCreatedAt = intent.getLongExtra("EXTRA_CREATED_AT", 0L)

        // CONFIGURACIÓN RECYCLER CARTELERA
        val layoutManagerCartelera = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        layoutManagerCartelera.isItemPrefetchEnabled = true
        recyclerCartelera = findViewById(R.id.peliscartelera)
        recyclerCartelera.setHasFixedSize(true)
        recyclerCartelera.layoutManager = layoutManagerCartelera
        recyclerCartelera.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        recyclerCartelera.nextFocusLeftId = R.id.peliscartelera
        recyclerCartelera.nextFocusRightId = R.id.peliscartelera
        recyclerCartelera.preserveFocusAfterLayout = true

        // --- BOTÓN REPRODUCIR ---
        tvReproducir.setOnClickListener {
            val urlActual = streamUrlGuardado
            val countdownActual = movieActual?.countdownMinutes ?: movieCountdown
            val createdAtOriginal = movieActual?.createdAt ?: movieCreatedAt
            val costoActual = movieActual?.castv ?: movieCastv

            // 🟢 ESCENARIO 4 (NUEVO): VIENE DEL CATÁLOGO API
            // Si el enlace es el de "tuservidor.com" o está en blanco, es una película
            // que aún no existe en tu base de datos. Pasa directo a Pedir (Alquilar).
            if (urlActual.contains("tuservidor.com") || urlActual.isBlank()) {
                manejarEnlaceRoto(costoActual)
                return@setOnClickListener // Corta aquí, no hace nada más.
            }

            // 🟢 SOLUCIÓN: Ajuste de Fechas (Milisegundos vs Segundos)
            val createdAtMillis = if (createdAtOriginal > 0 && createdAtOriginal < 1000000000000L) {
                createdAtOriginal * 1000
            } else {
                createdAtOriginal
            }

            // 🟢 MATEMÁTICA DEL CONTADOR
            var isCountdownActive = false

            if (countdownActual > 0) {
                if (createdAtMillis == 0L) {
                    isCountdownActive = true
                } else {
                    val countdownDurationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(countdownActual.toLong())
                    val timeElapsed = System.currentTimeMillis() - createdAtMillis
                    val remainingTimeMillis = countdownDurationMillis - timeElapsed

                    if (remainingTimeMillis > 0) {
                        isCountdownActive = true
                    }
                }
            }

            // ESCENARIO 3: Viene de un contador ACTIVO -> Reproduce directo sin cobrar
            if (isCountdownActive) {
                if (urlActual.isNotBlank()) {
                    irAlReproductorDirecto()
                } else {
                    Toast.makeText(this, "Enlace de cuenta regresiva no disponible", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            // ESCENARIO 1 y 2: Preparar UI para cobrar/validar
            tvReproducir.isEnabled = false
            val textoOriginal = tvReproducir.text
            tvReproducir.text = "Procesando Datos..."

            CoroutineScope(Dispatchers.Main).launch {
                val enlaceValido = withContext(Dispatchers.IO) {
                    validaciones.isUrlValid(urlActual)
                }

                if (enlaceValido) {
                    // ESCENARIO 1: El enlace sirve -> Mostrar AlertDialog de Confirmación
                    procesarEnlaceBueno(costoActual)
                } else {
                    // ESCENARIO 2: El enlace está roto -> Mostrar AlertDialog para pedir la película
                    manejarEnlaceRoto(costoActual)
                }

                tvReproducir.isEnabled = true
                tvReproducir.text = textoOriginal
            }
        }

        tvReproducir.isFocusableInTouchMode = true
        tvReproducir.requestFocus()
        tvReproducir.setOnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.05f else 1f
            v.scaleY = if (hasFocus) 1.05f else 1f
        }

        cargarCartelera()
        buscarPelicula(movieOriginalTitle.ifBlank { movieTitle })
    }
    // --- FUNCIÓN PARA ACTIVAR EL CONTADOR DE 300 MINUTOS ---
    private fun activarContadorFirebase(tituloPelicula: String) {
        val query = databaseRef.child("movies").orderByChild("title").equalTo(tituloPelicula)

        query.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    // Actualizamos para que el contador inicie AHORA MISMO con 300 minutos (5 horas)
                    val updates = mapOf<String, Any>(
                        "countdownMinutes" to 300,
                        "createdAt" to System.currentTimeMillis()
                    )
                    child.ref.updateChildren(updates).addOnSuccessListener {
                        Log.d("ALQUILER", "Contador de 300 minutos activado para: $tituloPelicula")
                    }
                }
            }
        }.addOnFailureListener {
            Log.e("ALQUILER", "Error activando contador: ${it.message}")
        }
    }
    // --- ESCENARIO 1: ENLACE BUENO ---
    // --- ESCENARIO 1: ENLACE BUENO (AHORA CON CONFIRMACIÓN) ---
    private fun procesarEnlaceBueno(costo: Int) {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "Debes iniciar sesión para reproducir", Toast.LENGTH_SHORT).show()
            return
        }

        val correoKey = user.email!!.replace(".", "_").replace("@", "_")

        // En lugar de cobrar directo, mostramos la alerta de confirmación
        showConfirmPurchaseDialog(costo, correoKey)
    }

    // --- DIALOGO DE CONFIRMACIÓN DE ALQUILER (ENLACE BUENO) ---
    // --- DIALOGO DE CONFIRMACIÓN DE ALQUILER (ENLACE BUENO) ---
    private fun showConfirmPurchaseDialog(costo: Int, correoKey: String) {
        val tituloUsado = movieActual?.title ?: movieTitle
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val builder = AlertDialog.Builder(this)

        // 1. TÍTULO PERSONALIZADO (Fuerza tu diseño sin importar la TV)
        val customTitle = TextView(this).apply {
            text = "🎬 Confirmar Alquiler"
            setTextColor(colorDorado)
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setPadding(40, 50, 40, 20)
            gravity = android.view.Gravity.CENTER
        }
        builder.setCustomTitle(customTitle)

        // 2. MENSAJE MEJORADO CON COLORES (Spannable)
        val spannable = SpannableStringBuilder()
        spannable.append("Película: ")
        val startPelicula = spannable.length
        spannable.append("$tituloUsado\n\n")
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), startPelicula, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(android.text.style.StyleSpan(Typeface.BOLD), startPelicula, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        spannable.append("Costo: ")
        val startCosto = spannable.length
        spannable.append("$costo CasTV\n\n")
        // Verde llamativo para resaltar el precio
        spannable.setSpan(ForegroundColorSpan(Color.parseColor("#00E676")), startCosto, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(android.text.style.StyleSpan(Typeface.BOLD), startCosto, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), startCosto, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        spannable.append("El enlace está disponible en alta calidad.\n¿Deseas alquilar y ver la película ahora?")

        builder.setMessage(spannable)
        builder.setCancelable(true)

        builder.setPositiveButton("Ver Ahora", null)
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()

        // Estilo oscuro para mantener el diseño de la app
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))

        dialog.setOnShowListener {
            val btnVerAhora = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancelar = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Colores de los botones
            btnVerAhora.setTextColor(colorDorado)
            btnVerAhora.setTypeface(Typeface.DEFAULT_BOLD)
            btnCancelar.setTextColor(Color.WHITE)

            // Selector para Android TV y Padding extra para que se vean más como botones
            val focusSelector = R.drawable.focus_selector
            listOf(btnVerAhora, btnCancelar).forEach { button ->
                button.setBackgroundResource(focusSelector)
                button.isFocusable = true
                button.isFocusableInTouchMode = true
                button.setPadding(40, 20, 40, 20)
            }

            // 🟢 PREVENIR EFECTO REBOTE EN TV (Desactivamos temporalmente)
            btnVerAhora.isEnabled = false
            btnCancelar.isEnabled = false

            // Acción del botón "Ver Ahora"
            btnVerAhora.setOnClickListener {
                btnVerAhora.isEnabled = false
                btnVerAhora.text = "Procesando..."

                verificarPuntos(correoKey, costo) { tienePuntos ->
                    if (tienePuntos) {
                        descontarPuntos(correoKey, costo) { exito ->
                            if (exito) {
                                // 🟢 ACTIVAMOS EL CONTADOR AL COBRAR
                                val tituloMovie = movieActual?.title ?: movieTitle
                                activarContadorFirebase(tituloMovie)

                                dialog.dismiss()
                                Toast.makeText(this@ApiPeliculaActivity, "¡Película activada por 5 horas!", Toast.LENGTH_LONG).show()

                                irAlReproductorDirecto()
                            } else {
                                btnVerAhora.isEnabled = true
                                btnVerAhora.text = "Ver Ahora"
                                Toast.makeText(this@ApiPeliculaActivity, "Error procesando el pago", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        btnVerAhora.isEnabled = true
                        btnVerAhora.text = "Ver Ahora"
                        Toast.makeText(this@ApiPeliculaActivity, "Saldo CasTV insuficiente.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // Habilitar botones tras medio segundo y dar foco (Solución Anti-Rebote doble clic)
            Handler(Looper.getMainLooper()).postDelayed({
                btnVerAhora.isEnabled = true
                btnCancelar.isEnabled = true
                btnVerAhora.requestFocus()
            }, 500)

            // Asegurar que el mensaje principal se vea en color claro, más grande y centrado
            val tvMessage = dialog.findViewById<TextView>(android.R.id.message)
            tvMessage?.apply {
                setTextColor(Color.parseColor("#E0E0E0")) // Blanco suave elegante
                textSize = 17f
                setLineSpacing(0f, 1.2f)
                gravity = android.view.Gravity.CENTER
            }
        }

        dialog.show()

        // Ajustamos el tamaño del texto y colores del título y mensaje por código para el AlertDialog genérico
        val textViewId = dialog.context.resources.getIdentifier("android:id/message", null, null)
        val titleViewId = dialog.context.resources.getIdentifier("android:id/alertTitle", null, null)
        dialog.findViewById<TextView>(textViewId)?.apply {
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        dialog.findViewById<TextView>(titleViewId)?.apply {
            setTextColor(colorDorado)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
        }
    }

    // --- ESCENARIO 2: ENLACE ROTO ---
    private fun manejarEnlaceRoto(costo: Int) {
        val correoUsuario = auth.currentUser?.email ?: ""
        val tituloUsado = movieActual?.title ?: movieTitle
        showErrorDialog(tituloUsado, costo, correoUsuario)
    }

    // --- DIALOGO DE ENLACE ROTO Y PEDIDO ---
    @SuppressLint("SetTextI18n")
    private fun showErrorDialog(movieTitle: String, movieCastv: Int, correoUsuario: String) {
        val dialogView = layoutInflater.inflate(R.layout.player_alerdialogo, null)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val linkNosotros = dialogView.findViewById<TextView>(R.id.linkNosotros)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogImage)
        imageView.setImageResource(R.drawable.canal)

        val spannable = SpannableStringBuilder()
        val movieInfo = "Película: $movieTitle\n"
        spannable.append(movieInfo)
        val peliculaTexto = "Película:"
        val peliculaIndex = spannable.indexOf(peliculaTexto)
        spannable.setSpan(ForegroundColorSpan(Color.RED), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val tituloIndex = peliculaIndex + peliculaTexto.length + 1
        spannable.setSpan(ForegroundColorSpan(Color.GREEN), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.4f), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val precioInfo = "Precio CasTV: $$movieCastv\n"
        spannable.append(precioInfo)
        val precioTexto = "Precio CasTV:"
        val precioIndex = spannable.indexOf(precioTexto)
        spannable.setSpan(ForegroundColorSpan(Color.RED), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val precioValorIndex = precioIndex + precioTexto.length + 2
        spannable.setSpan(ForegroundColorSpan(Color.GREEN), precioValorIndex, precioValorIndex + movieCastv.toString().length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.4f), precioValorIndex, precioValorIndex + movieCastv.toString().length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Línea temporal mientras se obtiene usuario y saldo
        spannable.append("\nUsuario: Consultando...\n")
        spannable.append("Saldo actual: Consultando...\n")
        spannable.append("\nℹ️ INFORMACIÓN IMPORTANTE") // Añadir un ícono ayuda visualmente
        spannable.append("\nAl enviar su solicitud, el contenido será procesado por nuestro equipo de moderación.")
        spannable.append("\nEl tiempo estimado de gestión es de algunas horas; le notificaremos a través de la plataforma en cuanto esté disponible.")
        spannable.append("\n\n⚠️ RESTRICCIONES")
        spannable.append("\nSi la película tiene menos de un mes de estreno, no podrá ser puesta en línea. En ese caso, el valor será reembolsado automáticamente como crédito en CasTV.")
        spannable.append("\n\nRecuerda mantener saldo en tu cuenta CasTV para disfrutar de tus próximos alquileres.")

        messageText.text = spannable

        // Asegúrate de tener la clase CastvHelper importada si usas esto

        if (correoUsuario.isNotEmpty()) {
            CastvHelper.obtenerDatosUsuario(
                correoUsuario,
                onSuccess = { nombre, _, castv, _ ->
                    val usuarioIndex = spannable.indexOf("Usuario: Consultando...")
                    if (usuarioIndex != -1) {
                        spannable.replace(usuarioIndex, usuarioIndex + "Usuario: Consultando...".length, "Usuario: $nombre")
                    }
                    val saldoIndex = spannable.indexOf("Saldo actual: Consultando...")
                    if (saldoIndex != -1) {
                        spannable.replace(saldoIndex, saldoIndex + "Saldo actual: Consultando...".length, "Saldo actual: $castv CasTV")
                    }
                    messageText.text = spannable
                },
                onFailure = { Log.e("CastvHelper", "❌ Error obteniendo datos del usuario") }
            )
        }


        linkNosotros.text = "Más información aquí"
        linkNosotros.setTextColor(Color.RED)
        linkNosotros.paintFlags = linkNosotros.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        linkNosotros.setOnClickListener {
            startActivity(Intent(this, Nosotros::class.java))
        }

        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton("Volver al contenido") { dialog, _ ->
                dialog.dismiss()
                // No llamamos a finish() si queremos que siga en la película,
                // o si prefieres sacarlo, déjalo como finish()
            }
            .setNeutralButton("Alquilar Película", null)
            .create()

        alertDialog.setCanceledOnTouchOutside(false)
        dialogView.setBackgroundColor(colorFondo)

        alertDialog.setOnShowListener {
            val btnAlquilar = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val btnVolver = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            btnAlquilar.setTextColor(colorDorado)
            btnAlquilar.setTypeface(Typeface.DEFAULT_BOLD)
            btnVolver.setTextColor(colorDorado)

            val focusSelector = R.drawable.focus_selector
            listOf(btnAlquilar, btnVolver).forEach { button ->
                button.setBackgroundResource(focusSelector)
                button.isFocusable = true
                button.isFocusableInTouchMode = true
                button.setPadding(24, 12, 24, 12)
            }
            (btnAlquilar.parent as? View)?.setBackgroundColor(colorFondo)

            // ⚠️ ACÁ CONECTAMOS EL BOTÓN CON LA LÓGICA DE PEDIDO ⚠️
            btnAlquilar.setOnClickListener {
                verificarYProcesarPedido(alertDialog)
            }
            btnAlquilar.requestFocus()
        }
        alertDialog.show()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))
    }

    // --- LÓGICA DE VERIFICACIÓN Y ENVÍO DE PEDIDOS ---
    private fun verificarYProcesarPedido(dialog: AlertDialog) {
        val query = databaseRef.child("pedidosmovies")
            .orderByChild("title")
            .equalTo(movieTitle)

        query.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                enviarPedido(dialog)
            } else {
                Toast.makeText(this, "Esta película ya fue pedida, estamos trabajando en ella.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }.addOnFailureListener { e ->
            Log.e("ALQUILER_LOG", "ERROR en consulta de pedidos: ${e.message}")
        }
    }

    private fun enviarPedido(dialog: AlertDialog) {
        if (isProcessingOrder) return
        isProcessingOrder = true

        val user = auth.currentUser
        if (user != null && user.email != null) {
            val correoKey = user.email!!.replace(".", "_").replace("@", "_")

            databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val userName = snapshot.child("nombre").value?.toString() ?: "Sin nombre"
                    val userEmail = snapshot.child("correo").value?.toString() ?: user.email!!
                    val costoPedido = movieActual?.castv ?: movieCastv // Usamos el costo actualizado

                    verificarPuntos(correoKey, costoPedido) { tienePuntos ->
                        if (tienePuntos) {
                            val datos = hashMapOf(
                                "title" to movieTitle,
                                "castv" to costoPedido,
                                "email" to userEmail,
                                "nombre" to userName,
                                "userId" to snapshot.child("userId").value?.toString(),
                                "timestamp" to ServerValue.TIMESTAMP
                            )

                            databaseRef.child("pedidosmovies").push().setValue(datos)
                                .addOnSuccessListener {
                                    dialog.dismiss()
                                    descontarPuntos(correoKey, costoPedido)
                                    Toast.makeText(this, "Pedido enviado. Puntos descontados.", Toast.LENGTH_LONG).show()
                                    isProcessingOrder = false
                                }
                                .addOnFailureListener { e ->
                                    isProcessingOrder = false
                                    Toast.makeText(this, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            isProcessingOrder = false
                            Toast.makeText(this, "Saldo CasTV insuficiente.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    isProcessingOrder = false
                }
            }.addOnFailureListener { isProcessingOrder = false }
        }
    }

    private fun verificarPuntos(correoKey: String, costo: Int, callback: (Boolean) -> Unit) {
        val userRef = databaseRef.child("usuarios").child(correoKey)
        userRef.child("castv").get().addOnSuccessListener { snapshot ->
            val puntosActuales = (snapshot.value as? Number)?.toInt() ?: 0
            callback(puntosActuales >= costo)
        }.addOnFailureListener {
            callback(false)
        }
    }

    private fun descontarPuntos(correoKey: String, costo: Int, onComplete: ((Boolean) -> Unit)? = null) {
        val userRef = databaseRef.child("usuarios").child(correoKey)
        userRef.child("castv").get().addOnSuccessListener { snapshot ->
            val puntosActuales = (snapshot.value as? Number)?.toInt() ?: 0
            if (puntosActuales >= costo) {
                val nuevosPuntos = puntosActuales - costo
                userRef.child("castv").setValue(nuevosPuntos)
                    .addOnSuccessListener { onComplete?.invoke(true) }
                    .addOnFailureListener { onComplete?.invoke(false) }
            } else {
                onComplete?.invoke(false)
            }
        }.addOnFailureListener { onComplete?.invoke(false) }
    }

    private fun irAlReproductorDirecto() {
        val tituloConFecha = if (movieActual != null) {
            val fecha = movieActual?.releaseDate ?: movieReleaseDate
            "${movieActual?.title} $fecha"
        } else {
            "$movieTitle $movieReleaseDate"
        }

        val intent = Intent(this, PlayerPeliculas::class.java).apply {
            putExtra("EXTRA_STREAM_URL", streamUrlGuardado)
            putExtra("EXTRA_MOVIE_TITLE", tituloConFecha)
            putExtra("EXTRA_MOVIE_CASTV", movieActual?.castv ?: movieCastv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", movieActual?.imageUrl ?: movieImageUrl)
            putExtra("EXTRA_COUNTDOWN", movieActual?.countdownMinutes ?: movieCountdown)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    // --- CARGA DE UI Y API ---
    private fun cargarCartelera() {
        val pelisMostradas = mutableListOf<Movie>()

        carteleraAdapter = PelisCarteleraAdapter(pelisMostradas) { movieSeleccionado ->
            actualizarPeliculaSeleccionada(movieSeleccionado)
        }
        recyclerCartelera.adapter = carteleraAdapter

        CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val listaActualDelObjeto = Validacioneslista.obtenerPeliculasValidas()

                if (listaActualDelObjeto.size > pelisMostradas.size) {
                    pelisMostradas.clear()
                    pelisMostradas.addAll(listaActualDelObjeto)
                    carteleraAdapter.notifyDataSetChanged()
                }

                if (Validacioneslista.yaCargado()) break
                delay(500)
            }
        }
    }

    private fun actualizarPeliculaSeleccionada(movieSeleccionado: Movie) {
        tvTitulo.text = movieSeleccionado.title
        tvSinopsis.text = "Cargando información detallada..."
        tvInfoAdicional.text = "Obteniendo géneros y duración..."
        recyclerActores.adapter = null

        Glide.with(this).load(movieSeleccionado.imageUrl).placeholder(ivPoster.drawable).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(ivPoster)
        Glide.with(this).load(movieSeleccionado.imageUrl).centerCrop().transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade()).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(backgroundImageView)

        // 🟢 ACTUALIZACIÓN CRÍTICA DE VARIABLES GLOBALES
        streamUrlGuardado = movieSeleccionado.streamUrl
        movieTitle = movieSeleccionado.title
        movieImageUrl = movieSeleccionado.imageUrl
        movieCastv = movieSeleccionado.castv
        movieCountdown = movieSeleccionado.countdownMinutes
        movieCreatedAt = movieSeleccionado.createdAt
        movieActual = movieSeleccionado

        val consulta = movieSeleccionado.originalTitle ?: movieSeleccionado.title
        buscarPelicula(consulta)

        tvReproducir.requestFocus()
    }

    private fun buscarPelicula(query: String) {
        apiService.searchMovie(apiKey, "es-MX", query)
            .enqueue(object : Callback<MovieResponse> {
                override fun onResponse(call: Call<MovieResponse>, response: Response<MovieResponse>) {
                    if (response.isSuccessful) {
                        response.body()?.results?.firstOrNull()?.let {
                            mostrarPelicula(it)
                        } ?: mostrarContenidoLocal()
                    } else {
                        mostrarContenidoLocal()
                    }
                }

                override fun onFailure(call: Call<MovieResponse>, t: Throwable) {
                    mostrarContenidoLocal()
                }
            })
    }

    private fun mostrarPelicula(movie: TmdbMovie) {
        tvTitulo.text = movie.title
        tvFecha.text = "\uD83D\uDDD3 ${movie.release_date ?: "N/A"}"
        tvCalificacion.text = "⭐ ${movie.vote_average ?: "N/A"} "
        tvSinopsis.text = movie.overview ?: "Sin sinopsis disponible"

        val posterUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
        val backdropUrl = "https://image.tmdb.org/t/p/w780${movie.poster_path}"

        Glide.with(this).load(posterUrl).placeholder(ivPoster.drawable).into(ivPoster)
        Glide.with(this).load(backdropUrl).centerCrop().diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(800)).into(backgroundImageView)

        tvInfoAdicional.text = ""
        recyclerActores.adapter = null

        apiService.getMovieDetails(movie.id, apiKey, "es-MX").enqueue(object : Callback<MovieDetailResponse> {
            override fun onResponse(call: Call<MovieDetailResponse>, response: Response<MovieDetailResponse>) {
                if (response.isSuccessful) {
                    val detalles = response.body()
                    val generos = detalles?.genres?.joinToString(", ") { it.name } ?: "Desconocidos"
                    val duracion = detalles?.runtime ?: 0
                    tvInfoAdicional.text = "🎭 $generos ⏱ ${duracion} Min "
                }
            }
            override fun onFailure(call: Call<MovieDetailResponse>, t: Throwable) {
                tvInfoAdicional.text = "No se pudieron obtener detalles"
            }
        })

        apiService.getCredits(movie.id, apiKey).enqueue(object : Callback<CreditsResponse> {
            override fun onResponse(call: Call<CreditsResponse>, response: Response<CreditsResponse>) {
                if (response.isSuccessful) {
                    val creditos = response.body()
                    val director = creditos?.crew?.find { it.job == "Director" }?.name ?: "N/D"
                    tvInfoAdicional.append("Director: $director")

                    val actores = creditos?.cast?.take(6)
                    if (!actores.isNullOrEmpty()) {
                        recyclerActores.layoutManager = LinearLayoutManager(this@ApiPeliculaActivity, LinearLayoutManager.HORIZONTAL, false)
                        recyclerActores.adapter = ActoresAdapter(actores)
                    }
                }
            }
            override fun onFailure(call: Call<CreditsResponse>, t: Throwable) {}
        })
    }

    private fun mostrarContenidoLocal() {
        val movie = movieActual ?: return
        val url = movie.imageUrl

        tvTitulo.text = movie.title
        tvFecha.text = "Verificada ✅"
        tvCalificacion.text = ""
        tvSinopsis.text = "Cargando información..."

        Glide.with(this).load(url).dontAnimate().diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(ivPoster)
        Glide.with(this).load(url).centerCrop().diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade()).into(backgroundImageView)
    }
}