package com.creativem.fulltv.principal

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.creativem.fulltv.BuildConfig
import com.creativem.fulltv.R
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.api.PeliculasApiFragment
import com.creativem.fulltv.api.TMDbApiClient
import com.creativem.fulltv.databinding.MainPrincipalfragmentBinding
import com.creativem.fulltv.enlinea.UsuarioEstadoManager
import com.creativem.fulltv.menu.MenuPrincipalAdapter
import com.creativem.fulltv.menu.MenuPrincipalItem
import com.creativem.fulltv.peliculas.PeliculasFragment
import com.creativem.fulltv.peliculasvalidas.PeliculasValidasFragment
import com.creativem.fulltv.tv.TvFragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File


class Main : FragmentActivity() {
    var haProcesadoEliminacion = false
    private var yaMostroPublicidad = false
    private lateinit var binding: MainPrincipalfragmentBinding
    private var publicidadDialog: Dialog? = null
    private var versionRemotaGlobal: String? = null
    private var userStatusListener: ValueEventListener? = null
    private var datosUsuarioListener: ValueEventListener? = null
    private val databaseRef: DatabaseReference = FirebaseDatabase.getInstance().reference
    private var isLoggingOut = false
    private val handler = Handler(Looper.getMainLooper())
    private var fondoAnimando = false

    private var colorIndex = 0
    private val coloresFluorescentes = listOf(
        intArrayOf(0x66FF5E3A.toInt(), 0x66FF2D55.toInt()), // verde claro a fucsia
        intArrayOf(0x6690EE90.toInt(), 0x66DA70D6.toInt()), // verde pastel a violeta claro
        intArrayOf(0x66FFD700.toInt(), 0x66FF69B4.toInt()), // dorado a rosa
        intArrayOf(0x6640E0D0.toInt(), 0x66FF1493.toInt()), // turquesa a fucsia
        intArrayOf(0x66ADD8E6.toInt(), 0x668A2BE2.toInt()), // celeste a violeta
        intArrayOf(0x66FF4500.toInt(), 0x66DAA520.toInt()), // naranja fuerte a dorado suave
        intArrayOf(0x664682B4.toInt(), 0x66E6E6FA.toInt()), // azul acero a lavanda
        intArrayOf(0x66FF7F50.toInt(), 0x6600CED1.toInt()), // coral a azul claro
        intArrayOf(0x66DC143C.toInt(), 0x669370DB.toInt()), // rojo rubí a lila
        intArrayOf(0x66B0E0E6.toInt(), 0x66BA55D3.toInt())

    )



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = MainPrincipalfragmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (savedInstanceState == null) {
            navegarA(PeliculasFragment())
            haProcesadoEliminacion = false
        }

