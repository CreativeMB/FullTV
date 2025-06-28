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
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.principal.Reloj
import com.creativem.fulltv.menu.MenuItem
import com.creativem.fulltv.menu.MenuPresenter
import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.firestore.FirebaseFirestore
import jp.wasabeef.glide.transformations.BlurTransformation
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
import kotlinx.coroutines.delay
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.DownloadManager
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File


class PeliculasFragment : BrowseSupportFragment() {
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val validaciones = Validaciones()
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingContainer: FrameLayout
    private lateinit var binding: FragmentPeliculasBinding
    private val db = FirebaseFirestore.getInstance()
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

                // Actualizar los TextViews con el número de usuarios conectados y desconectados
                binding.useronline.text = "ON-${usuariosConectados.size}"
                binding.useroff.text = "OFF-${usuariosDesconectados.size}"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Connection", "Error al escuchar los usuarios: ${error.message}")
            }
        })



        // Inflar el layout principal
        binding = FragmentPeliculasBinding.bind(requireActivity().findViewById(R.id.main))
        // Inflar el layout de carga (loading overlay)
        loadingContainer =
            inflater.inflate(R.layout.loading_overlay, container, false) as FrameLayout
        progressBar = loadingContainer.findViewById(R.id.progressBar)
        loadingText = loadingContainer.findViewById(R.id.loadingText)

        // Establecer valores iniciales para nombre de usuario y cantidad de Castv
        binding.textUsuario.text = "users" // Cambia [Usuario] por el valor real
        binding.textCastv.text = "Castv" // Cambia el valor según corresponda

        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Iniciar el reloj
        val textHora = binding.textHora
        val textfecha = binding.textfecha
        val reloj = Reloj(textHora, textfecha)
        reloj.startClock()
        // Agregar la vista de entrada a la vista principal
        (view as? ViewGroup)?.addView(loadingContainer)

        // Configura el listener de clics mrnu
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is MenuItem) {
                Log.d("PeliculasValidasFragment", "Menu item clicked: ${item.name}")
                when (item.name) {
                    "Buscar\nPelicula" -> {
                        buscarPeliculaDialogo()
                    }
                    "Pedir\nPelicula" -> {
                        mostrarDialogoPedido()
                    }
                    "Activar\nPaquete" -> {
                        activarpaquete()
                    }

                    "Pelis\nGratis" -> {
                        val intent = Intent(requireContext(), PeliculasValidas::class.java)
                        startActivity(intent)
                    }

                    "¿Como\nPago?" -> {
                        val intent = Intent(requireContext(), Nosotros::class.java)
                        startActivity(intent)
                    }
                    "TV\nGratis" -> {
                        val intent = Intent(requireContext(), Tv::class.java)
                        startActivity(intent)
                    }
                    "Descarga\nActualizacion" -> {
                        descargarActualizacion()
                    }
                    "Cerrar\nCuenta" -> {
                        cerrarSesion() // Llama al método de cerrar sesión
                    }

                    else -> {
                        Toast.makeText(
                            requireContext(),
                            "${item.name} seleccionado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else if (item is Movie) {
                val intent = Intent(context, PlayerPeliculas::class.java)
                intent.putExtra("EXTRA_STREAM_URL", item.streamUrl)
                intent.putExtra("EXTRA_MOVIE_TITLE", item.title) // Título de la película
                intent.putExtra("EXTRA_MOVIE_YEAR", item.year) // Año de la película
                intent.putExtra("EXTRA_MOVIE_IMAGE_URL", item.imageUrl)
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

        obtenerNoticia()
        binding.mainBackgroundImage.setImageDrawable(null)



        adapter = rowsAdapter // Inicializa el adaptador

        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item is Movie) {
//                cargarImagenDeFondo(item.imageUrl)
            } else {
//                restablecerColorFondo()
            }
        }
        escucharCambiosEnPeliculas()
        cargarPeliculas()
        actualizarUsuarioInfo()
        mostrarPublicidad()

        viewLifecycleOwner.lifecycleScope.launch {
            // Esperar hasta que el RecyclerView de Leanback esté listo
            delay(1000)
            Log.d("DEBUG", "Tamaño de rowsAdapter: ${rowsAdapter.size()}")

            if (rowsAdapter.size() > 0 && rowsAdapter.get(0) is ListRow) {
                val firstRow = rowsAdapter.get(0) as ListRow
                Log.d("DEBUG", "Tamaño del adapter de la primera fila: ${firstRow.adapter.size()}")

                if (firstRow.adapter.size() > 0) {
                    requireActivity().runOnUiThread {
                        setSelectedPosition(0, true, object : ListRowPresenter.SelectItemViewHolderTask(0) {
                            override fun run(holder: androidx.leanback.widget.Presenter.ViewHolder?) {
                                super.run(holder)
                                Log.d("DEBUG", "Seleccionado primer elemento de la primera lista")

                                holder?.view?.post {
                                    holder.view.performClick() // Primer clic
                                    Log.d("DEBUG", "Primer clic realizado")

                                    holder.view.postDelayed({
                                        holder.view.performClick() // Segundo clic (doble clic)
                                        Log.d("DEBUG", "Segundo clic realizado")
                                    }, 200) // Pequeño retraso para simular doble clic
                                }
                            }
                        })
                    }
                }
            }
        }


        // Cargar información del usuario
        val usuarioId =
            FirebaseAuth.getInstance().currentUser?.uid // Obtén el ID del usuario autenticado

        // Llama a obtenerNombreUsuario y obtenerCantidadCastv dentro de una coroutine
        if (usuarioId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val nombreUsuario = validaciones.obtenerNombreUsuario(usuarioId)
                val cantidadCastv = validaciones.obtenerCantidadCastv(usuarioId)
                val cantidadPeliculas = validaciones.obtenerCantidadPeliculas()
                actualizarUsuario(
                    nombreUsuario,
                    cantidadCastv,
                    cantidadPeliculas
                ) // Actualiza la UI con la información del usuario
            }
        } else {
            // Manejo de usuario no autenticado
            Log.e("PeliculasValidasFragment", "No hay usuario autenticado")
            actualizarUsuario(
                "Usuario Desconocido",
                0,
                0
            )  // Actualiza la UI con información predeterminada
        }
    }

    private fun actualizarUsuarioInfo() {
        val usuarioId = FirebaseAuth.getInstance().currentUser?.uid

        if (usuarioId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val nombreUsuario = validaciones.obtenerNombreUsuario(usuarioId)
                val cantidadCastv = validaciones.obtenerCantidadCastv(usuarioId)
                val cantidadPeliculas =
                    validaciones.obtenerCantidadPeliculas() // Obtener cantidad de películas
                actualizarUsuario(
                    nombreUsuario,
                    cantidadCastv,
                    cantidadPeliculas
                ) // Pasar cantidad de películas
            }
        } else {
            Log.e("PeliculasValidasFragment", "No hay usuario autenticado")
            actualizarUsuario("Usuario Desconocido", 0, 0) // Información predeterminada
        }
    }

    // Sobrescribir el método onResume para actualizar la información del usuario
    override fun onResume() {
        super.onResume()
        actualizarUsuarioInfo() // Actualiza la información del usuario cada vez que el fragmento se vuelve visible
    }

    // Función para actualizar el nombre de usuario y la cantidad de Castv
    fun actualizarUsuario(usuario: String, cantidadCastv: Int, cantidadPeliculas: Int) {
        binding.textUsuario.text = usuario
        binding.textCastv.text =
            "Películas: $cantidadPeliculas | CasTV: $cantidadCastv" // Mostrar ambos valores
    }

    fun cargarPeliculas() {
        binding.linearLayout.visibility = View.GONE
        Glide.with(requireContext())
            .load("https://cdn.pixabay.com/photo/2024/10/01/03/12/ai-generated-9087076_1280.jpg")
            .apply(RequestOptions.bitmapTransform(BlurTransformation(15, 3)))
            .centerCrop()
            .into(binding.mainBackgroundImage)

        binding.mainBackgroundImage.apply {
            alpha = 0.6f // Ajusta el nivel de transparencia
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        mostrarCarga("Actualizando biblioteca en línea...")

        viewLifecycleOwner.lifecycleScope.launch {
            val peliculas =
                validaciones.obtenerPeliculasCompleta() // Obtenemos toda la colección
            // Ordenamos por fecha de publicación, siendo la primera la última actualizada
            val peliculasOrdenadas = peliculas.sortedByDescending { it.createdAt }
            ocultarCarga()
            binding.linearLayout.visibility = View.VISIBLE
            updateMovieList(peliculasOrdenadas)
        }
    }

    private fun obtenerNoticia() {
        val db = FirebaseFirestore.getInstance()
        val noticiaRef = db.collection("noticia").document("us4vaaf0VPezu9vuc4ns")

        noticiaRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {

                    // ✅ Banner principal
                    val mensajeBanner = document.getString("banner") ?: ""
                    if (mensajeBanner.isNotBlank()) {
                        binding.txtBanner.apply {
                            text = mensajeBanner
                            visibility = View.VISIBLE
                            isSelected = true
                            requestFocus()
                        }
                    } else {
                        binding.txtBanner.visibility = View.GONE
                    }

                    // ✅ Mensaje de actualización de versión
                    val mensajeActualizacion = document.getString("actualizacion") ?: ""
                    if (mensajeActualizacion.isNotBlank()) {
                        val textView = binding.txtActualizacion
                        textView.text = mensajeActualizacion
                        textView.visibility = View.VISIBLE
                        textView.isSelected = true
                        textView.requestFocus()

                        // 🎨 Animación de cambio de color
                        ObjectAnimator.ofArgb(
                            textView,
                            "textColor",
                            Color.RED,
                            Color.parseColor("#FF9800"), // Naranja
                            Color.YELLOW,
                            Color.GREEN,
                            Color.BLUE,
                            Color.parseColor("#4B0082"), // Índigo
                            Color.parseColor("#EE82EE"), // Violeta
                            Color.RED
                        ).apply {
                            duration = 4000L
                            repeatCount = ValueAnimator.INFINITE
                            repeatMode = ValueAnimator.RESTART
                            start()
                        }
// 📥 Al hacer clic, iniciar descarga con barra de progreso
                        textView.setOnClickListener {
                            descargarActualizacion()
                        }


                    } else {
                        binding.txtActualizacion.visibility = View.GONE
                    }

                } else {
                    binding.txtBanner.visibility = View.GONE
                    binding.txtActualizacion.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                binding.txtBanner.apply {
                    text = "Error al cargar banner"
                    visibility = View.VISIBLE
                    isSelected = true
                }
                binding.txtActualizacion.apply {
                    text = "Error al cargar versión"
                    visibility = View.VISIBLE
                    isSelected = true
                }
            }
    }

    private fun descargarActualizacion() {
        val url = "https://github.com/CreativeMB/FullTV/releases/download/fulltv/FullTV_update.apk"
        val fileName = "FullTV_update.apk"
        val apkFile = File(requireContext().getExternalFilesDir(null), fileName)

        val progressBar = ProgressBar(requireContext()).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }

        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Descargando actualización")
            .setMessage("Por favor espera mientras se descarga la nueva versión de FullTV.")
            .setView(progressBar)
            .setCancelable(false)
            .create()

        progressDialog.show()

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Descargando FullTV")
            setDescription("La actualización se está descargando...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(apkFile))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        progressDialog.dismiss()

                        if (apkFile.exists()) {
                            val apkUri = FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.provider",
                                apkFile
                            )

                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(apkUri, "application/vnd.android.package-archive")
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                            }

                            try {
                                startActivity(installIntent)
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "No se pudo abrir el instalador", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(requireContext(), "❌ No se encontró el archivo APK", Toast.LENGTH_LONG).show()
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), "❌ Error al descargar la actualización", Toast.LENGTH_LONG).show()
                    } else {
                        handler.postDelayed(this, 1000)
                    }
                }
                cursor.close()
            }
        })
    }



    private fun updateMovieList(peliculas: List<Movie>) {
        rowsAdapter.clear()

        // Primero, agregamos el menú
        val menuAdapter = ArrayObjectAdapter(MenuPresenter())
        val menuItems = listOf("TV\nGratis", "Pelis\nGratis", "Pedir\nPelicula", "Buscar\nPelicula", "Activar\nPaquete", "¿Como\nPago?", "Descarga\nActualizacion", "Cerrar\nCuenta")
        val menuIcons = listOf(
            R.drawable.tv,
            R.drawable.cartelera,
            R.drawable.pedido,
            R.drawable.buscar,
            R.drawable.activacion,
            R.drawable.pago,
            R.drawable.descarga,
            R.drawable.cerrrar
        )

        menuItems.forEachIndexed { i, item ->
            menuAdapter.add(MenuItem(item, menuIcons[i]))
        }

        // Agregamos el menú al rowsAdapter
        rowsAdapter.add(ListRow(HeaderItem(3, "Opciones"), menuAdapter))

        // Luego, agregamos el contenido de las películas
        agregarALista(peliculas, "Alquila Pelicula")

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
        val anchoTarjeta = 240 // Define el ancho aproximado de cada tarjeta en píxeles
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

//    private fun cargarImagenDeFondo(url: String) {
//        Glide.with(requireContext())
//            .load(url)
//            .centerCrop()
//            .transition(DrawableTransitionOptions.withCrossFade(1000))
//            .into(binding.mainBackgroundImage)
//    }

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
                actualizarUsuarioInfo()

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
        val intent = Intent(context, PlayerPeliculas::class.java).apply {
            putExtra("EXTRA_STREAM_URL", movie.streamUrl)  // Pasa el URL del stream
            putExtra("EXTRA_MOVIE_TITLE", movie.title)     // Pasa el título de la película
            putExtra("EXTRA_MOVIE_YEAR", movie.year)       // Pasa el año de la película
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
        // Crear un objeto JSON para el correo
        val emailData = mapOf(
            "to" to "fulltvurl@gmail.com", // Cambia esto por el correo del destinatario
            "subject" to "$pedido",
            "text" to "PAGADA: $pedido"
        )

        // Hacer la solicitud POST al servidor que envía el correo
        val url = "https://fulltvurl.glitch.me/sendEmail" // Cambia esto por la URL de tu servidor

        // Usar Volley para hacer la solicitud
        val requestQueue = Volley.newRequestQueue(requireContext()) // Contexto de tu actividad

        val jsonObjectRequest = object : JsonObjectRequest(
            Method.POST, url, JSONObject(emailData),
            Response.Listener { response ->
                Log.d("Email", "Correo enviado exitosamente: ${response.toString()}")
            },
            Response.ErrorListener { error ->
                Log.e("Email", "Error al enviar el correo: ${error.message}")
            }
        ) {}

        requestQueue.add(jsonObjectRequest)
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
        if (!isAdded) return // Evitar que se ejecute si el fragmento ya no está adjunto

        publicidadDialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(R.layout.pulicidad)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCancelable(false)

            val imgPublicidad = findViewById<ImageView>(R.id.imgPublicidad)
            val btnCerrar = findViewById<ImageButton>(R.id.btnCerrar)

            val storageRef = Firebase.storage.reference.child("FulltvPublicidad/1.jpg")

            storageRef.downloadUrl.addOnSuccessListener { uri ->
                if (isAdded) { // Verificar antes de cargar la imagen
                    Glide.with(requireContext())
                        .load(uri.toString())
                        .into(imgPublicidad)
                }
            }.addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
                }
            }

            btnCerrar.setOnClickListener {
                dismissDialog()
            }

            // Cerrar automáticamente en 10 segundos
            Handler(Looper.getMainLooper()).postDelayed({
                dismissDialog()
            }, 10000)

            show()
        }
    }

    // Método seguro para cerrar el diálogo
    private fun dismissDialog() {
        if (isAdded && publicidadDialog?.isShowing == true) {
            publicidadDialog?.dismiss()
            publicidadDialog = null
        }
    }

    // Asegurar que el diálogo se cierre correctamente si el fragmento se destruye
    override fun onDestroyView() {
        dismissDialog()
        super.onDestroyView()
    }


}


