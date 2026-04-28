package com.creativem.fulltv

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
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
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.api.PeliculasApiActivity
import com.creativem.fulltv.api.TMDbApiClient
import com.creativem.fulltv.databinding.ActivityPeliculasBinding
import com.creativem.fulltv.enlinea.UsuarioEstadoManager
import com.creativem.fulltv.menu.MenuPrincipalAdapter
import com.creativem.fulltv.menu.MenuPrincipalItem
import com.creativem.fulltv.peliculas.PeliculasFragment
import com.creativem.fulltv.peliculas.PeliculasValidasActivity
import com.creativem.fulltv.peliculas.Validacioneslista
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Login
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.principal.Nosotros
import com.creativem.fulltv.tv.TvActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.BuildConfig
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
    private val onDownloadComplete = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) { // Verificamos que sea nuestra descarga
                val file = File(getExternalFilesDir(null), "FullTV_update.apk")
                if (file.exists()) {
                    // 🟢 AQUÍ ES DONDE POR FIN SE LLAMA AL MÉTODO
                    instalarAPK(file)
                }
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

        binding.menuPrincipal.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
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
                val estado = snapshot.child("estado").getValue(String::class.java)
                if (!snapshot.exists() || estado == "eliminado") {
                    haProcesadoEliminacion = true
                    mostrarDialogoEliminado()
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
        databaseRef.child("noticia").child("us4vaaf0VPezu9vuc4ns").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val versionLocal = BuildConfig.VERSION_NAME
                val versionRemota = snapshot.child("versionapk").value?.toString() ?: ""
                versionRemotaGlobal = versionRemota

                if (esNuevaVersion(versionRemota, versionLocal)) {
                    mostrarAlertaActualizacion(versionRemota)
                }
            }
        }
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
        val url = "https://github.com/CreativeMB/FullTV/releases/download/fulltv/FullTV_update.apk"
        val file = File(getExternalFilesDir(null), "FullTV_update.apk")
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("FullTV Update v$version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request) // Guardamos el ID de la descarga

        // 🟢 REGISTRAMOS EL ESCUCHADOR PARA CUANDO TERMINE
        registerReceiver(onDownloadComplete, android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        Toast.makeText(this, "Descargando actualización...", Toast.LENGTH_LONG).show()
    }

    private fun instalarAPK(file: File) {
        // El authority debe ser EXACTAMENTE igual al que pusiste en el AndroidManifest.xml
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("Instalador", "Error al abrir APK: ${e.message}")
            Toast.makeText(this, "No se pudo abrir el instalador", Toast.LENGTH_LONG).show()
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
                    publicidadDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
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
        val input = EditText(this).apply { hint = "Ej: Moana 2 (2024)" }
        AlertDialog.Builder(this)
            .setTitle("Solicitar Película")
            .setView(input)
            .setPositiveButton("Pedir (20 CasTV)") { _, _ ->
                val p = input.text.toString().trim()
                if (p.isNotEmpty()) procesarPedido(p)
            }.show()
    }

    private fun procesarPedido(titulo: String) {
        val uid = auth.currentUser?.uid ?: return
        databaseRef.child("usuarios").child(uid).get().addOnSuccessListener { snap ->
            val saldo = snap.child("castv").getValue(Int::class.java) ?: 0
            if (saldo >= 20) {
                val data = hashMapOf(
                    "title" to titulo,
                    "userId" to uid,
                    "nombre" to (snap.child("nombre").value ?: "Usuario"),
                    "email" to (snap.child("correo").value ?: ""),
                    "createdAt" to ServerValue.TIMESTAMP
                )
                databaseRef.child("pedidosmovies").push().setValue(data).addOnSuccessListener {
                    databaseRef.child("usuarios").child(uid).child("castv").setValue(saldo - 20)
                    enviarCorreoNotificacion(titulo)
                    Toast.makeText(this, "Pedido enviado!", Toast.LENGTH_SHORT).show()
                }
            } else Toast.makeText(this, "Saldo insuficiente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun activarpaquete() {
        val input = EditText(this).apply { hint = "Nombre y Fecha del Pago" }
        AlertDialog.Builder(this)
            .setTitle("Activar Paquete")
            .setView(input)
            .setPositiveButton("Registrar") { _, _ ->
                val p = input.text.toString().trim()
                if (p.isNotEmpty()) {
                    val uid = auth.currentUser?.uid ?: return@setPositiveButton
                    val data = hashMapOf("title" to p, "userId" to uid, "createdAt" to ServerValue.TIMESTAMP)
                    databaseRef.child("pedidosmovies").push().setValue(data)
                    Toast.makeText(this, "Revisaremos tu pago pronto", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun enviarCorreoNotificacion(pedido: String) {
        val url = "https://server-csks8w.fly.dev/correo"
        val body = JSONObject().put("titulo", pedido)
        val request = object : JsonObjectRequest(Method.POST, url, body, null, null) {}
        Volley.newRequestQueue(this).add(request)
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
            val validador = com.creativem.fulltv.peliculas.Validaciones()

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

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(onDownloadComplete)
        } catch (e: Exception) {
            // Ya estaba desregistrado
        }
        publicidadDialog?.dismiss()

        peliculasListener?.let { databaseRef.child("movies").removeEventListener(it) }
        publicidadDialog?.dismiss()
        handler.removeCallbacksAndMessages(null)
    }
}