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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PeliculasActivity
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.peliculasvalidas.PelisCarteleraAdapter
import com.creativem.fulltv.peliculasvalidas.Validaciones
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.CineAlert
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.Perfil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

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
    private var modeloActual: Modelo? = null
    private var movieReleaseDate: String = ""
    private var movieOriginalTitle = ""
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

        movieOriginalTitle = intent.getStringExtra("EXTRA_ORIGINAL_TITLE") ?: ""
        modeloActual = intent.getParcelableExtra<Modelo>("EXTRA_MOVIE_DATA")
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

        // --- BOTÓN REPRODUCIR (CON DETECTOR DE PROMOCIÓN GLOBAL) ---
        tvReproducir.setOnClickListener {
            val urlActual = streamUrlGuardado
            val costoActual = modeloActual?.castv ?: movieCastv
            val user = auth.currentUser

            // 1. Verificación inicial de enlace roto
            if (urlActual.contains("tuservidor.com") || urlActual.isBlank()) {
                manejarEnlaceRoto(costoActual)
                return@setOnClickListener
            }

            // 🟢 DETECTOR DE PROMOCIONES / CONTADORES GLOBALES (PANTALLA PRINCIPAL)
            val countdownGlobal = modeloActual?.countdownMinutes ?: movieCountdown
            val createdAtGlobal = modeloActual?.createdAt ?: movieCreatedAt

            val createdAtMillis = if (createdAtGlobal > 0 && createdAtGlobal < 1000000000000L) {
                createdAtGlobal * 1000
            } else {
                createdAtGlobal
            }

            var isPromoGlobalActiva = false

            if (countdownGlobal > 0) {
                if (createdAtMillis == 0L) {
                    isPromoGlobalActiva = true
                } else {
                    val countdownDurationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(countdownGlobal.toLong())
                    val timeElapsed = System.currentTimeMillis() - createdAtMillis
                    val remainingTimeMillis = countdownDurationMillis - timeElapsed

                    if (remainingTimeMillis > 0) {
                        isPromoGlobalActiva = true
                    }
                }
            }

            // 🟢 ESCENARIO PRIORITARIO: Si la película tiene promoción global, reproduce de inmediato
            if (isPromoGlobalActiva) {
                irAlReproductorDirecto()
                return@setOnClickListener // Detiene el código aquí (sin perfiles ni cobros)
            }

            // 2. Verificación de usuario (solo se requiere si NO es una película promocional/gratuita)
            if (user == null || user.email == null) {
                CineAlert.show(this, "Debes iniciar sesión para reproducir", CineAlert.Tipo.ERROR)
                return@setOnClickListener
            }

            val correoKey = user.email!!.replace(".", "_").replace("@", "_")
            val tituloMovie = modeloActual?.title ?: movieTitle

            // Bloqueamos el botón y cambiamos el estado visual para el flujo normal de compra/alquiler
            tvReproducir.isEnabled = false
            val textoOriginal = tvReproducir.text
            tvReproducir.text = "Verificando..."

            // Iniciamos la corrutina en el hilo principal para el flujo de validación y renta individual
            CoroutineScope(Dispatchers.Main).launch {

                // Verificamos si este usuario tiene un alquiler activo personal (en segundo plano)
                val isAlquilerActivo = withContext(Dispatchers.IO) {
                    verificarAlquilerVigenteSincrono(correoKey, tituloMovie)
                }

                if (isAlquilerActivo) {
                    // ESCENARIO 3: El usuario ya tiene un alquiler activo personal -> Reproduce directo
                    tvReproducir.isEnabled = true
                    tvReproducir.text = textoOriginal
                    irAlReproductorDirecto()
                } else {
                    // El usuario no tiene alquiler activo -> Procedemos a validar el enlace para iniciar cobro
                    tvReproducir.text = "Procesando Datos..."

                    val enlaceValido = withContext(Dispatchers.IO) {
                        validaciones.isUrlValid(urlActual)
                    }

                    if (enlaceValido) {
                        // ESCENARIO 1: El enlace sirve -> Mostrar AlertDialog de Confirmación de Compra
                        procesarEnlaceBueno(costoActual)
                    } else {

                        manejarEnlaceRoto(costoActual)
                    }

                    tvReproducir.isEnabled = true
                    tvReproducir.text = textoOriginal
                }
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

    private fun verificarYCrearPeliculaRota(
        tituloMovie: String,
        originalTitleMovie: String,
        imageUrlMovie: String,
        urlRota: String,
        anio: String
    ) {
        if (isFinishing || isDestroyed) return

        // 🟢 CORREGIDO: URL exacta de tu Realtime Database y nodo "movies"
        val customDbUrl = "https://corario-16991-default-rtdb.firebaseio.com/"
        val databaseRef = com.google.firebase.database.FirebaseDatabase
            .getInstance(customDbUrl)
            .getReference("movies")

        // 🟢 Si el título original está vacío, no podemos buscar ni guardar correctamente
        if (originalTitleMovie.isBlank()) {
            android.util.Log.w("FirebaseTV", "⚠️ No se puede registrar película rota: título original vacío.")
            return
        }

        // 🟢 Busca rigurosamente por el campo "originalTitle" para evitar duplicados
        databaseRef.orderByChild("originalTitle").equalTo(originalTitleMovie)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (isFinishing || isDestroyed) return

                    if (snapshot.exists()) {
                        android.util.Log.d("FirebaseTV", "✅ La película '$originalTitleMovie' YA existe en 'movies'. Omitiendo guardado.")
                        return
                    }

                    // 🟢 NO EXISTE -> Procedemos a GUARDARLA
                    val newId = databaseRef.push().key ?: return
                    val nombreFormateado = "$tituloMovie ($anio)".trim()

                    // 🟢 Estructura EXACTA igual a tus películas actuales en Firebase
                    val nuevaPeliculaMap = hashMapOf(
                        "id" to newId,
                        "title" to tituloMovie,
                        "originalTitle" to originalTitleMovie,
                        "nombre" to nombreFormateado,
                        "imageUrl" to imageUrlMovie,
                        "streamUrl" to urlRota,
                        "castv" to 10,
                        "countdownMinutes" to 0,
                        "createdAt" to System.currentTimeMillis(),
                        "email" to "",
                        "trailerUrl" to "",
                        "userId" to ""
                    )

                    // Subida a Firebase
                    databaseRef.child(newId).setValue(nuevaPeliculaMap)
                        .addOnSuccessListener {
                            if (isFinishing || isDestroyed) return@addOnSuccessListener
                            android.util.Log.d("FirebaseTV", "🟢 Película rota GUARDADA exitosamente en 'movies': $newId")
                        }
                        .addOnFailureListener { e ->
                            if (isFinishing || isDestroyed) return@addOnFailureListener
                            android.util.Log.e("FirebaseTV", "❌ Error al guardar en Firebase", e)
                        }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    android.util.Log.e("FirebaseTV", "Error de base de datos: ${error.message}")
                }
            })
    }

    private suspend fun verificarAlquilerVigenteSincrono(correoKey: String, tituloPelicula: String): Boolean =
        kotlin.coroutines.suspendCoroutine { continuation ->
            // Limpiamos caracteres que no se permiten en claves de Firebase
            val peliculaKey = tituloPelicula.replace(".", "_")
                .replace("$", "_")
                .replace("#", "_")
                .replace("[", "_")
                .replace("]", "_")

            databaseRef.child("usuarios")
                .child(correoKey)
                .child("alquileres")
                .child(peliculaKey)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val createdAt = snapshot.child("createdAt").value as? Long ?: 0L
                        val countdownMinutes = (snapshot.child("countdownMinutes").value as? Number)?.toInt() ?: 0

                        if (countdownMinutes > 0 && createdAt > 0) {
                            val durationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                            val timeElapsed = System.currentTimeMillis() - createdAt
                            val remainingTime = durationMillis - timeElapsed

                            // Si queda tiempo restante, retorna true
                            continuation.resumeWith(Result.success(remainingTime > 0))
                            return@addOnSuccessListener
                        }
                    }
                    continuation.resumeWith(Result.success(false))
                }
                .addOnFailureListener {
                    continuation.resumeWith(Result.success(false))
                }
        }
    private fun activarAlquilerUsuario(correoKey: String, tituloPelicula: String) {
        val peliculaKey = tituloPelicula.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")

        val alquilerRef = databaseRef.child("usuarios")
            .child(correoKey)
            .child("alquileres")
            .child(peliculaKey)

        val datosAlquiler = mapOf<String, Any>(
            "countdownMinutes" to 300,
            "createdAt" to com.google.firebase.database.ServerValue.TIMESTAMP
        )

        alquilerRef.setValue(datosAlquiler).addOnSuccessListener {
            Log.d("ALQUILER", "Alquiler individual activado con éxito para: $tituloPelicula")
        }.addOnFailureListener {
            Log.e("ALQUILER", "Error al registrar alquiler local: ${it.message}")
        }
    }
    // --- FUNCIÓN PARA ACTIVAR EL CONTADOR DE 300 MINUTOS ---
