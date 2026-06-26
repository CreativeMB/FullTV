package com.creativem.fulltv.api

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.addCallback
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
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class ApiPeliculaActivity : AppCompatActivity() {

    private var realTmdbId: Int = 0
    private var realReleaseDate: String = ""
    private var realTitle: String = ""
    data class VideoResponse(val results: List<VideoResult>)
    data class VideoResult(val key: String, val site: String, val type: String)
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

        tvReproducir.setOnClickListener {
            val urlActual = streamUrlGuardado
            val costoActual = modeloActual?.castv ?: movieCastv
            val user = auth.currentUser

            if (urlActual.contains("tuservidor.com") || urlActual.isBlank()) {
                manejarEnlaceRoto(costoActual)
                return@setOnClickListener
            }

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

            if (isPromoGlobalActiva) {
                irAlReproductorDirecto()
                return@setOnClickListener
            }

            if (user == null || user.email == null) {
                CineAlert.show(this, "Debes iniciar sesión para reproducir", CineAlert.Tipo.ERROR)
                return@setOnClickListener
            }

            val correoKey = user.email!!.replace(".", "_").replace("@", "_")
            val tituloMovie = modeloActual?.title ?: movieTitle

            tvReproducir.isEnabled = false
            val textoOriginal = tvReproducir.text
            tvReproducir.text = "Verificando..."

            CoroutineScope(Dispatchers.Main).launch {
                val isAlquilerActivo = withContext(Dispatchers.IO) {
                    verificarAlquilerVigenteSincrono(correoKey, tituloMovie)
                }

                if (isAlquilerActivo) {
                    tvReproducir.isEnabled = true
                    tvReproducir.text = textoOriginal
                    irAlReproductorDirecto()
                } else {
                    tvReproducir.text = "Procesando Datos..."

                    val enlaceValido = withContext(Dispatchers.IO) {
                        validaciones.isUrlValid(urlActual)
                    }

                    if (enlaceValido) {
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

        // BUSCAMOS EL BOTÓN POR SU ID (Asegúrate de que en tu XML el botón tenga el id "btnVerTrailer")
        val btnVerTrailer = findViewById<View>(R.id.btnVerTrailer)

        btnVerTrailer.setOnClickListener {
            reproducirTrailer()
        }

        cargarCartelera()
        buscarPelicula(movieOriginalTitle.ifBlank { movieTitle })
        // Limpiamos el nombre de cualquier residuo como "(2026-04-24)" o "[1080p]" para asegurar la búsqueda
        val consultaLimpia = (movieOriginalTitle.ifBlank { movieTitle })
            .replace(Regex("\\(\\d{4}-\\d{2}-\\d{2}\\)"), "")
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()

        buscarPelicula(consultaLimpia)
        onBackPressedDispatcher.addCallback(this) {
            CastvHelper.regresarAPeliculas(this@ApiPeliculaActivity)
        }
    }

    private fun reproducirTrailer() {
        if (realTmdbId == 0) {
            Toast.makeText(this, "El tráiler no está disponible para esta película.", Toast.LENGTH_SHORT).show()
            return
        }

        // Buscamos primero en Español de México
        apiService.getMovieVideos(realTmdbId, apiKey, "es-MX").enqueue(object : Callback<VideoResponse> {
            override fun onResponse(call: Call<VideoResponse>, response: Response<VideoResponse>) {
                var keyTrailer = ""
                if (response.isSuccessful) {
                    val resultados = response.body()?.results ?: emptyList()
                    val trailer = resultados.find { it.site == "YouTube" && it.type == "Trailer" }
                        ?: resultados.find { it.site == "YouTube" }
                    if (trailer != null) {
                        keyTrailer = trailer.key
                    }
                }

                if (keyTrailer.isNotEmpty()) {
                    abrirTrailer(keyTrailer)
                } else {
                    // Plan B: Si no hay en español, buscamos el original sin idioma
                    apiService.getMovieVideos(realTmdbId, apiKey, "").enqueue(object : Callback<VideoResponse> {
                        override fun onResponse(call: Call<VideoResponse>, response: Response<VideoResponse>) {
                            if (response.isSuccessful) {
                                val resultados = response.body()?.results ?: emptyList()
                                val trailer = resultados.find { it.site == "YouTube" && it.type == "Trailer" }
                                    ?: resultados.find { it.site == "YouTube" }
                                if (trailer != null) {
                                    abrirTrailer(trailer.key)
                                } else {
                                    Toast.makeText(this@ApiPeliculaActivity, "Tráiler no disponible en YouTube.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        override fun onFailure(call: Call<VideoResponse>, t: Throwable) {
                            Toast.makeText(this@ApiPeliculaActivity, "Tráiler no disponible.", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }

            override fun onFailure(call: Call<VideoResponse>, t: Throwable) {
                Toast.makeText(this@ApiPeliculaActivity, "Error de red al buscar el tráiler.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun abrirTrailer(key: String) {
        val intent = Intent(this@ApiPeliculaActivity, TrailerActivity::class.java).apply {
            putExtra("EXTRA_TRAILER_KEY", key)
        }
        startActivity(intent)
    }
        private fun verificarYCrearPeliculaRota(
        tituloMovie: String,
        originalTitleMovie: String,
        imageUrlMovie: String,
        urlRota: String,
        anio: String,
        userEmail: String,
        userName: String,
        fechaActivacion: String,
        horaActivacion: Int
    ) {
        if (isFinishing || isDestroyed) return

        val customDbUrl = "https://corario-16991-default-rtdb.firebaseio.com/"
        val databaseRef = com.google.firebase.database.FirebaseDatabase
            .getInstance(customDbUrl)
            .getReference("movies")

        if (originalTitleMovie.isBlank()) {
            android.util.Log.w("FirebaseTV", "⚠️ No se puede registrar película rota: título original vacío.")
            return
        }

        databaseRef.orderByChild("originalTitle").equalTo(originalTitleMovie)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (isFinishing || isDestroyed) return

                    if (snapshot.exists()) {
                        android.util.Log.d("FirebaseTV", "La película '$originalTitleMovie' ya existe. Actualizando fecha y agregando solicitud.")

                        val existingId = snapshot.children.firstOrNull()?.key ?: ""
                        if (existingId.isNotEmpty()) {
                            val tiempoActual = System.currentTimeMillis()
                            databaseRef.child(existingId).child("createdAt").setValue(tiempoActual)
                                .addOnSuccessListener {
                                    android.util.Log.d("FirebaseTV", "📅 'createdAt' actualizado.")
                                }

                            val solicitudesRef = databaseRef.child(existingId).child("solicitudes").push()
                            val idSolicitud = solicitudesRef.key ?: ""

                            val datosSolicitud = mapOf<String, Any>(
                                "id" to idSolicitud,
                                "email" to userEmail,
                                "userId" to userName,
                                "fechaActivacion" to fechaActivacion,
                                "horaActivacion" to horaActivacion,
                                "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                            solicitudesRef.setValue(datosSolicitud)
                        }
                        return
                    }

                    val newId = databaseRef.push().key ?: return

                    val nombreFormateado = if (tituloMovie.contains("($anio)")) {
                        tituloMovie.trim()
                    } else {
                        "$tituloMovie ($anio)".trim()
                    }

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

                    databaseRef.child(newId).setValue(nuevaPeliculaMap)
                        .addOnSuccessListener {
                            if (isFinishing || isDestroyed) return@addOnSuccessListener
                            android.util.Log.d("FirebaseTV", "🟢 Película rota GUARDADA: $newId")

                            val solicitudesRef = databaseRef.child(newId).child("solicitudes").push()
                            val idSolicitud = solicitudesRef.key ?: ""

                            // 🟢 SOLUCIÓN: Guardamos la fecha y hora de activación también para las películas recién creadas
                            val datosSolicitud = mapOf<String, Any>(
                                "id" to idSolicitud,
                                "email" to userEmail,
                                "userId" to userName,
                                "fechaActivacion" to fechaActivacion,
                                "horaActivacion" to horaActivacion,
                                "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                            solicitudesRef.setValue(datosSolicitud)
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
            Log.d("ALQUILER", "Alquiler activado para: $tituloPelicula")
        }
    }

    private fun procesarEnlaceBueno(costo: Int) {
        val user = auth.currentUser
        if (user == null || user.email == null) {
            CineAlert.show(this, "Debes iniciar sesión para reproducir", CineAlert.Tipo.ERROR)
            return
        }
        val correoKey = user.email!!.replace(".", "_").replace("@", "_")
        showConfirmPurchaseDialog(costo, correoKey)
    }

    private fun showConfirmPurchaseDialog(costo: Int, correoKey: String) {
        // ARMAMOS EL TÍTULO CON LA FECHA COMPLETA PARA EL DIÁLOGO
        val tituloBase = modeloActual?.title ?: movieTitle
        val fecha = modeloActual?.releaseDate ?: ""
        val tituloUsado = if (fecha.isNotEmpty()) "$tituloBase ($fecha)" else tituloBase
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val builder = AlertDialog.Builder(this)

        val customTitle = TextView(this).apply {
            text = "🎬 Confirmar Alquiler"
            setTextColor(colorDorado)
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setPadding(40, 50, 40, 20)
            gravity = android.view.Gravity.CENTER
        }
        builder.setCustomTitle(customTitle)

        val spannable = SpannableStringBuilder()
        spannable.append("Película: ")
        val startPelicula = spannable.length
        spannable.append("$tituloUsado\n\n")
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), startPelicula, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startPelicula, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        spannable.append("Costo: ")
        val startCosto = spannable.length
        spannable.append("$costo CasTV\n\n")
        spannable.setSpan(ForegroundColorSpan(Color.parseColor("#00E676")), startCosto, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startCosto, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), startCosto, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        spannable.append("Contenido disponible.\n¿Deseas alquilar y ver la película ahora?")

        builder.setMessage(spannable)
        builder.setCancelable(true)

        builder.setPositiveButton("Ver Ahora", null)
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))

        dialog.setOnShowListener {
            val btnVerAhora = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancelar = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            btnVerAhora.setTextColor(colorDorado)
            btnVerAhora.setTypeface(Typeface.DEFAULT_BOLD)
            btnCancelar.setTextColor(Color.RED)

            val focusSelector = R.drawable.focus_selector
            listOf(btnVerAhora, btnCancelar).forEach { button ->
                button.setBackgroundResource(focusSelector)
                button.isFocusable = true
                button.isFocusableInTouchMode = true
                button.setPadding(40, 20, 40, 20)
            }

            btnVerAhora.isEnabled = false
            btnCancelar.isEnabled = false

            btnVerAhora.setOnClickListener {
                btnVerAhora.isEnabled = false
                btnVerAhora.text = "Procesando..."

                verificarPuntos(correoKey, costo) { tienePuntos ->
                    if (tienePuntos) {
                        descontarPuntos(correoKey, costo) { exito ->
                            if (exito) {
                                val tituloMovie = modeloActual?.title ?: movieTitle
                                activarAlquilerUsuario(correoKey, tituloMovie)

                                CineAlert.show(this@ApiPeliculaActivity, "¡Alquiler exitoso! Disponible por 5 horas.", CineAlert.Tipo.EXITO, dialog.window?.decorView as? ViewGroup) {
                                    irAlReproductorDirecto()
                                }
                            } else {
                                btnVerAhora.isEnabled = true
                                btnVerAhora.text = "Ver Ahora"
                                CineAlert.show(this@ApiPeliculaActivity, "Error procesando el pago", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
                            }
                        }
                    } else {
                        btnVerAhora.isEnabled = true
                        btnVerAhora.text = "Ver Ahora"
                        CineAlert.show(this@ApiPeliculaActivity, "Saldo CasTV insuficiente.", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
                    }
                }
            }

            Handler(Looper.getMainLooper()).postDelayed({
                btnVerAhora.isEnabled = true
                btnCancelar.isEnabled = true
                btnVerAhora.requestFocus()
            }, 500)

            val tvMessage = dialog.findViewById<TextView>(android.R.id.message)
            tvMessage?.apply {
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 17f
                setLineSpacing(0f, 1.2f)
                gravity = android.view.Gravity.CENTER
            }
        }

        dialog.show()

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

    private fun manejarEnlaceRoto(costo: Int) {
        val correoUsuario = auth.currentUser?.email ?: ""

        // ARMAMOS EL TÍTULO CON LA FECHA COMPLETA PARA EL DIÁLOGO
        val tituloBase = modeloActual?.title ?: movieTitle
        val fecha = modeloActual?.releaseDate ?: ""
        val tituloUsado = if (fecha.isNotEmpty()) "$tituloBase ($fecha)" else tituloBase

        showErrorDialog(tituloUsado, costo, correoUsuario)
    }

    @SuppressLint("SetTextI18n")
    private fun showErrorDialog(movieTitle: String, movieCastv: Int, correoUsuario: String) {
        val colorFondoPrincipal = Color.parseColor("#2A2A2A")
        val colorDorado = Color.parseColor("#C5A059")
        val colorRojoSuave = Color.parseColor("#F87171")

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        val maxDialogWidth = dpToPx(640)
        val targetWidth = minOf((displayMetrics.widthPixels * 0.75).toInt(), maxDialogWidth)

        val dialogView = layoutInflater.inflate(R.layout.player_alerdialogo, null).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val linkNosotros = dialogView.findViewById<TextView>(R.id.linkNosotros)

        messageText.setLineSpacing(0f, 1.25f)

        val spannable = SpannableStringBuilder()
        val colorVerdeSuave = Color.parseColor("#4ADE80")
        val colorGrisTexto = Color.parseColor("#B0BEC5")
        val colorLineaSeparadora = Color.parseColor("#444444")

        val startPeli = spannable.length
        spannable.append("🎬 Película: ")
        val endLabelPeli = spannable.length
        spannable.append("$movieTitle\n")
        val endPeli = spannable.length

        spannable.setSpan(ForegroundColorSpan(Color.WHITE), startPeli, endLabelPeli, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startPeli, endLabelPeli, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(colorDorado), endLabelPeli, endPeli - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), endLabelPeli, endPeli - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val startCosto = spannable.length
        spannable.append("🪙 Costo: ")
        val endLabelCosto = spannable.length
        spannable.append("$movieCastv CasTV\n")
        val endCosto = spannable.length

        spannable.setSpan(ForegroundColorSpan(Color.WHITE), startCosto, endLabelCosto, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startCosto, endLabelCosto, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(colorVerdeSuave), endLabelCosto, endCosto - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), endLabelCosto, endCosto - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val startInfoUsuario = spannable.length
        spannable.append("👤 Usuario: Consultando...   |   💎 Saldo actual: Consultando...\n\n")
        val endInfoUsuario = spannable.length
        spannable.setSpan(ForegroundColorSpan(colorGrisTexto), startInfoUsuario, endInfoUsuario, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val startLinea = spannable.length
        spannable.append("──────────────────────────────────────────\n\n")
        val endLinea = spannable.length
        spannable.setSpan(ForegroundColorSpan(colorLineaSeparadora), startLinea, endLinea, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val startRestricciones = spannable.length
        spannable.append("⚠️ RESTRICCIONES IMPORTANTES\n")
        val endLabelRestricciones = spannable.length
        spannable.setSpan(ForegroundColorSpan(colorRojoSuave), startRestricciones, endLabelRestricciones, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startRestricciones, endLabelRestricciones, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.append("• Recuerda mantener saldo en tu cuenta para tus próximos alquileres.")
        val endAll = spannable.length

        spannable.setSpan(RelativeSizeSpan(0.85f), startRestricciones, endAll, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(colorGrisTexto), endLabelRestricciones, endAll, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        messageText.text = spannable

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
        linkNosotros.setTextColor(colorRojoSuave)
        linkNosotros.paintFlags = linkNosotros.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        linkNosotros.setOnClickListener {
            startActivity(Intent(this, Perfil::class.java))
        }

        val dialogScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            addView(dialogView)
        }

        val layoutBotones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(20)
            }
        }

        val btnVolver = TextView(this).apply {
            text = "Volver al contenido"
            setTextColor(colorRojoSuave)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(30), dpToPx(12), dpToPx(30), dpToPx(12))
            isFocusable = true
            isFocusableInTouchMode = true
            background = androidx.core.content.ContextCompat.getDrawable(this@ApiPeliculaActivity, R.drawable.focus_selector)

            setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.05f else 1f
                v.scaleY = if (hasFocus) 1.05f else 1f
            }
        }

        val btnAlquilar = TextView(this).apply {
            text = "Alquilar Película"
            setTextColor(colorDorado)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(30), dpToPx(12), dpToPx(30), dpToPx(12))
            isFocusable = true
            isFocusableInTouchMode = true
            background = androidx.core.content.ContextCompat.getDrawable(this@ApiPeliculaActivity, R.drawable.focus_selector)

            setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.05f else 1f
                v.scaleY = if (hasFocus) 1.05f else 1f
            }
        }

        val paramsBoton = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dpToPx(12), 0, dpToPx(12), 0)
        }

        layoutBotones.addView(btnVolver, paramsBoton)
        layoutBotones.addView(btnAlquilar, paramsBoton)

        val dialogWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(28), dpToPx(28), dpToPx(28), dpToPx(28))
            background = GradientDrawable().apply {
                setColor(colorFondoPrincipal)
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), colorDorado)
            }
            addView(dialogScrollView)
            addView(layoutBotones)
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogWrapper)
            .setCancelable(false)
            .create()

        alertDialog.setCanceledOnTouchOutside(false)

        btnVolver.setOnClickListener {
            alertDialog.dismiss()
            volverAlContenido()
        }

        btnAlquilar.setOnClickListener {
            // Deshabilitamos el botón mientras consulta TMDb
            btnAlquilar.isEnabled = false
            val textoOriginal = btnAlquilar.text
            btnAlquilar.text = "Verificando fecha..."

            validarEstrenoYProcesar(
                onSuccess = {
                    // Si pasó los 40 días: restauramos el botón y abrimos el calendario
                    btnAlquilar.isEnabled = true
                    btnAlquilar.text = textoOriginal
                    CastvHelper.mostrarSelectorFechaHora(this@ApiPeliculaActivity) { fechaSeleccionada, horaSeleccionada ->
                        enviarPedido(alertDialog, fechaSeleccionada, horaSeleccionada)
                    }
                },
                onRechazado = { mensajeError ->
                    // Si es reciente o hubo error: restauramos el botón y mostramos el mensaje rojo
                    btnAlquilar.isEnabled = true
                    btnAlquilar.text = textoOriginal
                    CineAlert.show(
                        this@ApiPeliculaActivity,
                        mensajeError,
                        CineAlert.Tipo.ERROR,
                        alertDialog.window?.decorView as? ViewGroup
                    )
                }
            )
        }
        alertDialog.show()

        alertDialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        btnAlquilar.requestFocus()
    }
    private fun validarEstrenoYProcesar(onSuccess: () -> Unit, onRechazado: (String) -> Unit) {
        // Tomamos el título original o el título normal para buscarlo en la API
        val queryTitle = movieOriginalTitle.ifBlank { modeloActual?.originalTitle ?: movieTitle }

        // Consultamos la API oficial
        apiService.searchMovie(apiKey, "es-MX", queryTitle).enqueue(object : Callback<MovieResponse> {
            override fun onResponse(call: Call<MovieResponse>, response: Response<MovieResponse>) {
                var fechaAValidar = ""

                // 1. Intentamos sacar la fecha real de TMDb
                if (response.isSuccessful) {
                    val primeraPeli = response.body()?.results?.firstOrNull()
                    if (primeraPeli != null && !primeraPeli.release_date.isNullOrBlank()) {
                        fechaAValidar = primeraPeli.release_date
                    }
                }

                // 2. Si TMDb no respondió con fecha, usamos la fecha que llegó por la app
                if (fechaAValidar.isBlank()) {
                    fechaAValidar = modeloActual?.releaseDate ?: movieReleaseDate
                }

                // 3. SI DEFINITIVAMENTE NO HAY FECHA (Seguridad estricta): RECHAZAMOS
                if (fechaAValidar.isBlank()) {
                    onRechazado("No se pudo verificar la fecha de estreno en la base de datos. Operación denegada por seguridad.")
                    return
                }

                // 4. HACEMOS EL CÁLCULO ESTRICTO DE LOS 40 DÍAS
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val dateEstreno = sdf.parse(fechaAValidar)

                    if (dateEstreno != null) {
                        val diffMillis = System.currentTimeMillis() - dateEstreno.time
                        val diffDias = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)

                        if (diffDias < 40) {
                            val diasFaltantes = if (diffDias < 0) 40 + kotlin.math.abs(diffDias) else 40 - diffDias
                            onRechazado("Película en cines Estreno ($fechaAValidar).\nDeben pasar 40 días. Faltan aprox. $diasFaltantes días.")
                        } else {
                            onSuccess() // Pasó la prueba, procedemos
                        }
                    } else {
                        onRechazado("Error leyendo la fecha de estreno.")
                    }
                } catch (e: Exception) {
                    onRechazado("Error de formato en la fecha.")
                }
            }

            override fun onFailure(call: Call<MovieResponse>, t: Throwable) {
                // Si no hay internet para consultar la API, bloqueamos el pedido por seguridad
                onRechazado("Error de red. No se pudo conectar con la base de datos para validar el estreno.")
            }
        })
    }

    private fun volverAlContenido() {
        val intent = Intent(this@ApiPeliculaActivity, PeliculasActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in_slow, R.anim.stay)
        finish()
    }

    private fun enviarPedido(dialog: AlertDialog, fechaActivacion: String, horaActivacion: Int) {
        if (isProcessingOrder) return
        isProcessingOrder = true

        val user = auth.currentUser
        if (user != null && user.email != null) {
            val correoKey = user.email!!.replace(".", "_").replace("@", "_")

            databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val userName = snapshot.child("nombre").value?.toString() ?: "Sin nombre"
                    val userEmail = snapshot.child("correo").value?.toString() ?: user.email!!
                    val costoPedido = modeloActual?.castv ?: movieCastv

                    verificarPuntos(correoKey, costoPedido) { tienePuntos ->
                        if (tienePuntos) {
                            descontarPuntos(correoKey, costoPedido) { exitoDescuento ->
                                if (exitoDescuento) {
                                    val tituloOriginal = movieOriginalTitle.ifBlank { modeloActual?.originalTitle ?: movieTitle }
                                    val urlImagen = movieImageUrl.ifBlank { modeloActual?.imageUrl ?: "" }

                                    // 🟢 EXTRAEMOS LA FECHA Y ARMAMOS EL TÍTULO
                                    val tituloBase = modeloActual?.title ?: movieTitle
                                    val fecha = modeloActual?.releaseDate ?: ""
                                    val tituloUsado = if (fecha.isNotEmpty()) "$tituloBase ($fecha)" else tituloBase

                                    // 🟢 SACAMOS EL AÑO REAL DE LA FECHA (Ej: "2026" de "2026-04-24")
                                    val anioEstreno = if (fecha.length >= 4) fecha.substring(0, 4) else "2024"

                                    verificarYCrearPeliculaRota(
                                        tituloMovie = tituloUsado, // Enviamos el título con fecha a Firebase
                                        originalTitleMovie = tituloOriginal,
                                        imageUrlMovie = urlImagen,
                                        urlRota = streamUrlGuardado,
                                        anio = anioEstreno, // Año real
                                        userEmail = userEmail,
                                        userName = userName,
                                        fechaActivacion = fechaActivacion,
                                        horaActivacion = horaActivacion
                                    )

                                    CineAlert.show(this, "Pedido enviado. Puntos descontados.", CineAlert.Tipo.EXITO, dialog.window?.decorView as? ViewGroup) {
                                        isProcessingOrder = false
                                        dialog.dismiss()
                                        volverAlContenido()
                                    }
                                } else {
                                    isProcessingOrder = false
                                    CineAlert.show(this, "Error al procesar el descuento de puntos.", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
                                }
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

                userRef.child("castv").setValue(nuevosPuntos)
                    .addOnSuccessListener {
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
        val tituloBase = modeloActual?.title ?: movieTitle
        val fecha = modeloActual?.releaseDate ?: ""
        val tituloConFecha = if (fecha.isNotEmpty()) "$tituloBase ($fecha)" else tituloBase

        val intent = Intent(this, PlayerPeliculas::class.java).apply {
            putExtra("EXTRA_STREAM_URL", streamUrlGuardado)
            putExtra("EXTRA_MOVIE_TITLE", tituloConFecha) // Pasamos título + fecha
            putExtra("EXTRA_MOVIE_CASTV", modeloActual?.castv ?: movieCastv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", modeloActual?.imageUrl ?: movieImageUrl)
            putExtra("EXTRA_COUNTDOWN", modeloActual?.countdownMinutes ?: movieCountdown)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun cargarCartelera() {
        val pelisMostradas = mutableListOf<Modelo>()

        carteleraAdapter = PelisCarteleraAdapter(pelisMostradas) { movieSeleccionado ->
            actualizarPeliculaSeleccionada(movieSeleccionado)
        }
        recyclerCartelera.adapter = carteleraAdapter

        CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val listaCompleta = Validacioneslista.obtenerPeliculasValidas()

                val listaFiltrada = listaCompleta.filter { movie ->
                    val esUrlValida = movie.streamUrl.isNotBlank() && !movie.streamUrl.contains("tuservidor.com")
                    val isCountdownActive = calcularSiContadorEstaActivo(movie)
                    esUrlValida && !isCountdownActive
                }

                if (listaFiltrada.size != pelisMostradas.size) {
                    pelisMostradas.clear()
                    pelisMostradas.addAll(listaFiltrada)
                    carteleraAdapter.notifyDataSetChanged()
                }

                if (Validacioneslista.yaCargado()) break
                delay(1000)
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

        return remainingTimeMillis > 0
    }

    private fun actualizarPeliculaSeleccionada(modeloSeleccionado: Modelo) {
        tvTitulo.text = modeloSeleccionado.title
        tvSinopsis.text = "Cargando información detallada..."
        tvInfoAdicional.text = "Obteniendo géneros y duración..."
        recyclerActores.adapter = null

        Glide.with(this).load(modeloSeleccionado.imageUrl).placeholder(ivPoster.drawable).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(ivPoster)
        Glide.with(this).load(modeloSeleccionado.imageUrl).centerCrop().transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade()).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(backgroundImageView)

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
        realTmdbId = movie.id
        realReleaseDate = movie.release_date ?: ""
        realTitle = movie.title ?: ""
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
        // 🟢 Fallback: si TMDb no responde, usamos los datos locales/intent
        realReleaseDate = movie.releaseDate
        realTitle = movie.title
        realTmdbId = movie.id.toIntOrNull() ?: 0

        val url = movie.imageUrl

        tvTitulo.text = movie.title.ifEmpty { "Gran Estreno CineParche" }
        tvFecha.text = if (movie.releaseDate.isNullOrBlank()) "Estreno Reciente" else "${movie.releaseDate} ✅"

        tvCalificacion.text = if (movie.voteAverage == null || movie.voteAverage == 0.0) {
            "⭐ 8.5"
        } else {
            "⭐ ${movie.voteAverage}"
        }

        tvInfoAdicional.text = if (movie.genres.isNullOrBlank()) {
            "Acción • Aventura • Cine"
        } else {
            movie.genres
        }

        tvSinopsis.text = if (movie.overview.isNullOrBlank()) {
            "Disfruta de esta increíble producción ahora en CineParche."
        } else {
            movie.overview
        }

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
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            CastvHelper.regresarAPeliculas(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}