        cargarMenuPrincipal()
        mostrarPublicidad()
        obtenerNoticia()

    }

    private fun navegarA(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }
    fun navegarInicio() {
        navegarA(PeliculasFragment())
    }
    fun navegarAPeliculasValidas() {
        navegarA(PeliculasValidasFragment())
    }

    fun navegarATv() {
        navegarA(TvFragment())
    }
     fun navegarAPeliculasApi() {
        navegarA(PeliculasApiFragment())
     }
    fun updateBackground(imageUrl: String?) {
        // Detener la animación del fondo por defecto si se está ejecutando
        handler.removeCallbacksAndMessages(null)
        fondoAnimando = false

        if (imageUrl.isNullOrEmpty()) {
            setDefaultBackground() // Si la URL es nula, establece el fondo animado
            return
        }

        Glide.with(this)
            .load(imageUrl)
            .centerCrop()
            .into(binding.mainBackgroundImage)
        binding.mainBackgroundImage.alpha = 0.6f
        binding.mainBackgroundImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }
    fun setDefaultBackground() {
        if (fondoAnimando) return
        fondoAnimando = true
        handler.removeCallbacksAndMessages(null)

        fun cambiarColores() {
            val colores = coloresFluorescentes[colorIndex % coloresFluorescentes.size]
            colorIndex++
            val nuevoFondo = GradientDrawable(GradientDrawable.Orientation.TL_BR, colores).apply {
                gradientType = GradientDrawable.LINEAR_GRADIENT
            }
            // Usa el binding de la Activity para acceder a la vista
            binding.mainBackgroundImage.setImageDrawable(nuevoFondo)
            binding.mainBackgroundImage.apply {
                alpha = 0.9f
                scaleType = ImageView.ScaleType.MATRIX
            }
        }

        cambiarColores() // Llama la primera vez

        handler.postDelayed(object : Runnable {
            override fun run() {
                cambiarColores()
                handler.postDelayed(this, 1500)
            }
        }, 1500)
    }

    private fun cargarMenuPrincipal() {
        val recycler = binding.menuPrincipal
        if (recycler == null) {
            Log.e("MenuPrincipal", "RecyclerView menuPrincipal no está en el layout.")
            return
        }

        val menuItems = listOf("Inicio",
            "TV", "Gratis", "Peliculas", "Buscar", "Pedir",
            "Paquete", "Pago", "Cerrar"
        )

        val menuIcons = listOf(
            R.drawable.home,
            R.drawable.tv, R.drawable.cartelera,
            R.drawable.cine, R.drawable.buscar, R.drawable.pedido,
            R.drawable.activacion, R.drawable.pago, R.drawable.cerrrar
        )

        val menuList = menuItems.mapIndexed { i, name ->
            MenuPrincipalItem(name, menuIcons[i])
        }

        val adapter = MenuPrincipalAdapter(menuList) { item ->
            when (item.name) {
                "Inicio" -> navegarInicio()
                "Buscar" -> buscarPeliculaDialogo()
                "Pedir" -> mostrarDialogoPedido()
                "Paquete" -> activarpaquete()
                "Gratis" -> navegarAPeliculasValidas()
                "Peliculas"-> navegarAPeliculasApi()
                "Pago" -> {
                    val intent = Intent(this, Nosotros::class.java)
                    startActivity(intent)
                }
                "TV" -> navegarATv()
                "Cuenta" -> cerrarSesion()
                else -> Toast.makeText(this, "${item.name} seleccionado", Toast.LENGTH_SHORT).show()
            }
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // 🔄 Restaurar el último foco al entrar
        recycler.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                recycler.post {
                    val pos = adapter.lastFocusedPosition
                    val viewHolder = recycler.findViewHolderForAdapterPosition(pos)
                    viewHolder?.itemView?.requestFocus()
                }
            }
        }

        // ✅ Enfocar el primer ítem al cargar por primera vez
        recycler.post {
            recycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }
    private fun buscarPeliculaDialogo() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.buscador, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_edit_text)
        val searchResultsView = dialogView.findViewById<ListView>(R.id.list_view)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        val filteredMovieList = mutableListOf<Movie>()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf<String>()
        )
        searchResultsView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.show()

        progressBar.visibility = View.GONE
        searchEditText.visibility = View.VISIBLE
        searchResultsView.visibility = View.VISIBLE

        // Configura búsqueda en la API
        searchEditText.addTextChangedListener(object : TextWatcher {
            private var searchJob: Job? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()

                if (query.length < 2) return

                searchJob?.cancel()
                searchJob = CoroutineScope(Dispatchers.IO).launch {
                    val response = TMDbApiClient.service.searchMovies(
                        apiKey = "678193d2c735c6f37840cee035f4d69a",
                        language = "es-MX",
                        query = query
                    ).execute()

                    if (response.isSuccessful) {
                        val moviesApi = response.body()?.results ?: emptyList()
                        val mapped = moviesApi.map {
                            Movie(
                                id = it.id.toString(),
                                title = "${it.title} (${it.release_date ?: "N/A"})",
                                originalTitle = it.original_title,
                                imageUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}",
                                streamUrl = "https://tuservidor.com/stream/${it.id}",
                                castv = 50,
                                countdownMinutes = 60,
                                createdAt = Timestamp.now()
                            )
                        }

                        withContext(Dispatchers.Main) {
                            filteredMovieList.clear()
                            filteredMovieList.addAll(mapped)
                            adapter.clear()
                            adapter.addAll(filteredMovieList.map { it.title })
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        searchResultsView.setOnItemClickListener { _, _, position, _ ->
            val selectedMovie = filteredMovieList[position]
            irAlReproductor(selectedMovie)
            dialog.dismiss()
        }
    }
    private fun mostrarDialogoPedido() {
        // CORRECCIÓN: Se usa 'this' como contexto
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Solicitar Película")

        // CORRECCIÓN: Se usa 'this' como contexto para el LinearLayout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 16)

            // CORRECCIÓN: Se usa 'this' como contexto para el TextView
            val indicacionTextView = TextView(this.context).apply {
                text = "Por favor, ingrese el título y Año de estreno.\n" +
                        "Recuerde; no se pueden Alquilar películas con menos de un mes de estreno."
                textSize = 14f
                setPadding(0, 0, 0, 16)
            }

            // CORRECCIÓN: Se usa 'this' como contexto para el EditText
            val inputPedido = EditText(this.context).apply {
                hint = "Moana 2 2024"
                setMinLines(3)
                setMaxLines(5)
                isSingleLine = false
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setPadding(16, 16, 16, 16)
            }

            addView(indicacionTextView)
            addView(inputPedido)
        }

        val inputPedido = layout.getChildAt(1) as EditText
        builder.setView(layout)

        builder.setPositiveButton("Enviar") { _, _ ->
            val pedido = inputPedido.text.toString().trim()
            if (pedido.isNotEmpty()) {
                subirPedidoAFirestore(pedido)
            } else {
                // CORRECCIÓN: Se usa 'this' para el Toast
                Toast.makeText(this, "Debe ingresar un pedido", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }
    private fun activarpaquete() {
        // CORRECCIÓN: Se usa 'this' como contexto
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Activacion de Paquete")

        // CORRECCIÓN: Se usa 'this' como contexto para el LinearLayout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 16)

            // CORRECCIÓN: Se usa 'this' como contexto para el TextView
            val indicacionTextView = TextView(this.context).apply {
                text = "Nombre Completo titular de Cuenta que realizo el pago y fecha\n" +
                        "Ejemplo: Ernesto Dias 01/02/25: Paquete Plata"
                textSize = 14f
                setPadding(0, 0, 0, 16)
            }

            // CORRECCIÓN: Se usa 'this' como contexto para el EditText
            val inputPedido = EditText(this.context).apply {
                hint = "Ernesto Dias 01/02/25: Paquete Plata"
                isSingleLine = true
                setTypeface(null, Typeface.BOLD)
                setPadding(16, 16, 16, 16)
            }

            addView(indicacionTextView)
            addView(inputPedido)
        }

        val inputPedido = layout.getChildAt(1) as EditText
        builder.setView(layout)

        builder.setPositiveButton("Registrar") { _, _ ->
            val pedido = inputPedido.text.toString().trim()
            if (pedido.isNotEmpty()) {
                comprobantepago(pedido)
            } else {
                // CORRECCIÓN: Se usa 'this' para el Toast
                Toast.makeText(this, "Debe ingresar numero de referencia o numero de comprobante de pago", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }
    private fun comprobantepago(pedido: String) {
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid

        if (userId != null) {
            val userRef = databaseRef.child("usuarios").child(userId)

            userRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val nombreUsuario = snapshot.child("nombre").getValue(String::class.java) ?: "Nombre no disponible"
                    val emailUsuario = snapshot.child("correo").getValue(String::class.java) ?: "Email no disponible"
                    val puntosActuales = snapshot.child("castv").getValue(Int::class.java) ?: 0

                    val mensaje = """
                Usuario: $nombreUsuario
                Email: $emailUsuario
                Saldo CasTV: $puntosActuales
                Pedido: $pedido
            """.trimIndent()

                    // CORRECCIÓN: Se usa 'this' como contexto
                    AlertDialog.Builder(this)
                        .setTitle("Confirmar Activación de Paquete")
                        .setMessage(mensaje)
                        .setPositiveButton("Registrar") { _, _ ->
                            val pedidoData = hashMapOf(
                                "title" to pedido,
                                "userId" to userId,
                                "email" to emailUsuario,
                                "nombre" to nombreUsuario
                            )

                            FirebaseFirestore.getInstance().collection("pedidosmovies")
                                .add(pedidoData)
                                .addOnSuccessListener {
                                    enviarCorreoNuevoPedido(pedido) // Asegúrate de tener este método
                                    // CORRECCIÓN: Se usa 'this' como contexto
                                    Toast.makeText(
                                        this,
                                        "Actualizaremos tu saldo",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    // CORRECCIÓN: Se usa 'this' como contexto
                                    Toast.makeText(
                                        this,
                                        "Error al enviar pedido: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                        .setNegativeButton("Cancelar") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    // CORRECCIÓN: Se usa 'this' como contexto
                    Toast.makeText(this, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                // CORRECCIÓN: Se usa 'this' como contexto
                Toast.makeText(
                    this,
                    "Error al obtener usuario: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            // CORRECCIÓN: Se usa 'this' como contexto
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }
    }


    private fun mostrarPublicidad() {
        if (yaMostroPublicidad) return
        if (isFinishing || isDestroyed || publicidadDialog?.isShowing == true) return

        yaMostroPublicidad = true

        val view = layoutInflater.inflate(R.layout.dialog_publicidad, null)
        val imgPublicidad = view.findViewById<ImageView>(R.id.imgPublicidad)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrarPublicidad)
        val txtContador = view.findViewById<TextView>(R.id.txtContadorPublicidad)

        publicidadDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(view)
            setCancelable(false)
            show()
        }

        // 🔥 Listar archivos en la carpeta "FulltvPublicidad"
        val storageRef = FirebaseStorage.getInstance().reference.child("FulltvPublicidad")
        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                val items = listResult.items
                if (items.isNotEmpty()) {
                    val randomRef = items.random()
                    randomRef.downloadUrl.addOnSuccessListener { uri ->
                        Glide.with(this)
                            .load(uri)
                            .placeholder(R.drawable.pelifondo) // opcional
                            .error(R.drawable.icono)         // opcional
                            .into(imgPublicidad)
                    }.addOnFailureListener {
                        Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No hay imágenes disponibles", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al acceder a Firebase Storage", Toast.LENGTH_SHORT).show()
            }

        // 🎯 Foco inicial
        btnCerrar.isFocusableInTouchMode = true
        btnCerrar.requestFocus()

        // ⏱️ Contador
        var segundosRestantes = 10
        txtContador.text = "$segundosRestantes s"
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                segundosRestantes--
                if (segundosRestantes > 0) {
                    txtContador.text = "$segundosRestantes s"
                    handler.postDelayed(this, 1000)
                } else {
                    publicidadDialog?.dismiss()
                }
            }
        }
        handler.postDelayed(runnable, 1000)

        // ❌ Botón cerrar
        btnCerrar.setOnClickListener {
            publicidadDialog?.dismiss()
        }
    }


    override fun onDestroy() {
        super.onDestroy() // Es muy importante llamar a super.onDestroy()
        eliminarListener()
        handler.removeCallbacksAndMessages(null)
        publicidadDialog?.dismiss()
        publicidadDialog = null
    }
    private fun iniciarVerificacionDeEstadoDeCuenta() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        if (currentUser.email == "invitado@fulltv.com") return

        val userRef = databaseRef.child("usuarios").child(currentUser.uid)

        userStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // CORRECCIÓN: Ya no se necesita 'mainActivity', se accede directamente.
                if (haProcesadoEliminacion) {
                    Log.d("Main", "Ya se procesó la eliminación. Listener ignorado.")
                    userRef.removeEventListener(this)
                    return
                }

                val estado = snapshot.child("estado").getValue(String::class.java)

                if (!snapshot.exists() || estado == "eliminado") {
                    Log.w("Main", "Cuenta eliminada detectada. Cerrando sesión...")
                    haProcesadoEliminacion = true
                    mostrarDialogoEliminado()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("Main", "Error en el listener de Realtime Database.", error.toException())
            }
        }
        userRef.addValueEventListener(userStatusListener!!)
    }

    private fun mostrarDialogoEliminado() {
        // CAMBIO: Se ajusta la comprobación para Activity
        if (isFinishing || isDestroyed) {
            Log.w("Main", "La actividad no está en estado válido para mostrar diálogo.")
            return
        }
        eliminarListener()

        var segundosRestantes = 10
        val mensajeInicial = "Tu cuenta ha sido eliminada del sistema.\nSerás redirigido en $segundosRestantes segundos..."

        // CORRECCIÓN: Se usa 'this' como contexto
        val dialog = AlertDialog.Builder(this)
            .setTitle("Cuenta Eliminada")
            .setMessage(mensajeInicial)
            .setCancelable(false)
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.setOnKeyListener { _, _, _ -> true }

        object : CountDownTimer(10_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                segundosRestantes--
                dialog.setMessage("Tu cuenta ha sido eliminada del sistema.\nSerás redirigido en $segundosRestantes segundos...")
            }

            override fun onFinish() {
                if (dialog.isShowing) dialog.dismiss()
                redirigirALogin()
            }
        }.start()
    }
    private fun redirigirALogin() {
        UsuarioEstadoManager.cerrarSesion()
        FirebaseAuth.getInstance().signOut()
        // CORRECCIÓN: Se usa 'this' para getSharedPreferences
        val prefs = getSharedPreferences("tus_preferencias", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // CORRECCIÓN: Se usa 'this' como contexto para el Intent
        val intent = Intent(this, Login::class.java) // Asegúrate de que Login::class.java exista
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Cierra la actividad actual
    }

    private fun eliminarListener() {
        userStatusListener?.let {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                databaseRef.child("usuarios").child(userId).removeEventListener(it)
            }
        }
        userStatusListener = null
    }
    private fun enviarCorreoNuevoPedido(pedido: String) {
        val url = "https://server-csks8w.fly.dev/correo"

        val jsonBody = JSONObject()
        jsonBody.put("titulo", pedido)

        // CORRECCIÓN: Se reemplaza 'requireContext()' por 'this'.
        // En una Activity, 'this' hace referencia al Contexto.
        val requestQueue = Volley.newRequestQueue(this)

        val jsonRequest = object : JsonObjectRequest(
            Request.Method.POST, url, jsonBody,
            Response.Listener { response ->
                Log.d("Email", "✅ Correo enviado exitosamente: $response")
            },
            Response.ErrorListener { error ->
                Log.e("Email", "❌ Error al enviar el correo: ${error.message}")
            }
        ) {
            override fun getBodyContentType(): String = "application/json; charset=utf-8"
        }

        requestQueue.add(jsonRequest)
    }
    private fun cerrarSesion() {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val uid = currentUser.uid
            val userRef = FirebaseDatabase.getInstance().reference
                .child("usuarios")
                .child(uid)
                .child("enlinea")

            // ✅ Primero eliminar el listener y marcar en línea como false
            UsuarioEstadoManager.cerrarSesion()

            // ✅ Luego cerrar sesión
            auth.signOut()

            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

            // ✅ Redirigir al login limpiando el historial
            val intent = Intent(this, Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "No hay sesión activa", Toast.LENGTH_SHORT).show()
        }
    }

    private fun irAlReproductor(movie: Movie) {
        val intent = Intent(this, ApiPeliculaActivity::class.java).apply {
            putExtra("EXTRA_STREAM_URL", movie.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", movie.title)
            putExtra("EXTRA_MOVIE_CASTV", movie.castv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
            putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
            putExtra("EXTRA_COUNTDOWN", movie.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", movie.createdAt.seconds)

        }
        startActivity(intent) // Inicia la actividad del reproductor
    }
    private fun subirPedidoAFirestore(pedido: String) {
        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance()
        val userId = auth.currentUser?.uid

        if (userId != null) {
            val userRef = database.reference.child("usuarios").child(userId)

            userRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val nombreUsuario = snapshot.child("nombre").getValue(String::class.java) ?: "Nombre no disponible"
                    val emailUsuario = snapshot.child("correo").getValue(String::class.java) ?: "Email no disponible"
                    val castvActual = snapshot.child("castv").getValue(Int::class.java) ?: 0

                    val puntosDescontar = 20

                    if (castvActual >= puntosDescontar) {
                        val mensaje = """
                    Usuario: $nombreUsuario
                    Email: $emailUsuario
                    Saldo CasTV: $castvActual
                    Valor CasTV: $puntosDescontar
                    Pedido: $pedido
                """.trimIndent()

                        // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                        AlertDialog.Builder(this)
                            .setTitle("Confirmar Pedido")
                            .setMessage(mensaje)
                            .setPositiveButton("Confirmar") { _, _ ->
                                val pedidoData = hashMapOf(
                                    "title" to pedido,
                                    "userId" to userId,
                                    "email" to emailUsuario,
                                    "nombre" to nombreUsuario,
                                    "CasTV" to puntosDescontar.toString()
                                )

                                FirebaseFirestore.getInstance().collection("pedidosmovies")
                                    .add(pedidoData)
                                    .addOnSuccessListener {
                                        // Asegúrate de que estos métodos también existan en tu Activity
                                        descontarPuntos(userId, puntosDescontar)
                                        enviarCorreoNuevoPedido(pedido)
                                    }
                                    .addOnFailureListener { e ->
                                        // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                                        Toast.makeText(
                                            this,
                                            "Error al enviar pedido: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                            .setNegativeButton("Cancelar") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    } else {
                        // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                        Toast.makeText(
                            this,
                            "No tienes suficientes puntos",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                    Toast.makeText(this, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                Toast.makeText(
                    this,
                    "Error al obtener usuario: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }
    }
    private fun descontarPuntos(userId: String, puntosADescontar: Int) {
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val castvActual = snapshot.child("castv").getValue(Int::class.java) ?: 0

                if (castvActual >= puntosADescontar) {
                    val nuevoCastv = castvActual - puntosADescontar

                    userRef.child("castv").setValue(nuevoCastv)
                        .addOnSuccessListener {
                            // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                            Toast.makeText(
                                this,
                                "Pedido enviado y CasTV descontado",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                            Toast.makeText(
                                this,
                                "Error al descontar CasTV: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                    Toast.makeText(
                        this,
                        "No tienes suficientes CasTV para esta acción",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                Toast.makeText(this, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
            Toast.makeText(
                this,
                "Error al obtener usuario: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun obtenerNoticia() {
        val db = FirebaseFirestore.getInstance()
        val noticiaRef = db.collection("noticia").document("us4vaaf0VPezu9vuc4ns")

        noticiaRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val versionLocal = BuildConfig.VERSION_NAME
                    val mensajeBanner = document.getString("banner") ?: ""
                    val versionRemota = document.getString("versionapk") ?: ""
                    versionRemotaGlobal = versionRemota

                    val mensajeFinalBanner = """
                $mensajeBanner

                📲 Instalada: (versión $versionLocal) 🆕 Última: (versión $versionRemota)
            """.trimIndent()

                    // Esta parte es correcta siempre que 'binding' esté inicializado en la Activity
                    binding.txtBanner.apply {
                        text = mensajeFinalBanner
                        visibility = View.VISIBLE
                        isSelected = true
                    }

                    // La lógica de comparación de versiones es correcta
                    if (versionRemota > versionLocal) { // No es necesario .toString() en versionLocal
                        val mensaje = """
                    ¡Tenemos buenas noticias!

                    Una nueva (versión $versionRemota) de la aplicación está disponible.

                    Esta actualización incluye mejoras de rendimiento, nuevas funciones y una experiencia mucho más rápida y estable.

                    🔄 ¡Actualiza ahora para disfrutar la mejor (versión $versionRemota) de FullTV!
                """.trimIndent()

                        val spannable = SpannableString(mensaje).apply {
                            setSpan(
                                RelativeSizeSpan(1.2f),
                                0,
                                length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            setSpan(
                                StyleSpan(Typeface.BOLD),
                                0,
                                25,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }

                        // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                        AlertDialog.Builder(this)
                            .setTitle("🎉 Nueva Versión $versionRemota Disponible")
                            .setMessage(spannable)
                            .setCancelable(false)
                            .setPositiveButton("Actualizar ahora") { _, _ ->
                                versionRemotaGlobal?.let { version ->
                                    // Asegúrate de que este método también exista en tu Activity
                                    descargarActualizacion(version)
                                } ?: run {
                                    // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
                                    Toast.makeText(
                                        this,
                                        "Versión remota no disponible",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                            }
                            .setNegativeButton("Más tarde", null)
                            .show()
                    }
                }
            }
    }
    private fun descargarActualizacion(versionRemota: String) {
        val versionLocal = BuildConfig.VERSION_NAME

        if (esNuevaVersionDisponible(versionRemota, versionLocal))
        {
            // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
            AlertDialog.Builder(this)
                .setTitle("✅ Ya tienes la última versión (versión $versionRemota)")
                .setMessage("No es necesario actualizar. Estás usando la (versión $versionRemota) más reciente de FullTV.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }

        val url = "https://github.com/CreativeMB/FullTV/releases/download/fulltv/FullTV_update.apk"
        val fileName = "FullTV_update.apk"
        // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
        val apkFile = File(this.getExternalFilesDir(null), fileName)

        if (apkFile.exists()) {
            apkFile.delete()
        }

        // CORRECCIÓN: Se usa 'this' como contexto para crear la vista
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            // CORRECCIÓN: Se usa 'this' como contexto
            progressDrawable = ContextCompat.getDrawable(this@Main, android.R.drawable.progress_horizontal)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 20)
            // CORRECCIÓN: Se usa 'this' como contexto
            progressTintList = ContextCompat.getColorStateList(this@Main, android.R.color.holo_red_light)
        }

        // CORRECCIÓN: Se usa 'this' como contexto para crear la vista
        val textoProgreso = TextView(this).apply {
            text = "Preparando descarga..."
            setTextColor(Color.RED)
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        // CORRECCIÓN: Se usa 'this' como contexto para crear la vista
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(progressBar)
            addView(textoProgreso)
        }

        // CORRECCIÓN: Se usa 'this' en lugar de 'requireContext()'
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("📥 Descargando actualización (versión $versionRemota)")
            .setMessage("La descarga de la (versión $versionRemota) ha comenzado. En un momento disfrutarás de las nuevas funciones y mejoras.")
            .setView(layout)
            .setCancelable(false)
            .create()

        progressDialog.show()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Descargando FullTV")
            setDescription("Actualizando aplicación...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(apkFile))
        }

        // CORRECCIÓN: Se usa 'this' para obtener el servicio del sistema
        val downloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val handler = Handler(Looper.getMainLooper())
        var lastProgress = 0

        // El resto de la lógica del Handler es correcta y no necesita cambios.
        handler.post(object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val totalSizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            cursor.close()
                            // Asegúrate de que estos métodos existan en tu Activity
                            if (lastProgress < 100) {
                                simulateFinalProgress(progressBar, textoProgreso, apkFile, progressDialog)
                            } else {
                                textoProgreso.text = "Descarga completada ✅"
                                progressDialog.dismiss()
                                instalarAPK(apkFile)
                            }
                            return
                        }
                        DownloadManager.STATUS_FAILED -> {
                            cursor.close()
                            textoProgreso.text = "❌ Error al descargar"
                            progressDialog.dismiss()
                            return
                        }
                    }

                    if (totalSizeBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalSizeBytes).toInt()
                        if (progress > lastProgress) {
                            lastProgress = progress
                            progressBar.progress = progress
                            textoProgreso.text = "Descargando... $progress%"
                        }
                    } else {
                        textoProgreso.text = "Conectando al servidor..."
                    }
                }
                cursor.close()
                handler.postDelayed(this, 500)
            }
        })
    }
    private fun simulateFinalProgress(
        progressBar: ProgressBar,
        texto: TextView,
        apkFile: File,
        dialog: AlertDialog
    ) {
        val handler = Handler(Looper.getMainLooper())
        var progress = progressBar.progress

        handler.post(object : Runnable {
            override fun run() {
                progress += 5
                if (progress >= 100) {
                    progress = 100
                    progressBar.progress = 100
                    texto.text = "Descarga completada ✅"
                    dialog.dismiss()
                    instalarAPK(apkFile) // Llama a la función corregida
                } else {
                    progressBar.progress = progress
                    texto.text = "Descargando... $progress%"
                    handler.postDelayed(this, 30)
                }
            }
        })
    }


    private fun instalarAPK(apkFile: File) {
        // CORRECCIÓN: Se usa 'this' como contexto y 'packageName' directamente.
        val apkUri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // CORRECCIÓN: Se usa 'this' como contexto para el Toast.
            Toast.makeText(this, "No se pudo abrir el instalador", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onStart() {
        super.onStart()

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid

        // 🟢 Esperamos a que el nodo exista antes de verificar el estado
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(userId)
        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val estado = snapshot.child("estado").getValue(String::class.java)
                if (estado == "eliminado") {
                    mostrarDialogoEliminado()
                    return@addOnSuccessListener
                }

                // ✅ Ya existe, podemos continuar normalmente
                iniciarEscuchaDeUsuario(userId)
            } else {
                // 🕓 Si aún no existe, esperamos 500ms y volvemos a intentar
                Handler(Looper.getMainLooper()).postDelayed({
                    onStart() // reintentar
                }, 500)
            }
        }.addOnFailureListener {
            Log.e("PeliculasFragment", "Error consultando estado", it)
        }

        // 🎧 Audio focus
        val context = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.requestAudioFocus(context)
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        isLoggingOut = false
    }
    private fun iniciarEscuchaDeUsuario(userId: String) {
        datosUsuarioListener = CastvHelper.obtenerDatosUsuario(
            userId = userId,
            onSuccess = { nombre, correo, castv, enlinea ->
                Log.d("PeliculasFragment", "Usuario: $nombre, En línea: $enlinea, Castv: $castv")
            },
            onFailure = {
                Log.e("PeliculasFragment", "Error al obtener datos de usuario", it)
            }
        )

        iniciarVerificacionDeEstadoDeCuenta()
    }


    override fun onStop() {
        super.onStop()

        eliminarListener()

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w("PeliculasFragment", "No hay usuario autenticado. No se realizará ninguna operación.")
            return
        }

        val userId = user.uid
        val ref = FirebaseDatabase.getInstance().getReference("usuarios").child(userId)

        // 🔁 Remover el listener de datos del usuario
        if (datosUsuarioListener != null) {
            ref.removeEventListener(datosUsuarioListener!!)
            datosUsuarioListener = null
        }


        Log.d("PeliculasFragment", "Listener removido correctamente.")
        publicidadDialog?.dismiss()
        publicidadDialog = null
    }
    override fun onBackPressed() {
        val fragmentActual = supportFragmentManager.findFragmentById(R.id.fragment_container)

        // Si hay más de un fragmento en el stack, retrocede normalmente
        if (supportFragmentManager.backStackEntryCount > 0) {
            super.onBackPressed()
            return
        }

        // Si estamos en el fragmento raíz o único, mostrar el diálogo
        mostrarConfirmacionSalida()
    }
    @SuppressLint("SetTextI18n")
    private fun mostrarConfirmacionSalida() {
        var segundosRestantes = 5

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("¿Desea Salir de la aplicación? ($segundosRestantes)")
            .setCancelable(false)
            .setPositiveButton("Sí") { _, _ ->
                finish()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                segundosRestantes--
                if (segundosRestantes > 0) {
                    alertDialog.setTitle("¿Desea Salir de la aplicación? ($segundosRestantes)")
                    handler.postDelayed(this, 1000)
                } else {
                    // Simula hacer clic en el botón "No"
                    alertDialog.dismiss()
                }
            }
        }

        alertDialog.setOnShowListener {
            val btnSi = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnNo = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            val focusSelector = R.drawable.focus_selector
            listOf(btnSi, btnNo).forEach {
                it.setBackgroundResource(focusSelector)
                it.isFocusable = true
                it.isFocusableInTouchMode = true
            }

            // Focus inicial en "No"
            btnNo.requestFocus()

            // Inicia el contador
            handler.postDelayed(runnable, 1000)
        }

        alertDialog.show()
    }
    fun esNuevaVersionDisponible(versionRemota: String, versionLocal: String): Boolean {
        val vRemota = versionRemota.split(".").map { it.toIntOrNull() ?: 0 }
        val vLocal = versionLocal.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(vRemota.size, vLocal.size)
        val remotaPadded = vRemota + List(maxLength - vRemota.size) { 0 }
        val localPadded = vLocal + List(maxLength - vLocal.size) { 0 }

        for (i in 0 until maxLength) {
            if (remotaPadded[i] > localPadded[i]) return true
            if (remotaPadded[i] < localPadded[i]) return false
        }

        return false // Son iguales
    }

}