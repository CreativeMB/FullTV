package com.creativem.fulltv.peliculas

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.menu.MenuPrincipalItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import android.widget.LinearLayout
import android.text.InputType
import android.widget.ImageButton
import com.creativem.fulltv.databinding.FragmentPeliculasBinding
import com.creativem.fulltv.peliculasvalidas.PeliculasValidas
import com.creativem.fulltv.principal.Login
import com.creativem.fulltv.principal.Nosotros
import com.creativem.fulltv.tv.Tv
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.storage
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.media.AudioManager
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.leanback.app.RowsSupportFragment
import com.creativem.fulltv.api.ApiPeliculaActivity
import java.io.File
import com.creativem.fulltv.BuildConfig
import kotlinx.coroutines.withContext
import android.os.Build
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.creativem.fulltv.menu.MenuPrincipalAdapter
import com.creativem.fulltv.principal.AudioFocusHelper
import java.net.URLEncoder

class PeliculasFragment : RowsSupportFragment() {
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val validaciones = Validaciones()
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingContainer: FrameLayout
    private lateinit var binding: FragmentPeliculasBinding
    private val db = FirebaseFirestore.getInstance()
    private var versionRemotaGlobal: String? = null


    // Declarar las listas de UIDs (Strings)
    val usuariosConectados = mutableListOf<String>()
    val usuariosDesconectados = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflar el layout de BrowseSupportFragment
        val view = super.onCreateView(inflater, container, savedInstanceState)
        // Ya no inflar ni añadir loading_overlay
        binding = FragmentPeliculasBinding.bind(requireActivity().findViewById(R.id.main))



        loadingContainer = binding.loadingOverlay
        progressBar = binding.progressBar
        loadingText = binding.loadingText

        val realtimeDbRef = FirebaseDatabase.getInstance().getReference("usuarios_conectados")

        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserUid != null) {
            val userStatusRef = realtimeDbRef.child(currentUserUid)

            // Escuchar cambios en la conexión
            val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
            connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        // El usuario está conectado, actualizar estado a true
                        userStatusRef.setValue(true)
                            .addOnSuccessListener { Log.d("Connection", "Estado actualizado a conectado") }
                            .addOnFailureListener { e -> Log.e("Connection", "Error al actualizar estado", e) }

                        // Configurar la desconexión automática cuando el usuario pierda conexión
                        userStatusRef.onDisconnect().setValue(false)
                            .addOnSuccessListener { Log.d("Connection", "Estado de desconexión configurado") }
                            .addOnFailureListener { e -> Log.e("Connection", "Error al configurar desconexión", e) }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Connection", "Error al escuchar conexión: ${error.message}")
                }
            })
        } else {
            Log.e("Connection", "Usuario no autenticado")
        }

        if (currentUserUid != null) {
            val userStatusRef = realtimeDbRef.child(currentUserUid)

            // Escuchar cambios en la conexión
            val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
            connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        // El usuario está conectado, actualizar estado a true
                        userStatusRef.setValue(true)
                            .addOnSuccessListener { Log.d("Connection", "Estado actualizado a conectado") }
                            .addOnFailureListener { e -> Log.e("Connection", "Error al actualizar estado", e) }

                        // Configurar la desconexión automática cuando el usuario pierda conexión
                        userStatusRef.onDisconnect().setValue(false)
                            .addOnSuccessListener { Log.d("Connection", "Estado de desconexión configurado") }
                            .addOnFailureListener { e -> Log.e("Connection", "Error al configurar desconexión", e) }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Connection", "Error al escuchar conexión: ${error.message}")
                }
            })
        } else {
            Log.e("Connection", "Usuario no autenticado")
        }

        // Escuchar los cambios en los usuarios conectados y desconectados
        realtimeDbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usuariosConectados.clear()
                usuariosDesconectados.clear()

                for (userSnapshot in snapshot.children) {
                    // Obtener el valor de conexión (true/false) para cada usuario
                    val conectado = userSnapshot.getValue(Boolean::class.java) ?: false

                    // Agregar el usuario a la lista según su estado de conexión
                    if (conectado) {
                        usuariosConectados.add(userSnapshot.key ?: "")  // Agregar solo el UID
                    } else {
                        usuariosDesconectados.add(userSnapshot.key ?: "")  // Agregar solo el UID
                    }
                }

            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Connection", "Error al escuchar los usuarios: ${error.message}")
            }
        })




        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)



