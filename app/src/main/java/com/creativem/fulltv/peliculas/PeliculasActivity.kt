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
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.creativem.fulltv.principal.ViewUtils
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
    private var lastFocusedMovie: View? = null
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
                        val file = File(getExternalFilesDir(null), "CineParcheApp-debug.apk")
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

        // --- LÓGICA DE MEMORIA DE FOCO ---
        // 1. Escuchamos cada cambio de foco en la pantalla
        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            // Si el nuevo foco pertenece a la lista de películas, lo guardamos
            if (newFocus != null && isViewDescendantOf(newFocus, binding.rvPeliculas)) {
                lastFocusedMovie = newFocus
            }
        }

        // 2. Obligamos al menú a que al bajar, busque la última película
        binding.menuPrincipal.setOnFocusChangeListener { _, hasFocus ->
            // Si el foco SALE del menú, intentamos recuperar la última posición
            if (!hasFocus && lastFocusedMovie != null) {
                lastFocusedMovie?.requestFocus()
            }
        }



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

        // 📊 Registro de Usuario
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            CastvHelper.nuevosusuarios(this, currentUser.displayName ?: "Usuario", currentUser.email!!)
        }
    }

    // Función auxiliar para saber si una vista está dentro del RecyclerView
    private fun isViewDescendantOf(view: View, parent: ViewGroup): Boolean {
        var current = view.parent
        while (current != null) {
            if (current == parent) return true
            current = current.parent
        }
        return false
    }

    // ==========================================
    // 1. CONFIGURACIÓN DE UI
    // ==========================================
    private fun setupMenuHorizontal() {
        val menuItems = listOf(
            "TV", "Gratis", "Peliculas", "Buscar",
            "Pedir", "Paquete", "Pago", "Cerrar"
        )
        val menuIcons = listOf(
            R.drawable.tv, R.drawable.cartelera,
            R.drawable.cine, R.drawable.buscar, R.drawable.pedido,
            R.drawable.activacion, R.drawable.pagos, R.drawable.cerrar
        )


        // Asegúrate de usar los R.drawable correspondientes (aquí puse IDs de ejemplo)
        val menuList = menuItems.mapIndexed { i, name ->
            MenuPrincipalItem(name, menuIcons[i])
        }

        val adapter = MenuPrincipalAdapter(menuList) { item ->
            when (item.name) {
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
        binding.menuPrincipal.isFocusable = true
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
        // 1. Usamos el objeto central para obtener las columnas
        val columnas = ViewUtils.calcularColumnas(this)

        // 2. Aplicamos el número de columnas al Grid
        binding.rvPeliculas.layoutManager = GridLayoutManager(this, columnas)
// ESTO REEMPLAZA AL XML Y NO DA ERROR
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            binding.rvPeliculas.preserveFocusAfterLayout = true
        }
        movieAdapter = MoviesAdapter(
            movieList,
            onItemClick = { movie -> irAlReproductor(movie) },
            onFocusChange = { movie ->
                // Ahora sí, el fondo se actualiza dinámicamente al mover el foco
                actualizarImagenDeFondo(movie.imageUrl)
            }
        )
        binding.rvPeliculas.adapter = movieAdapter
        binding.rvPeliculas.setHasFixedSize(true)
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
        // Colores de marca CineParche
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        // Título con estilo y color de marca
        val title = SpannableString("🚀 Nueva Versión $version")
        title.setSpan(ForegroundColorSpan(colorDorado), 0, title.length, 0)
        title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Hemos mejorado CineParche para ti. Actualiza ahora para disfrutar de la mejor experiencia.")
            .setCancelable(false) // Obliga al usuario a decidir para mantener la estabilidad
            .setPositiveButton("ACTUALIZAR") { _, _ -> descargarAPK(version) }
            .setNegativeButton("LUEGO", null)
            .create()

        dialog.show()

        // --- Personalización Estética ---

        // Fondo inmersivo azul noche
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))

        // Estilo de los botones
        val btnActualizar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val btnLuego = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val focusSelector = R.drawable.focus_selector

        btnActualizar.apply {
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            setBackgroundResource(focusSelector) // Soporte para TV
            requestFocus() // Foco inmediato en la actualización
        }

        btnLuego.apply {
            setTextColor(Color.LTGRAY)
            setBackgroundResource(focusSelector)
        }

        // Color del mensaje explicativo
        val messageView = dialog.findViewById<TextView>(android.R.id.message)
        messageView?.setTextColor(Color.WHITE)
        messageView?.textSize = 16f
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun descargarAPK(version: String) {
        // Colores de identidad CineParche
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val url = "https://github.com/CreativeMB/center/releases/download/apk/CineParcheApp-debug.apk"
        val file = File(getExternalFilesDir(null), "CineParcheApp-debug.apk")
        if (file.exists()) file.delete()

        // --- DISEÑO PROFESIONAL CINEPARCHE ---
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            // Cambiamos el tinte de la barra de Rojo a Dorado
            progressTintList = ColorStateList.valueOf(colorDorado)
            progressBackgroundTintList = ColorStateList.valueOf(Color.GRAY)
        }

        val textoProgreso = TextView(this).apply {
            text = "Iniciando descarga segura..."
            setTextColor(Color.WHITE) // Blanco para legibilidad sobre azul
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 30, 0, 0)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundColor(colorFondo) // Fondo azul noche
            addView(progressBar)
            addView(textoProgreso)
        }

        // Título con estilo dorado
        val title = SpannableString("📥 Actualizando CineParche v$version")
        title.setSpan(ForegroundColorSpan(colorDorado), 0, title.length, 0)
        title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)

        progressDialog = AlertDialog.Builder(this)
            .setCustomTitle(TextView(this).apply {
                text = title
                textSize = 20f
                setPadding(60, 40, 60, 0)
                setTextColor(colorDorado)
                setBackgroundColor(colorFondo)
            })
            .setView(layout)
            .setCancelable(false)
            .create()

        progressDialog?.show()

        // Eliminamos bordes del sistema
        progressDialog?.window?.setBackgroundDrawable(ColorDrawable(colorFondo))

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("CineParche v$version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))

        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

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
                        // El texto de porcentaje también puede resaltar en dorado
                        if (progress > 0) textoProgreso.setTextColor(colorDorado)
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cursor.close()
                        textoProgreso.text = "Descarga completada ✅"
                        textoProgreso.setTextColor(Color.GREEN)
                        handler.postDelayed({
                            progressDialog?.dismiss()
                            instalarAPK(file)
                        }, 800)
                        return
                    }
                }
                cursor.close()
                handler.postDelayed(this, 500)
            }
        }
        handler.post(monitor)

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

