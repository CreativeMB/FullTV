package com.creativem.fulltv.peliculas

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.api.PeliculasApiActivity
import com.creativem.fulltv.api.TMDbApiClient
import com.creativem.fulltv.databinding.ActivityPeliculasBinding
import com.creativem.fulltv.enlinea.UsuarioEstadoManager
import com.creativem.fulltv.menu.MenuPrincipalAdapter
import com.creativem.fulltv.menu.MenuPrincipalItem
import com.creativem.fulltv.peliculasvalidas.PeliculasValidasActivity
import com.creativem.fulltv.peliculasvalidas.Validaciones
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Login
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.principal.Nosotros
import com.creativem.fulltv.tv.TvActivity
import com.google.firebase.auth.FirebaseAuth
import com.creativem.fulltv.BuildConfig
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class PeliculasActivity : AppCompatActivity() {
    private var yaTieneListener = false
    private lateinit var binding: ActivityPeliculasBinding
    private lateinit var movieAdapter: MoviesAdapter
    private val movieList = mutableListOf<Movie>()
    private var esModoGratis = false // Para saber si estamos filtrando por validación o no
    // --- Firebase & Listeners ---
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val databaseRef: DatabaseReference = FirebaseDatabase.getInstance().reference
    private var peliculasListener: ValueEventListener? = null
    private var userStatusListener: ValueEventListener? = null
    private var downloadId: Long = -1
    // --- Estado y Control ---
    private var haProcesadoEliminacion = false
    private var yaMostroPublicidad = false
    private var publicidadDialog: Dialog? = null

    private var versionRemotaGlobal: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private var progressDialog: AlertDialog? = null
    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                progressDialog?.dismiss()
                val query = DownloadManager.Query().setFilterById(downloadId)
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val cursor = dm.query(query)

                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                        val file = File(getExternalFilesDir(null), "FullTV_update.apk")
                        if (file.exists()) {
                            instalarAPK(file)
                        }
                    } else {
                        Toast.makeText(context, "Error en la descarga", Toast.LENGTH_SHORT).show()
                    }
                }
                cursor.close()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔧 Configuración Visual TV (Pantalla Completa)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        binding = ActivityPeliculasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔄 Botón atrás personalizado
        onBackPressedDispatcher.addCallback(this) {
            mostrarConfirmacionSalida()
        }
        CoroutineScope(Dispatchers.IO).launch {
            Validacioneslista.cargarPeliculas()
        }


        // 🚀 Iniciar Componentes
        setupMenuHorizontal()
        setupMovieGrid()

        // 🛡️ Seguridad, Actualizaciones y Publicidad
        iniciarVerificacionDeEstadoDeCuenta()
        obtenerNoticiaYActualizaciones()
        mostrarPublicidad()

        // 📊 Registro de Usuario
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            CastvHelper.nuevosusuarios(this, currentUser.displayName ?: "Usuario", currentUser.email!!)
        }
    }

    // ==========================================
    // 1. CONFIGURACIÓN DE UI
    // ==========================================
    private fun setupMenuHorizontal() {
        val menuItems = listOf(
            "Inicio", "TV", "Gratis", "Peliculas", "Buscar",
            "Pedir", "Paquete", "Pago", "Cerrar"
        )
        val menuIcons = listOf(
            R.drawable.home,
            R.drawable.tv, R.drawable.cartelera,
            R.drawable.cine, R.drawable.buscar, R.drawable.pedido,
            R.drawable.activacion, R.drawable.pago, R.drawable.cerrrar
        )


        // Asegúrate de usar los R.drawable correspondientes (aquí puse IDs de ejemplo)
        val menuList = menuItems.mapIndexed { i, name ->
            MenuPrincipalItem(name, menuIcons[i])
        }

        val adapter = MenuPrincipalAdapter(menuList) { item ->
            when (item.name) {
                "Inicio" -> navegarInicio()
                "Gratis" -> navegarGratis() // En esta pantalla, Inicio y Gratis suelen ser lo mismo
                "Buscar" -> buscarPeliculaDialogo()
                "Pedir" -> mostrarDialogoPedido()
                "Paquete" -> activarpaquete()
                "Peliculas" -> navegarAPeliculasApi()
                "Pago" -> {
                    val intent = Intent(this, Nosotros::class.java)
                    startActivity(intent)
                }

                "TV" -> navegarATv()
                "Cerrar" -> cerrarSesion()
                else -> Toast.makeText(this, "${item.name} seleccionado", Toast.LENGTH_SHORT).show()
            }
        }

        binding.menuPrincipal.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.menuPrincipal.adapter = adapter
    }
    fun navegarInicio() {
        // En lugar de abrir la actividad de nuevo, subimos al principio de la lista
        binding.rvPeliculas.smoothScrollToPosition(0)
        binding.rvPeliculas.requestFocus()
    }
    // 2. Funciones de Navegación corregidas para Actividades
    fun navegarGratis() {
        // Aquí abres la actividad de peliculas validas/gratis
        val intent = Intent(this, PeliculasValidasActivity::class.java)
        startActivity(intent)
    }

    fun navegarATv() {
        // Aquí abres la actividad de peliculas validas/gratis
        val intent = Intent(this, TvActivity::class.java)
        startActivity(intent)
    }

    fun navegarAPeliculasApi() {
        // Aquí abres la actividad de peliculas validas/gratis
        val intent = Intent(this, PeliculasApiActivity::class.java)
        startActivity(intent)
    }

    // 3. Configuración de la Grilla Adaptable
    private fun setupMovieGrid() {
        val columnas = calcularColumnas(this)
        binding.rvPeliculas.layoutManager = GridLayoutManager(this, columnas)

        movieAdapter = MoviesAdapter(
            movieList,
            onItemClick = { movie -> irAlReproductor(movie) },
            onFocusChange = { movie ->
                // 🟢 AQUÍ ESTABA EL ERROR (Estaba vacío).
                // Llamamos a la función para pintar el fondo:
                actualizarImagenDeFondo(movie.imageUrl)
            }
        )
        binding.rvPeliculas.adapter = movieAdapter
    }
    private fun actualizarImagenDeFondo(url: String?) {
        if (!url.isNullOrEmpty()) {
            Glide.with(this)
                .load(url)
                .centerCrop() // Asegura que llene toda la pantalla
                .error(R.drawable.pelifondo) // Imagen por defecto si falla
                .into(binding.imgFondo)

            // Opcional: ajustar la transparencia si se ve muy fuerte
            binding.imgFondo.alpha = 0.3f
        }
    }
    // Función para que se adapte a cualquier pantalla (TV, Tablet, Celular)
    private fun calcularColumnas(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val anchoPantallaDp = displayMetrics.widthPixels / displayMetrics.density

        // 160dp es un buen tamaño para posters en TV y móvil
        val anchoMinimoItem = 160
        val columnas = (anchoPantallaDp / anchoMinimoItem).toInt()

        // Retornamos al menos 2 columnas para que no se vea una sola gigante
        return if (columnas >= 2) columnas else 2
    }

    // ==========================================
    // 2. SEGURIDAD: CUENTA ELIMINADA
    // ==========================================

    private fun iniciarVerificacionDeEstadoDeCuenta() {
        val email = auth.currentUser?.email ?: return
        if (email == "invitado@fulltv.com") return

        val correoKey = email.replace(".", "_").replace("@", "_")
        val userRef = databaseRef.child("usuarios").child(correoKey)

        userStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (haProcesadoEliminacion) return

                // 🟢 NUEVA LÓGICA:
                if (snapshot.exists()) {
                    val estado = snapshot.child("estado").getValue(String::class.java)

                    // SOLO lo sacamos si el estado dice "eliminado"
                    if (estado == "eliminado") {
                        haProcesadoEliminacion = true
                        mostrarDialogoEliminado()
                    }
                } else {
                    // Si el snapshot NO EXISTE, significa que el administrador
                    // borró los datos para un RESET. No lo sacamos de la app.
                    Log.d("Seguridad", "Los datos no existen, el usuario puede seguir (Reset)")

                    // OPCIONAL: Podrías llamar aquí a CastvHelper.nuevosusuarios(...)
                    // para que le cree su perfil de nuevo automáticamente si no existe.
                    val currentUser = auth.currentUser
                    CastvHelper.nuevosusuarios(this@PeliculasActivity, currentUser?.displayName ?: "Usuario", email)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        userRef.addValueEventListener(userStatusListener!!)
    }

    private fun mostrarDialogoEliminado() {
        var segundos = 10
        val dialog = AlertDialog.Builder(this)
            .setTitle("Cuenta Eliminada")
            .setMessage("Tu cuenta ha sido eliminada. Serás redirigido en $segundos s...")
            .setCancelable(false)
            .create()
        dialog.show()

        object : CountDownTimer(10000, 1000) {
            override fun onTick(ms: Long) {
                segundos--
                dialog.setMessage("Tu cuenta ha sido eliminada. Serás redirigido en $segundos s...")
            }
            override fun onFinish() {
                redirigirALogin()
            }
        }.start()
    }

    private fun redirigirALogin() {
        UsuarioEstadoManager.cerrarSesion()
        auth.signOut()
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ==========================================
    // 3. ACTUALIZACIONES
    // ==========================================

    private fun obtenerNoticiaYActualizaciones() {
        val versionLocal = BuildConfig.VERSION_NAME
        // Apuntamos al nodo noticia en Realtime Database
        val ref = FirebaseDatabase.getInstance().getReference("noticia").child("us4vaaf0VPezu9vuc4ns")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val versionRemota = snapshot.child("versionapk").getValue(String::class.java) ?: ""

                    if (versionRemota.isNotEmpty() && esNuevaVersion(versionRemota, versionLocal)) {
                        mostrarAlertaActualizacion(versionRemota)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error: ${error.message}")
            }
        })
    }

    private fun esNuevaVersion(remota: String, local: String): Boolean {
        val r = remota.split("."); val l = local.split(".")
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrNull(i)?.toIntOrNull() ?: 0
            val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    private fun mostrarAlertaActualizacion(version: String) {
        AlertDialog.Builder(this)
            .setTitle("🚀 Nueva Versión $version")
            .setMessage("Actualiza FullTV para obtener las mejoras.")
            .setCancelable(false)
            .setPositiveButton("Actualizar") { _, _ -> descargarAPK(version) }
            .setNegativeButton("Luego", null)
            .show()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun descargarAPK(version: String) {
        val url = "https://github.com/CreativeMB/center/releases/download/apk/FullTV.apk"
        val file = File(getExternalFilesDir(null), "FullTV_update.apk")
        if (file.exists()) file.delete()

        // --- DISEÑO DEL DIÁLOGO ROJO (Como tu código viejo) ---
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(Color.RED) // Rojo
        }

        val textoProgreso = TextView(this).apply {
            text = "Iniciando descarga..."
            setTextColor(Color.RED)
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            addView(progressBar)
            addView(textoProgreso)
        }

        progressDialog = AlertDialog.Builder(this)
            .setTitle("📥 Descargando FullTV v$version")
            .setView(layout)
            .setCancelable(false)
            .create()

        progressDialog?.show()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("FullTV v$version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))

        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        // MONITOR DE PROGRESO (Hilo para actualizar la barra)
        val handler = Handler(Looper.getMainLooper())
        val monitor = object : Runnable {
            @SuppressLint("Range")
            override fun run() {
                val q = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(q)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    if (total > 0) {
                        val progress = ((downloaded * 100) / total).toInt()
                        progressBar.progress = progress
                        textoProgreso.text = "Descargando... $progress%"
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cursor.close()
                        textoProgreso.text = "Descarga completada ✅"
                        handler.postDelayed({
                            progressDialog?.dismiss()
                            instalarAPK(file)
                        }, 500)
                        return
                    }
                }
                cursor.close()
                handler.postDelayed(this, 500)
            }
        }
        handler.post(monitor)

        // Registro del Receiver compatible con Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun instalarAPK(file: File) {
        val authority = "${packageName}.provider"
        val uri = FileProvider.getUriForFile(this, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                // No tiene permiso, lo enviamos a configuración
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Autoriza la instalación y vuelve a intentarlo", Toast.LENGTH_LONG).show()
                return
            }
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("Instalador", "Error al abrir: ${e.message}")
        }
    }
    // ==========================================
    // 4. PUBLICIDAD
    // ==========================================

    private fun mostrarPublicidad() {
        if (yaMostroPublicidad) return
        yaMostroPublicidad = true

        val storageRef = FirebaseStorage.getInstance().reference.child("FulltvPublicidad")
        storageRef.listAll().addOnSuccessListener { list ->
            if (list.items.isNotEmpty()) {
                list.items.random().downloadUrl.addOnSuccessListener { uri ->
                    val dialogView = layoutInflater.inflate(R.layout.dialog_publicidad, null)
                    val img = dialogView.findViewById<ImageView>(R.id.imgPublicidad)

                    Glide.with(this).load(uri).into(img)
                    publicidadDialog =
                        Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                    publicidadDialog?.setContentView(dialogView)
                    publicidadDialog?.show()

                    dialogView.findViewById<View>(R.id.btnCerrarPublicidad).setOnClickListener {
                        publicidadDialog?.dismiss()
                    }
                }
            }
        }
    }

    // ==========================================
    // 5. BUSCADOR TMDB
    // ==========================================
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
                                createdAt = System.currentTimeMillis()
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


    // ==========================================
    // 6. PEDIDOS Y CasTV
    // ==========================================

    private fun mostrarDialogoPedido() {
        val input = EditText(this).apply {
            hint = "Ej: Moana 2 (2024)"
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Solicitar Película")
            .setView(input)
            .setPositiveButton("Siguiente") { _, _ ->
                val nombrePeli = input.text.toString().trim()
                if (nombrePeli.isNotEmpty()) {
                    // AQUÍ ESTÁ EL TRUCO: En lugar de procesar, abre el resumen
                    comprobantepago(nombrePeli)
                } else {
                    Toast.makeText(this, "Escribe el nombre de la película", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    private fun comprobantepago(pedido: String) {
        val user = auth.currentUser
        if (user != null && user.email != null) {
            val correoKey = user.email!!.replace(".", "_").replace("@", "_")

            // Buscamos los datos reales para mostrar en el resumen
            databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val nombreUsuario = snapshot.child("nombre").value?.toString() ?: "Usuario"
                    val saldoActual = (snapshot.child("castv").value as? Number)?.toInt() ?: 0
                    val costo = 20

                    // Construimos el mensaje de resumen
                    val mensaje = """
                    🎬 Película: $pedido
                    👤 Usuario: $nombreUsuario
                    💰 Saldo Actual: $saldoActual CasTV
                    📉 Costo: $costo CasTV
                    ------------------------------
                    Saldo final: ${saldoActual - costo} CasTV
                """.trimIndent()

                    // SEGUNDO ALERT DIALOG (Confirmación de envío)
                    AlertDialog.Builder(this)
                        .setTitle("Confirmar Pedido")
                        .setMessage(mensaje)
                        .setCancelable(false)
                        .setPositiveButton("Confirmar y Enviar") { _, _ ->
                            if (saldoActual >= costo) {
                                ejecutarProcesoFinal(correoKey, pedido, costo, nombreUsuario, user.email!!)
                            } else {
                                Toast.makeText(this, "Saldo insuficiente", Toast.LENGTH_LONG).show()
                            }
                        }
                        .setNegativeButton("Corregir") { _, _ -> mostrarDialogoPedido() } // Regresa al anterior
                        .show()
                }
            }
        }
    }
    private fun ejecutarProcesoFinal(correoKey: String, titulo: String, costo: Int, nombre: String, email: String) {
        val data = hashMapOf(
            "title" to titulo,
            "castv" to costo,
            "nombre" to nombre,
            "email" to email,
            "timestamp" to ServerValue.TIMESTAMP
        )

        // 1. Guardamos el pedido
        databaseRef.child("pedidosmovies").push().setValue(data).addOnSuccessListener {
            // 2. Descontamos los puntos usando el método que ya tienes
            descontarPuntos(correoKey, costo)


            Toast.makeText(this, "¡Pedido registrado con éxito!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun descontarPuntos(correoKey: String, puntosADescontar: Int) {
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)

        userRef.child("castv").get().addOnSuccessListener { snapshot ->
            val castvActual = (snapshot.value as? Number)?.toInt() ?: 0

            if (castvActual >= puntosADescontar) {
                val nuevoCastv = castvActual - puntosADescontar

                userRef.child("castv").setValue(nuevoCastv)
                    .addOnSuccessListener {
                        Log.d("ALQUILER_LOG", "✅ Descuento aplicado. Nuevo saldo: $nuevoCastv")
                        Toast.makeText(this, "Pedido enviado exitosamente", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this, Nosotros::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ALQUILER_LOG", "❌ Error al actualizar saldo: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("ALQUILER_LOG", "Error de conexión: ${e.message}")
        }
    }
    private fun activarpaquete() {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val uid = user.uid
        val correoKey = email.replace(".", "_").replace("@", "_")

        // --- DISEÑO DEL DIÁLOGO ---
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val descripcion = TextView(this).apply {
            text = "Selecciona el paquete que pagaste:"
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }

        // Grupo de selección
        val radioGroup = android.widget.RadioGroup(this)

        // Opción Plata
        val rbPlata = android.widget.RadioButton(this).apply {
            text = "Plata: \$5.000 (50 Castv)"
            id = View.generateViewId()
        }
        // Opción Bronce
        val rbBronce = android.widget.RadioButton(this).apply {
            text = "Bronce: \$10.000 (120 Castv)"
            id = View.generateViewId()
        }
        // Opción Oro
        val rbOro = android.widget.RadioButton(this).apply {
            text = "Oro: \$20.000 (250 Castv)"
            id = View.generateViewId()
        }

        radioGroup.addView(rbPlata)
        radioGroup.addView(rbBronce)
        radioGroup.addView(rbOro)
        rbPlata.isChecked = true // Seleccionado por defecto

        val inputReferencia = EditText(this).apply {
            hint = "Escribe Banco y Nombre completo "
            setPadding(20, 30, 20, 30)
        }

        layout.addView(descripcion)
        layout.addView(radioGroup)
        layout.addView(TextView(this).apply { text = "\nDetalles adicionales:"; textSize = 14f })
        layout.addView(inputReferencia)

        // --- MOSTRAR EL DIÁLOGO ---
        AlertDialog.Builder(this)
            .setTitle("💎 Activar Paquete")
            .setView(layout)
            .setPositiveButton("Enviar Reporte") { _, _ ->

                // Determinar qué plan eligió y cuántos puntos son
                val planSeleccionado = when (radioGroup.checkedRadioButtonId) {
                    rbPlata.id -> "PLATA"
                    rbBronce.id -> "BRONCE"
                    rbOro.id -> "ORO"
                    else -> "DESCONOCIDO"
                }

                val puntosPlan = when (radioGroup.checkedRadioButtonId) {
                    rbPlata.id -> 50
                    rbBronce.id -> 120
                    rbOro.id -> 250
                    else -> 0
                }

                val detalle = inputReferencia.text.toString().trim()
                val tituloFinal = "$planSeleccionado - $detalle"

                // EJECUTAR EL GUARDADO
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            databaseRef.child("usuarios").child(correoKey).get().await()
                        }
                        val nombreReal = snapshot.child("nombre").value?.toString() ?: "Usuario"

                        val data = hashMapOf(
                            "title" to tituloFinal,
                            "castv" to puntosPlan, // Aquí ya va el valor real según el plan
                            "email" to email,
                            "nombre" to nombreReal,
                            "timestamp" to ServerValue.TIMESTAMP,
                            "userId" to uid
                        )

                        withContext(Dispatchers.IO) {
                            databaseRef.child("pedidosmovies").push().setValue(data).await()
                        }

                        Toast.makeText(this@PeliculasActivity, "✅ Reporte de $planSeleccionado enviado", Toast.LENGTH_LONG).show()

                    } catch (e: Exception) {
                        Toast.makeText(this@PeliculasActivity, "❌ Error al enviar reporte", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ==========================================
    // 7. LISTA Y REPRODUCTOR
    // ==========================================

    private fun escucharCambiosEnPeliculas() {
        if (yaTieneListener) return // No agregar doble listener

        val moviesRef = databaseRef.child("movies")
        peliculasListener = moviesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val nuevasPeliculas = mutableListOf<Movie>()
                    val yaValidadas = Validacioneslista.obtenerPeliculasValidas().map { it.id }.toSet()

                    for (child in snapshot.children) {
                        val movie = child.getValue(Movie::class.java)
                        if (movie != null) {
                            val movieConId = movie.copy(id = child.key ?: "")
                            if (yaValidadas.contains(movieConId.id)) {
                                movieConId.isValid = true
                            }
                            nuevasPeliculas.add(movieConId)
                        }
                    }

                    // 🟢 MEJORA: No hacemos clear() inmediato.
                    // Ordenamos y comparamos antes de asignar para evitar parpadeos.
                    val listaOrdenada = nuevasPeliculas.sortedByDescending { it.createdAt }

                    movieList.clear()
                    movieList.addAll(listaOrdenada)
                    movieAdapter.notifyDataSetChanged()

                    if (!Validacioneslista.yaCargado()) {
                        validarYActualizarVistasEnVivo()
                    }
                    yaTieneListener = true
                }
            }
            override fun onCancelled(error: DatabaseError) {
                yaTieneListener = false
            }
        })
    }
    private fun validarYActualizarVistasEnVivo() {
        // No borramos nombres, usamos la lógica masiva local
        CoroutineScope(Dispatchers.Main).launch {
            val validador = Validaciones()

            // Hacemos una copia para no tener errores de concurrencia
            val listaActual = ArrayList(movieList)

            // Lanzamos la validación de cada película INDEPENDIENTEMENTE
            listaActual.forEach { movie ->
                launch(Dispatchers.Main) {
                    // Cada película se valida en su propio "hilo" de corrutina
                    val esValida = withContext(Dispatchers.IO) {
                        validador.isUrlValid(movie.streamUrl)
                    }

                    // Buscamos la posición por si la lista se movió (scroll)
                    val posicionActual = movieList.indexOfFirst { it.id == movie.id }
                    if (posicionActual != -1) {
                        movieList[posicionActual].isValid = esValida
                        // 🔄 ACTUALIZA LA VISTA AL INSTANTE (una por una)
                        movieAdapter.notifyItemChanged(posicionActual)
                    }
                }
            }

            // Al final, guardamos en el objeto para que otras actividades lo usen
            // Pero el usuario ya vio los resultados uno por uno antes de que esto termine
            Validacioneslista.cargarPeliculas()
        }
    }

    // Nueva función de apoyo para refrescar etiquetas rápido
    private fun sincronizarConCacheLocal() {
        val validadas = Validacioneslista.obtenerPeliculasValidas().map { it.id }.toSet()

        for (index in movieList.indices) {
            val movie = movieList[index]
            if (validadas.contains(movie.id) && !movie.isValid) {
                movie.isValid = true
                movieAdapter.notifyItemChanged(index)
            }
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
            putExtra("EXTRA_CREATED_AT", movie.createdAt / 1000)

        }
        startActivity(intent) // Inicia la actividad del reproductor
    }

    private fun cerrarSesion() {
        UsuarioEstadoManager.cerrarSesion()
        auth.signOut()
        startActivity(Intent(this, Login::class.java))
        finish()
    }

    private fun mostrarConfirmacionSalida() {
        AlertDialog.Builder(this)
            .setTitle("¿Desea salir de FullTV?")
            .setPositiveButton("Sí") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onStart() {
        super.onStart()

        // Solo iniciamos el listener si la lista está vacía
        if (movieList.isEmpty()) {
            escucharCambiosEnPeliculas()
        } else {
            // Si ya hay películas, solo asegúrate de que las etiquetas estén al día
            sincronizarConCacheLocal()
        }

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        // 1. Limpiar el Receptor de Descargas (APK)
        try {
            unregisterReceiver(onDownloadComplete)
        } catch (e: Exception) {
            // Ya estaba desregistrado o nunca se activó
        }

        // 2. Limpiar Listener de Películas (Realtime Database)
        peliculasListener?.let {
            databaseRef.child("movies").removeEventListener(it)
        }

        // 3. Limpiar Listener de Seguridad (Estado de cuenta)
        // Es vital quitarlo de la ruta exacta del usuario
        val email = auth.currentUser?.email
        if (email != null && userStatusListener != null) {
            val correoKey = email.replace(".", "_").replace("@", "_")
            databaseRef.child("usuarios").child(correoKey).removeEventListener(userStatusListener!!)
        }

        // 4. Cerrar todos los diálogos abiertos para evitar error "WindowLeaked"
        publicidadDialog?.let { if (it.isShowing) it.dismiss() }
        progressDialog?.let { if (it.isShowing) it.dismiss() } // El de la descarga roja

        // 5. Limpiar el Handler (Detiene el monitor de progreso de descarga)
        handler.removeCallbacksAndMessages(null)

        Log.d("PeliculasActivity", "Limpieza de onDestroy completada")
    }
}