// Configura el listener de clics SOLO para items tipo Movie (Leanback)
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Movie) {
                val intent = Intent(context, ApiPeliculaActivity::class.java)
                intent.putExtra("EXTRA_ORIGINAL_TITLE", item.originalTitle)
                intent.putExtra("EXTRA_STREAM_URL", item.streamUrl)
                intent.putExtra("EXTRA_MOVIE_TITLE", item.title)
                intent.putExtra("EXTRA_MOVIE_YEAR", item.year)
                intent.putExtra("EXTRA_MOVIE_IMAGE_URL", item.imageUrl)
                intent.putExtra("EXTRA_COUNTDOWN", item.countdownMinutes)
                startActivity(intent)
            }
        }

        return view
    }

    // Agrega este método para cerrar sesión
    private fun cerrarSesion() {
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()

        // Aquí puedes redirigir al usuario a la pantalla de inicio de sesión o cualquier otra actividad
        val intent = Intent(
            requireContext(),
            Login::class.java
        ) // Cambia a tu actividad de inicio de sesión
        startActivity(intent)
        requireActivity().finish() // Finaliza la actividad actual si es necesario
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cargarMenuPrincipal()


        CoroutineScope(Dispatchers.IO).launch {
            Validacioneslista.cargarPeliculas()

            // ⚠️ Cambio importante: actualizar etiquetas en el hilo principal
            withContext(Dispatchers.Main) {
                actualizarSoloEtiquetas()
            }
        }

        obtenerNoticia()

        binding.mainBackgroundImage.setImageDrawable(null)


        adapter = rowsAdapter // Inicializa el adaptador

        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item is Movie) {
                cargarImagenDeFondo(item.imageUrl)
            } else {
                establecerFondoPorDefecto()
            }
        }
        escucharCambiosEnPeliculas()
        cargarPeliculas()
        mostrarPublicidad()

        // Cargar información del usuario
        val usuarioId =
            FirebaseAuth.getInstance().currentUser?.uid // Obtén el ID del usuario autenticado


    }


    fun cargarPeliculas() {

        establecerFondoPorDefecto()

        mostrarCarga("Actualizando biblioteca en línea...")

        viewLifecycleOwner.lifecycleScope.launch {
            val peliculas =
                validaciones.obtenerPeliculasCompleta() // Obtenemos toda la colección
            // Ordenamos por fecha de publicación, siendo la primera la última actualizada
            val peliculasOrdenadas = peliculas.sortedByDescending { it.createdAt }
            ocultarCarga()

            updateMovieList(peliculasOrdenadas)
        }
    }
    private fun actualizarSoloEtiquetas() {
        for (i in 0 until rowsAdapter.size()) {
            val row = rowsAdapter[i]
            if (row is ListRow) {
                val adapter = row.adapter as? ArrayObjectAdapter ?: continue
                for (j in 0 until adapter.size()) {
                    adapter.notifyArrayItemRangeChanged(j, 1) // 🔁 Solo re-bindea el ítem
                }
            }
        }
    }

    //fondo animado de colores
    private var fondoActual: GradientDrawable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fondoAnimando = false
    private var matrizX = 0f
    private var direccion = 1
    private var colorIndex = 0
    private var brilloOverlayId: Int = View.generateViewId()

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
        intArrayOf(0x66B0E0E6.toInt(), 0x66BA55D3.toInt())  // azul hielo a morado medio
    )



    private fun establecerFondoPorDefecto() {
        if (fondoAnimando) return
        fondoAnimando = true
        handler.removeCallbacksAndMessages(null)

        // 1. Cambios de colores rápidos
        fun cambiarColores() {
            val colores = coloresFluorescentes[colorIndex % coloresFluorescentes.size]
            colorIndex++

            val nuevo = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                colores
            ).apply {
                gradientType = GradientDrawable.LINEAR_GRADIENT
            }

            fondoActual?.let { anterior ->
                val transicion = TransitionDrawable(arrayOf(anterior, nuevo))
                binding.mainBackgroundImage.setImageDrawable(transicion)
                transicion.isCrossFadeEnabled = true
                transicion.startTransition(600)
            } ?: run {
                binding.mainBackgroundImage.setImageDrawable(nuevo)
            }

            binding.mainBackgroundImage.apply {
                alpha = 0.9f
                scaleType = ImageView.ScaleType.MATRIX
            }

            fondoActual = nuevo
        }

        cambiarColores()

        handler.postDelayed(object : Runnable {
            override fun run() {
                cambiarColores()
                handler.postDelayed(this, 1500)
            }
        }, 1500)

        // 2. Movimiento escaneado
        handler.post(object : Runnable {
            override fun run() {
                val matrix = Matrix().apply {
                    matrizX += direccion * 1.5f
                    if (matrizX > 120f || matrizX < -120f) direccion *= -1
                    setTranslate(matrizX, 0f)
                }

                binding.mainBackgroundImage.imageMatrix = matrix
                handler.postDelayed(this, 16)
            }
        })

    }

    private fun cargarMenuPrincipal() {
        val recycler = binding.menuPrincipal
        if (recycler == null) {
            Log.e("MenuPrincipal", "RecyclerView menuPrincipal no está en el layout.")
            return
        }

        val menuItems = listOf(
            "TV Gratis", "Pelis Gratis", "Pedir Pelicula", "Buscar Pelicula",
            "Activar Paquete", "¿Como Pago?", "Descarga", "Cerrar Cuenta"
        )

        val menuIcons = listOf(
            R.drawable.tv, R.drawable.cartelera, R.drawable.pedido, R.drawable.buscar,
            R.drawable.activacion, R.drawable.pago, R.drawable.descarga, R.drawable.cerrrar
        )

        val menuList = menuItems.mapIndexed { i, name ->
            MenuPrincipalItem(name, menuIcons[i])
        }

        val adapter = MenuPrincipalAdapter(menuList) { item ->
            Log.d("PeliculasValidasFragment", "Menu item clicked: ${item.name}")
            when (item.name) {
                "Buscar Pelicula" -> buscarPeliculaDialogo()
                "Pedir Pelicula" -> mostrarDialogoPedido()
                "Activar Paquete" -> activarpaquete()
                "Pelis Gratis" -> startActivity(Intent(requireContext(), PeliculasValidas::class.java))
                "¿Como Pago?" -> startActivity(Intent(requireContext(), Nosotros::class.java))
                "TV Gratis" -> startActivity(Intent(requireContext(), Tv::class.java))
                "Descarga" -> {
                    versionRemotaGlobal?.let { version ->
                        descargarActualizacion(version)
                    } ?: Toast.makeText(requireContext(), "Versión remota no disponible", Toast.LENGTH_SHORT).show()
                }
                "Cerrar Cuenta" -> cerrarSesion()
                else -> Toast.makeText(requireContext(), "${item.name} seleccionado", Toast.LENGTH_SHORT).show()
            }
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
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


    private fun cargarImagenDeFondo(url: String?) {
        handler.removeCallbacksAndMessages(null)
        fondoAnimando = false

        if (url.isNullOrEmpty()) {
            establecerFondoPorDefecto()
            return
        }

        Glide.with(requireContext())
            .load(url)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(1000))
            .error(R.drawable.icono)
            .into(binding.mainBackgroundImage)

        binding.mainBackgroundImage.apply {
            alpha = 0.6f
            scaleType = ImageView.ScaleType.CENTER_CROP
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

                    binding.txtBanner.apply {
                        text = mensajeFinalBanner
                        visibility = View.VISIBLE
                        isSelected = true
                    }

                    // ✅ Mostrar diálogo si hay nueva versión
                    if (versionRemota > versionLocal.toString()) {
                        val mensaje = """
        ¡Tenemos buenas noticias!

        Una nueva (versión $versionRemota) de la aplicación está disponible.

        Esta actualización incluye mejoras de rendimiento, nuevas funciones y una experiencia mucho más rápida y estable.

        🔄 ¡Actualiza ahora para disfrutar la mejor (versión $versionRemota) de FullTV!
    """.trimIndent()

                        val spannable = SpannableString(mensaje).apply {
                            setSpan(RelativeSizeSpan(1.2f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(StyleSpan(Typeface.BOLD), 0, 25, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // Negrita a "¡Tenemos buenas noticias!"
                        }

                        AlertDialog.Builder(requireContext())
                            .setTitle("🎉 Nueva Versión $versionRemota Disponible")
                            .setMessage(spannable)
                            .setCancelable(false)
                            .setPositiveButton("Actualizar ahora") { _, _ ->
                                versionRemotaGlobal?.let { version ->
                                    descargarActualizacion(version)
                                } ?: run {
                                    Toast.makeText(requireContext(), "Versión remota no disponible", Toast.LENGTH_SHORT).show()
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

        // ✅ Validar si la versión ya está instalada
        if (versionRemota == versionLocal) {
            AlertDialog.Builder(requireContext())
                .setTitle("✅ Ya tienes la última versión (versión $versionRemota)")
                .setMessage("No es necesario actualizar. Estás usando la (versión $versionRemota) más reciente de FullTV.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }

        val url = "https://github.com/CreativeMB/FullTV/releases/download/fulltv/FullTV_update.apk"
        val fileName = "FullTV_update.apk"
        val apkFile = File(requireContext().getExternalFilesDir(null), fileName)

        if (apkFile.exists()) {
            apkFile.delete()
        }

        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            progressDrawable = ContextCompat.getDrawable(requireContext(), android.R.drawable.progress_horizontal)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 20)
            progressTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light)
        }

        val textoProgreso = TextView(requireContext()).apply {
            text = "Preparando descarga..."
            setTextColor(Color.RED)
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(progressBar)
            addView(textoProgreso)
        }

        val progressDialog = AlertDialog.Builder(requireContext())
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

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val handler = Handler(Looper.getMainLooper())
        var lastProgress = 0
        var totalSizeBytes = 0L
        var downloadedBytes = 0L

        handler.post(object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    totalSizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            cursor.close()
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
                    instalarAPK(apkFile)
                } else {
                    progressBar.progress = progress
                    texto.text = "Descargando... $progress%"
                    handler.postDelayed(this, 30)
                }
            }
        })
    }


    private fun instalarAPK(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No se pudo abrir el instalador", Toast.LENGTH_LONG).show()
        }
    }



    private fun updateMovieList(peliculas: List<Movie>) {
        rowsAdapter.clear()

        // ✅ Agregar encabezado
        val headerPresenter = HeaderPresenter()
        val headerRowAdapter = ArrayObjectAdapter(headerPresenter)
        headerRowAdapter.add(Object()) // puede ser cualquier objeto

        val headerItem = HeaderItem(" ") // título invisible
        rowsAdapter.add(ListRow(headerItem, headerRowAdapter))


        // Luego, agregamos el contenido de las películas
        agregarALista(peliculas, "")

        // Notificamos el cambio de rango si es necesario
        rowsAdapter.notifyArrayItemRangeChanged(
            rowsAdapter.size() - 1,
            1
        ) // Actualiza el rango para el menú
    }

    // Nueva función para calcular el número de elementos por fila basado en el ancho de pantalla
    private fun calcularElementosPorFila(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        val anchoPantalla = displayMetrics.widthPixels
        val anchoTarjeta = 245 // Define el ancho aproximado de cada tarjeta en píxeles
        return (anchoPantalla / anchoTarjeta).coerceAtLeast(1) // Asegura al menos 1 elemento por fila
    }

    private fun agregarALista(peliculas: List<Movie>, titulo: String) {
        val cardPresenter = CardPresenter()
        val elementosPorFila = calcularElementosPorFila()

        // Dividir la lista en sublistas del tamaño calculado
        val chunkedPeliculas = peliculas.chunked(elementosPorFila)

        // Agregar el encabezado solo para la primera sublista
        if (chunkedPeliculas.isNotEmpty()) {
            val listRowAdapter = ArrayObjectAdapter(cardPresenter).apply {
                addAll(0, chunkedPeliculas[0])
            }
            rowsAdapter.add(ListRow(HeaderItem(0, titulo), listRowAdapter))
        }

        // Agregar las sublistas restantes sin encabezado
        chunkedPeliculas.drop(1).forEach { chunk ->
            val listRowAdapter = ArrayObjectAdapter(cardPresenter).apply {
                addAll(0, chunk)
            }
            rowsAdapter.add(ListRow(null, listRowAdapter))
        }
    }

    private fun mostrarCarga(mensaje: String = "Cargando...") {
        loadingText.text = mensaje
        loadingContainer.visibility = View.VISIBLE
    }

    private fun ocultarCarga() {
        loadingContainer.visibility = View.GONE
    }

    private fun escucharCambiosEnPeliculas() {
        validaciones.obtenerPeliculasRef().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("PeliculasValidasFragment", "Error al escuchar cambios: ${error.message}")
                Toast.makeText(requireContext(), "Error al cargar películas", Toast.LENGTH_SHORT)
                    .show()
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val peliculas = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Movie::class.java)?.run {
                        this.copy(id = doc.id)
                    }
                }

                val peliculasOrdenadas = peliculas.sortedByDescending { it.createdAt }
                updateMovieList(peliculasOrdenadas) // Elimina el segundo parámetro

            } else {
                Log.d("PeliculasValidasFragment", "No se encontraron películas.")
                Toast.makeText(requireContext(), "No hay películas disponibles", Toast.LENGTH_SHORT)
                    .show()
                updateMovieList(emptyList()) // Llama con solo una lista vacía si no hay datos
            }
        }
    }

    private fun buscarPeliculaDialogo() {
        // Creamos el layout para el diálogo usando un EditText, ProgressBar y ListView
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.buscador, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_edit_text)
        val searchResultsView = dialogView.findViewById<ListView>(R.id.list_view)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        // Lista de películas para la búsqueda
        val movieList = mutableListOf<Movie>()
        val filteredMovieList = mutableListOf<Movie>() // Lista para almacenar películas filtradas

        // Adaptador para los resultados de búsqueda
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            filteredMovieList.map { it.title })
        searchResultsView.adapter = adapter

        // Crear el AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()

        // Muestra el ProgressBar y oculta el EditText y la lista al principio
        progressBar.visibility = View.VISIBLE
        searchEditText.visibility = View.GONE
        searchResultsView.visibility = View.GONE

        // Cargar todas las películas desde Firestore sin validaciones
        CoroutineScope(Dispatchers.Main).launch {
            val peliculas =
                validaciones.obtenerPeliculasCompleta() // Obtenemos todas las películas sin filtrar
            movieList.clear()
            movieList.addAll(peliculas)
            filteredMovieList.clear()
            filteredMovieList.addAll(movieList)

            adapter.clear()
            adapter.addAll(filteredMovieList.map { it.title })

            // Oculta el ProgressBar y muestra el EditText y el ListView cuando los datos estén listos
            progressBar.visibility = View.GONE
            searchEditText.visibility = View.VISIBLE
            searchResultsView.visibility = View.VISIBLE

            adapter.notifyDataSetChanged()
        }
        // Listener para la entrada en el EditText
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                val filteredList = movieList.filter { movie ->
                    movie.title.contains(query, ignoreCase = true)
                }
                filteredMovieList.clear()
                filteredMovieList.addAll(filteredList)
                adapter.clear()
                adapter.addAll(filteredMovieList.map { it.title })
                adapter.notifyDataSetChanged()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener para detectar clic en los elementos de la lista
        searchResultsView.setOnItemClickListener { _, _, position, _ ->
            val selectedMovie = filteredMovieList[position]
            irAlReproductor(selectedMovie)
            dialog.dismiss() // Cierra el diálogo después de seleccionar
        }
    }

    private fun irAlReproductor(movie: Movie) {
        val intent = Intent(context, ApiPeliculaActivity::class.java).apply {
            putExtra("EXTRA_STREAM_URL", movie.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", movie.title)
            putExtra("EXTRA_MOVIE_YEAR", movie.year)
            putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
            putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
            putExtra("EXTRA_COUNTDOWN", movie.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", movie.createdAt.seconds)

        }
        startActivity(intent) // Inicia la actividad del reproductor
    }

    // Mostrar el diálogo para realizar un pedido
    private fun mostrarDialogoPedido() {
        // Crear el AlertDialog.Builder
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Solicitar Película")

        // Crear un LinearLayout para contener el TextView y el EditText
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 16)

            // Crear un TextView para indicar al usuario cómo debe ingresar el pedido
            val indicacionTextView = TextView(requireContext()).apply {
                text = "Por favor, ingrese el título y Año de estreno.\n" +
                        "Recuerde; no se pueden Alquilar películas con menos de un mes de estreno."
                textSize = 14f
                setPadding(0, 0, 0, 16) // Espaciado inferior
            }

            // Crear el EditText para ingresar el pedido
            val inputPedido = EditText(requireContext()).apply {
                hint = "Moana 2 2024"
                setMinLines(3) // Mínimo de 3 líneas visibles
                setMaxLines(5) // Máximo de 5 líneas visibles
                isSingleLine = false // Permite múltiples líneas
                setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) // Establece tipo de texto multílínea
                setPadding(16, 16, 16, 16) // Espaciado interno
            }

            // Agregar el TextView y el EditText al LinearLayout
            addView(indicacionTextView)
            addView(inputPedido)
        }

        // Declarar inputPedido fuera del apply para accederlo luego
        val inputPedido = layout.getChildAt(1) as EditText

        // Establecer el layout como la vista del AlertDialog
        builder.setView(layout)

        // Botones del diálogo
        builder.setPositiveButton("Enviar") { _, _ ->
            val pedido = inputPedido.text.toString().trim()
            if (pedido.isNotEmpty()) {
                subirPedidoAFirestore(pedido)
            } else {
                Toast.makeText(requireContext(), "Debe ingresar un pedido", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        // Mostrar el diálogo
        builder.create().show()
    }


    // Subir el pedido a Firestore
    private fun subirPedidoAFirestore(pedido: String) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        val userId = auth.currentUser?.uid

        if (userId != null) {
            val userRef = db.collection("users").document(userId)

            // Obtener datos del usuario
            userRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val nombreUsuario = document.getString("nombre") ?: "Nombre no disponible"
                    val emailUsuario = document.getString("email") ?: "Email no disponible"
                    val puntosActuales = document.getLong("puntos")?.toInt() ?: 0

                    val puntosDescontar = 20 // Establecemos el valor de los puntos a descontar

                    if (puntosActuales >= puntosDescontar) {
                        // Mostrar mensaje de confirmación
                        val mensaje = """
                        Usuario: $nombreUsuario
                        Email: $emailUsuario
                        Saldo CasTV: $puntosActuales
                        Valor CasTV: $puntosDescontar
                        Pedido: $pedido
                 
                    """.trimIndent()

                        AlertDialog.Builder(requireContext())
                            .setTitle("Confirmar Pedido")
                            .setMessage(mensaje)
                            .setPositiveButton("Confirmar") { _, _ ->
                                // Crear el pedido con más campos
                                val pedidoData = hashMapOf(
                                    "title" to pedido,
                                    "userId" to userId,
                                    "email" to emailUsuario,
                                    "nombre" to nombreUsuario,
                                    "year" to puntosDescontar.toString() // Agregar el campo de puntos a descontar
                                )

                                // Subir pedido a la colección
                                db.collection("pedidosmovies").add(pedidoData)
                                    .addOnSuccessListener {
                                        // Descontar puntos
                                        descontarPuntos(userId, puntosDescontar)
                                        enviarCorreoNuevoPedido(pedido)
                                        Toast.makeText(
                                            requireContext(),
                                            "Pedido enviado correctamente",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(
                                            requireContext(),
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
                        Toast.makeText(
                            requireContext(),
                            "No tienes suficientes puntos",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT)
                        .show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error al obtener usuario: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }
    }


    // Descontar puntos del usuario
    private fun descontarPuntos(userId: String, puntosADescontar: Int) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)

        // Obtener puntos actuales y actualizar
        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val puntosActuales = document.getLong("puntos")?.toInt() ?: 0

                if (puntosActuales >= puntosADescontar) {
                    // Actualizar los puntos
                    userRef.update("puntos", puntosActuales - puntosADescontar)
                        .addOnSuccessListener {
                            Toast.makeText(
                                requireContext(),
                                "Pedido enviado y puntos descontados",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                requireContext(),
                                "Error al descontar puntos: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "No tienes suficientes puntos para esta acción",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(
                requireContext(),
                "Error al obtener usuario: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // Método para enviar un correo
      private fun enviarCorreoNuevoPedido(pedido: String) {
        val url = "https://correo-railway.fly.dev/correo"

        val jsonBody = JSONObject()
        jsonBody.put("titulo", pedido) // sin URLEncoder

        val requestQueue = Volley.newRequestQueue(requireContext())

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


    private fun activarpaquete() {
        // Crear el AlertDialog.Builder
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Activacion de Paquete")

        // Crear un LinearLayout para contener el TextView y el EditText
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 16)

            // Crear un TextView para indicar al usuario cómo debe ingresar el pedido
            val indicacionTextView = TextView(requireContext()).apply {
                text = "Nombre Completo titular de Cuenta que realizo el pago y fecha\n" +
                        "Ejemplo: Ernesto Dias 01/02/25: Paquete Plata"
                textSize = 14f
                setPadding(0, 0, 0, 16) // Espaciado inferior
            }

            // Crear el EditText para ingresar el pedido
            val inputPedido = EditText(requireContext()).apply {
                hint = "Ernesto Dias 01/02/25: Paquete Plata"
                setSingleLine(true) // Permitir solo una línea
                setTypeface(null, Typeface.BOLD) // Establecer el texto en negrita
                setPadding(16, 16, 16, 16) // Espaciado interno
            }

            // Agregar el TextView y el EditText al LinearLayout
            addView(indicacionTextView)
            addView(inputPedido)
        }

        // Declarar inputPedido fuera del apply para accederlo luego
        val inputPedido = layout.getChildAt(1) as EditText

        // Establecer el layout como la vista del AlertDialog
        builder.setView(layout)

        // Botones del diálogo
        builder.setPositiveButton("Registrar") { _, _ ->
            val pedido = inputPedido.text.toString().trim()
            if (pedido.isNotEmpty()) {
                comprobantepago(pedido)
            } else {
                Toast.makeText(requireContext(), "Debe ingresar numero de referencia o numero de comprobante de pago", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        // Mostrar el diálogo
        builder.create().show()
    }

    // Subir el comprobante de pago a Firestore
    private fun comprobantepago(pedido: String) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        val userId = auth.currentUser?.uid

        if (userId != null) {
            val userRef = db.collection("users").document(userId)

            // Obtener datos del usuario
            userRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val nombreUsuario = document.getString("nombre") ?: "Nombre no disponible"
                    val emailUsuario = document.getString("email") ?: "Email no disponible"
                    val puntosActuales = document.getLong("puntos")?.toInt() ?: 0

                    val mensaje = """
                    Usuario: $nombreUsuario
                    Email: $emailUsuario
                    Saldo CasTV: $puntosActuales
                    Pedido: $pedido
                """.trimIndent()

                    AlertDialog.Builder(requireContext())
                        .setTitle("Confirmar Activacion de paquete")
                        .setMessage(mensaje)
                        .setPositiveButton("Registrar") { _, _ ->
                            // Crear el pedido sin descontar puntos
                            val pedidoData = hashMapOf(
                                "title" to pedido,
                                "userId" to userId,
                                "email" to emailUsuario,
                                "nombre" to nombreUsuario
                            )

                            // Subir pedido a la colección
                            db.collection("pedidosmovies").add(pedidoData)
                                .addOnSuccessListener {
                                    enviarCorreoNuevoPedido(pedido)
                                    Toast.makeText(
                                        requireContext(),
                                        "Actualisaremos tu saldo",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        requireContext(),
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
                    Toast.makeText(requireContext(), "Usuario no encontrado", Toast.LENGTH_SHORT)
                        .show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(
                    requireContext(),
                    "Error al obtener usuario: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
        }
    }
    private var publicidadDialog: Dialog? = null

    private fun mostrarPublicidad() {
        if (!isAdded || publicidadDialog?.isShowing == true) return

        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_publicidad, null)

        val imgPublicidad = view.findViewById<ImageView>(R.id.imgPublicidad)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrarPublicidad)
        val txtContador = view.findViewById<TextView>(R.id.txtContadorPublicidad)
        val textoPublicidad = view.findViewById<TextView>(R.id.tvPublicidadTexto)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBarPublicidad)

        publicidadDialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(view)
            setCancelable(false)
            show()
        }

        btnCerrar.isFocusableInTouchMode = true
        btnCerrar.requestFocus()

        // Contador regresivo
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

        // Botón cerrar manual
        btnCerrar.setOnClickListener {
            publicidadDialog?.dismiss()
        }

        // Mostrar barra y simular progreso mientras carga
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        var progreso = 0
        val progresoHandler = Handler(Looper.getMainLooper())
        val progresoRunnable = object : Runnable {
            override fun run() {
                if (progreso < 100) {
                    progreso += 25  // carga mucho más rápido
                    if (progreso > 100) progreso = 100
                    progressBar.progress = progreso
                    progresoHandler.postDelayed(this, 40) // cada 40ms
                }
            }
        }
        progresoHandler.post(progresoRunnable)

        // Cargar imagen desde Firebase
        val folderRef = Firebase.storage.reference.child("FulltvPublicidad")
        folderRef.listAll().addOnSuccessListener { listResult ->
            val archivos = listResult.items
            if (archivos.isNotEmpty()) {
                val imagenAleatoria = archivos.random()
                imagenAleatoria.downloadUrl.addOnSuccessListener { uri ->
                    if (isAdded) {
                        Glide.with(requireContext())
                            .load(uri)
                            .into(imgPublicidad)

                        // Ocultar barra y texto cuando termine
                        progressBar.visibility = View.GONE
                        progresoHandler.removeCallbacks(progresoRunnable)
                        textoPublicidad.visibility = View.GONE
                    }
                }.addOnFailureListener {
                    progressBar.visibility = View.GONE
                    progresoHandler.removeCallbacks(progresoRunnable)
                }
            } else {
                progressBar.visibility = View.GONE
                progresoHandler.removeCallbacks(progresoRunnable)
            }
        }.addOnFailureListener {
            progressBar.visibility = View.GONE
            progresoHandler.removeCallbacks(progresoRunnable)
        }
    }
    override fun onStart() {
        super.onStart()

        val context = requireContext() // <-- contexto correcto para Fragment

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.requestAudioFocus(context)
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(
                { /* puedes manejar cambios si quieres */ },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

}