//    private fun mostrarPublicidad() {
//        if (yaMostroPublicidad) return
//        yaMostroPublicidad = true
//
//        val storageRef = FirebaseStorage.getInstance().reference.child("FulltvPublicidad")
//        storageRef.listAll().addOnSuccessListener { list ->
//            if (list.items.isNotEmpty()) {
//                list.items.random().downloadUrl.addOnSuccessListener { uri ->
//                    val dialogView = layoutInflater.inflate(R.layout.dialog_publicidad, null)
//                    val img = dialogView.findViewById<ImageView>(R.id.imgPublicidad)
//
//                    Glide.with(this).load(uri).into(img)
//                    publicidadDialog =
//                        Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
//                    publicidadDialog?.setContentView(dialogView)
//                    publicidadDialog?.show()
//
//                    dialogView.findViewById<View>(R.id.btnCerrarPublicidad).setOnClickListener {
//                        publicidadDialog?.dismiss()
//                    }
//                }
//            }
//        }
//    }

    // ==========================================
    // 5. BUSCADOR TMDB
    // ==========================================
    private fun buscarPeliculaDialogo() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.buscador, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_edit_text)
        val searchResultsView = dialogView.findViewById<ListView>(R.id.list_view)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        progressBar.visibility = View.GONE

        val filteredMovieList = mutableListOf<Movie>()
        val allFirebaseMovies = mutableListOf<Movie>() // Cache local de Firebase

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        searchResultsView.adapter = adapter

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.show()

        // --- FUNCIÓN PARA QUITAR TILDES Y MAYÚSCULAS ---
        fun String.normalizar(): String {
            val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
            val temp = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            return diacritics.replace(temp, "").lowercase()
        }

        // 1. CARGAMOS TODOS LOS DATOS DE FIREBASE UNA SOLA VEZ AL ABRIR EL DIÁLOGO
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = FirebaseDatabase.getInstance().getReference("movies").get().await()
                val loadedMovies = snapshot.children.mapNotNull { doc ->
                    val m = doc.getValue(Movie::class.java)
                    m?.copy(id = doc.key ?: "")
                }
                allFirebaseMovies.addAll(loadedMovies)
            } catch (e: Exception) {
                Log.e("FIREBASE", "Error cargando base de datos local: ${e.message}")
            }
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            private var searchJob: Job? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val queryRaw = s.toString().trim()
                val queryNormalizada = queryRaw.normalizar()

                if (queryRaw.length < 2) {
                    adapter.clear()
                    return
                }

                searchJob?.cancel()
                searchJob = CoroutineScope(Dispatchers.IO).launch {
                    // --- A. FILTRADO EN FIREBASE (LOCAL) ---
                    // Buscamos cualquier coincidencia en el título (infalible)
                    val firebaseMatches = allFirebaseMovies.filter {
                        it.title.normalizar().contains(queryNormalizada)
                    }.map { it.copy(title = "💿 ${it.title}") }

                    // --- B. BÚSQUEDA EN API ---
                    val apiResults = try {
                        val response = TMDbApiClient.service.searchMovies(
                            apiKey = "678193d2c735c6f37840cee035f4d69a",
                            language = "es-MX",
                            query = queryRaw
                        ).execute()

                        if (response.isSuccessful) {
                            response.body()?.results?.map {
                                Movie(
                                    id = it.id.toString(),
                                    title = "🌐 ${it.title} (${it.release_date?.take(4) ?: "N/A"})",
                                    originalTitle = it.original_title,
                                    imageUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}",
                                    streamUrl = "https://tuservidor.com/stream/${it.id}",
                                    castv = 50,
                                    countdownMinutes = 60,
                                    createdAt = System.currentTimeMillis()
                                )
                            } ?: emptyList()
                        } else emptyList()
                    } catch (e: Exception) { emptyList() }

                    // --- C. COMBINAR ---
                    val combined = firebaseMatches + apiResults

                    withContext(Dispatchers.Main) {
                        filteredMovieList.clear()
                        filteredMovieList.addAll(combined)
                        adapter.clear()
                        adapter.addAll(filteredMovieList.map { it.title })
                        adapter.notifyDataSetChanged()
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
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val title = SpannableString("¿Desea cerrar CineParche?")
        title.setSpan(ForegroundColorSpan(colorDorado), 0, title.length, 0)
        title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Si sales ahora, te perderás lo mejor del parche.")
            .setPositiveButton("SÍ, SALIR") { _, _ -> finish() }
            .setNegativeButton("VOLVER (5s)", null) // Texto inicial
            .create()

        dialog.show()

        // Estética del fondo y mensaje
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.WHITE)

        val btnNegativo = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val btnPositivo = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        btnNegativo.setTextColor(colorDorado)
        btnPositivo.setTextColor(Color.WHITE)

        // --- Lógica del Contador ---
        val timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val segundosRestantes = millisUntilFinished / 1000
                btnNegativo.text = "VOLVER (${segundosRestantes}s)"
            }

            override fun onFinish() {
                if (dialog.isShowing) {
                    dialog.dismiss() // Se cierra sin hacer nada
                }
            }
        }

        timer.start()

        // Si el usuario presiona un botón manualmente, detenemos el timer
        dialog.setOnDismissListener { timer.cancel() }
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