//    private fun activarContadorFirebase(tituloPelicula: String) {
//        val query = databaseRef.child("movies").orderByChild("title").equalTo(tituloPelicula)
//
//        query.get().addOnSuccessListener { snapshot ->
//            if (snapshot.exists()) {
//                for (child in snapshot.children) {
//                    // Actualizamos para que el contador inicie AHORA MISMO con 300 minutos (5 horas)
//                    val updates = mapOf<String, Any>(
//                        "countdownMinutes" to 300,
//                        "createdAt" to System.currentTimeMillis()
//                    )
//                    child.ref.updateChildren(updates).addOnSuccessListener {
//                        Log.d("ALQUILER", "Contador de 300 minutos activado para: $tituloPelicula")
//                    }
//                }
//            }
//        }.addOnFailureListener {
//            Log.e("ALQUILER", "Error activando contador: ${it.message}")
//        }
//    }
    // --- ESCENARIO 1: ENLACE BUENO (AHORA CON CONFIRMACIÓN) ---
    private fun procesarEnlaceBueno(costo: Int) {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            CineAlert.show(this, "Debes iniciar sesión para reproducir", CineAlert.Tipo.ERROR)
            return
        }

        val correoKey = user.email!!.replace(".", "_").replace("@", "_")

        // En lugar de cobrar directo, mostramos la alerta de confirmación
        showConfirmPurchaseDialog(costo, correoKey)
    }

    // --- DIALOGO DE CONFIRMACIÓN DE ALQUILER (ENLACE BUENO) ---
    private fun showConfirmPurchaseDialog(costo: Int, correoKey: String) {
        val tituloUsado = modeloActual?.title ?: movieTitle
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

        spannable.append("Contenido disponible.\n¿Deseas alquilar y ver la película ahora?")

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
            btnCancelar.setTextColor(Color.RED)

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
                        // DENTRO DE showConfirmPurchaseDialog (btnVerAhora.setOnClickListener):
                        descontarPuntos(correoKey, costo) { exito ->
                            if (exito) {
                                // 🟢 REGISTRAMOS EL ALQUILER EXCLUSIVAMENTE EN EL PERFIL DE ESTE USUARIO
                                val tituloMovie = modeloActual?.title ?: movieTitle
                                activarAlquilerUsuario(correoKey, tituloMovie)

                                CineAlert.show(this@ApiPeliculaActivity, "¡Alquiler exitoso! Disponible por 5 horas. Puedes pausar y continuar viendo desde tu Perfil.", CineAlert.Tipo.EXITO, dialog.window?.decorView as? ViewGroup) {
                                    irAlReproductorDirecto()
                                }
                            } else {
                                btnVerAhora.isEnabled = true
                                btnVerAhora.text = "Ver Ahora"
                                CineAlert.show(this@ApiPeliculaActivity, "Error procesando el pago", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
                            }
                        }
//                        descontarPuntos(correoKey, costo) { exito ->
//                            if (exito) {
//                                // 🟢 ACTIVAMOS EL CONTADOR AL COBRAR
//                                val tituloMovie = modeloActual?.title ?: movieTitle
//                                activarContadorFirebase(tituloMovie)
//
////
//                                CineAlert.show(this@ApiPeliculaActivity, "¡Película activada por 300 minutos!", CineAlert.Tipo.EXITO, dialog.window?.decorView as? ViewGroup)
//                                {
//                                irAlReproductorDirecto()
//                                }
//                            } else {
//                                btnVerAhora.isEnabled = true
//                                btnVerAhora.text = "Ver Ahora"
//                                CineAlert.show(this@ApiPeliculaActivity, "Error procesando el pago", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
//                            }
//
//                        }
                    } else {
                        btnVerAhora.isEnabled = true
                        btnVerAhora.text = "Ver Ahora"
                        CineAlert.show(this@ApiPeliculaActivity, "Saldo CasTV insuficiente.",CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
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
        val tituloUsado = modeloActual?.title ?: movieTitle
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
        spannable.append("\nEl tiempo estimado de gestión es lo mas pronto posible; Notificacion por canal oficial de telegram.")
        spannable.append("\n⚠️ RESTRICCIONES")
        spannable.append("\nSi la película tiene menos de un mes de estreno, No será procesada. El valor será reembolsado automáticamente como crédito en CasTV.")
        spannable.append("\nRecuerda mantener saldo en tu cuenta CasTV para disfrutar de tus próximos alquileres.")

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
            startActivity(Intent(this, Perfil::class.java))
        }

        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)

            .setNegativeButton("Volver al contenido") { dialog, _ ->
                dialog.dismiss()
                volverAlContenido()
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
    // 🟢 ESTA FUNCIÓN ES LA QUE HACE EL REGRESO LIMPIO
    private fun volverAlContenido() {
        val intent = Intent(this@ApiPeliculaActivity, PeliculasActivity::class.java).apply {
            // Trae la cartelera al frente sin recargarla
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)

        // 🟢 USAMOS TUS ANIMACIONES PERSONALIZADAS
        // R.anim.fade_in_slow -> Hace aparecer la cartelera suavemente (500ms)
        // R.anim.stay -> Mantiene la pantalla de detalles quieta mientras se desvanece
        overridePendingTransition(R.anim.fade_in_slow, R.anim.stay)

        finish()
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
                // 🟢 Usamos el bloque { } para que espere 2.5 segundos antes de cerrar y salir
                CineAlert.show(
                    this,
                    "Esta película ya fue pedida, estamos trabajando en ella.",
                    CineAlert.Tipo.ERROR,
                    dialog.window?.decorView as? ViewGroup
                ) {
                    // --- TODO ESTO SE EJECUTARÁ DESPUÉS DE 2.5 SEGUNDOS ---
                    dialog.dismiss()
                    volverAlContenido()
                }
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
                    val costoPedido = modeloActual?.castv ?: movieCastv // Usamos el costo actualizado

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

                                    descontarPuntos(correoKey, costoPedido)

                                    // 🟢 SI SE DESCONTARON LOS PUNTOS: Guardamos la película rota aquí mismo
                                    val tituloOriginal = movieOriginalTitle.ifBlank { modeloActual?.originalTitle ?: movieTitle }
                                    val urlImagen = movieImageUrl.ifBlank { modeloActual?.imageUrl ?: "" }
                                    val anioEstreno = "2026"

                                    verificarYCrearPeliculaRota(
                                        tituloMovie = movieTitle,
                                        originalTitleMovie = tituloOriginal,
                                        imageUrlMovie = urlImagen,
                                        urlRota = streamUrlGuardado,
                                        anio = anioEstreno
                                    )

                                    CineAlert.show(this, "Pedido enviado. Puntos descontados.", CineAlert.Tipo.EXITO, dialog.window?.decorView as? ViewGroup)
                                    {
                                    isProcessingOrder = false
                                    dialog.dismiss()
                                    volverAlContenido()
                                }
                                }
                                .addOnFailureListener { e ->
                                    isProcessingOrder = false
                                    CineAlert.show(this, "Error al enviar: ${e.message}", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
                                }

                        } else {
                            isProcessingOrder = false
                            CineAlert.show(this, "Saldo CasTV insuficiente.", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
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

                // Realizamos el descuento
                userRef.child("castv").setValue(nuevosPuntos)
                    .addOnSuccessListener {
                        // 🟢 AHORA: Registramos el consumo en el historial
                        // Necesitamos obtener el email original para el historial
                        val emailOriginal = correoKey.replace("_", ".")
                        val tituloPelicula = modeloActual?.title ?: movieTitle

                        CastvHelper.registrarConsumo(emailOriginal, tituloPelicula, costo)

                        onComplete?.invoke(true)
                    }
                    .addOnFailureListener { onComplete?.invoke(false) }
            } else {
                onComplete?.invoke(false)
            }
        }.addOnFailureListener { onComplete?.invoke(false) }
    }

    private fun irAlReproductorDirecto() {
        val tituloConFecha = if (modeloActual != null) {
            val fecha = modeloActual?.releaseDate ?: movieReleaseDate
            "${modeloActual?.title} $fecha"
        } else {
            "$movieTitle $movieReleaseDate"
        }

        val intent = Intent(this, PlayerPeliculas::class.java).apply {
            putExtra("EXTRA_STREAM_URL", streamUrlGuardado)
            putExtra("EXTRA_MOVIE_TITLE", tituloConFecha)
            putExtra("EXTRA_MOVIE_CASTV", modeloActual?.castv ?: movieCastv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", modeloActual?.imageUrl ?: movieImageUrl)
            putExtra("EXTRA_COUNTDOWN", modeloActual?.countdownMinutes ?: movieCountdown)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    // --- CARGA DE UI Y API ---
    private fun cargarCartelera() {
        val pelisMostradas = mutableListOf<Modelo>()

        carteleraAdapter = PelisCarteleraAdapter(pelisMostradas) { movieSeleccionado ->
            actualizarPeliculaSeleccionada(movieSeleccionado)
        }
        recyclerCartelera.adapter = carteleraAdapter

        CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val listaCompleta = Validacioneslista.obtenerPeliculasValidas()

                // Filtramos:
                // 1. Que la URL sea válida (no vacía o no "tuservidor.com")
                // 2. Que el contador ya haya expirado o no exista (isCountdownActive = false)
                val listaFiltrada = listaCompleta.filter { movie ->
                    val esUrlValida = movie.streamUrl.isNotBlank() && !movie.streamUrl.contains("tuservidor.com")

                    // Calculamos si el contador está activo para esta película
                    val isCountdownActive = calcularSiContadorEstaActivo(movie)

                    esUrlValida && !isCountdownActive
                }

                if (listaFiltrada.size != pelisMostradas.size) {
                    pelisMostradas.clear()
                    pelisMostradas.addAll(listaFiltrada)
                    carteleraAdapter.notifyDataSetChanged()
                }

                if (Validacioneslista.yaCargado()) break
                delay(1000) // Aumentamos un poco el delay para no saturar
            }
        }
    }
    private fun calcularSiContadorEstaActivo(modelo: Modelo): Boolean {
        if (modelo.countdownMinutes <= 0) return false

        val createdAtMillis = if (modelo.createdAt > 0 && modelo.createdAt < 1000000000000L) {
            modelo.createdAt * 1000
        } else {
            modelo.createdAt
        }

        if (createdAtMillis == 0L) return false

        val countdownDurationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(modelo.countdownMinutes.toLong())
        val timeElapsed = System.currentTimeMillis() - createdAtMillis
        val remainingTimeMillis = countdownDurationMillis - timeElapsed

        // Si remainingTimeMillis > 0, significa que el contador está corriendo
        return remainingTimeMillis > 0
    }
    private fun actualizarPeliculaSeleccionada(modeloSeleccionado: Modelo) {
        tvTitulo.text = modeloSeleccionado.title
        tvSinopsis.text = "Cargando información detallada..."
        tvInfoAdicional.text = "Obteniendo géneros y duración..."
        recyclerActores.adapter = null

        Glide.with(this).load(modeloSeleccionado.imageUrl).placeholder(ivPoster.drawable).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(ivPoster)
        Glide.with(this).load(modeloSeleccionado.imageUrl).centerCrop().transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade()).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(backgroundImageView)

        // 🟢 ACTUALIZACIÓN CRÍTICA DE VARIABLES GLOBALES
        streamUrlGuardado = modeloSeleccionado.streamUrl
        movieTitle = modeloSeleccionado.title
        movieImageUrl = modeloSeleccionado.imageUrl
        movieCastv = modeloSeleccionado.castv
        movieCountdown = modeloSeleccionado.countdownMinutes
        movieCreatedAt = modeloSeleccionado.createdAt
        modeloActual = modeloSeleccionado

        val consulta = modeloSeleccionado.originalTitle ?: modeloSeleccionado.title
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
        val backdropUrl = "https://image.tmdb.org/t/p/w780${movie.backdrop_path}"

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
        val movie = modeloActual ?: return
        val url = movie.imageUrl

        // 1. Título
        tvTitulo.text = movie.title.ifEmpty { "Gran Estreno CineParche" }

        // 2. Fecha (Si está vacía, no ponemos nada o un texto genérico)
        tvFecha.text = if (movie.releaseDate.isNullOrBlank()) "Estreno Reciente" else "${movie.releaseDate} ✅"

        // 3. Calificación (MEJORADO: Si es 0 o null, ponemos un 8.5 por defecto)
        // Un 8.5 o 9.0 hace que la película se vea "recomendada"
        tvCalificacion.text = if (movie.voteAverage == null || movie.voteAverage == 0.0) {
            "⭐ 8.5"
        } else {
            "⭐ ${movie.voteAverage}"
        }

        // 4. Géneros (NUEVO: Si está vacío, ponemos géneros comunes de cine)
        // Esto rellena el espacio que veías vacío
        tvInfoAdicional.text = if (movie.genres.isNullOrBlank()) {
            "Acción • Aventura • Cine"
        } else {
            movie.genres
        }

        // 5. Sinopsis (Mensaje de invitación profesional)
        tvSinopsis.text = if (movie.overview.isNullOrBlank()) {
            "Disfruta de esta increíble producción ahora en CineParche. Una historia fascinante que no te puedes perder. ¡Prepara tus palomitas y dale play!"
        } else {
            movie.overview
        }

        // --- Gestión de Imágenes con Glide ---
        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.pelifondo)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(ivPoster)

        Glide.with(this)
            .load(url)
            .centerCrop()
            .placeholder(android.R.color.black)
            .error(android.R.color.black)
            .transition(DrawableTransitionOptions.withCrossFade())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(backgroundImageView)
    }
}