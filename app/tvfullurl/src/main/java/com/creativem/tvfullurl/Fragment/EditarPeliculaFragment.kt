package com.creativem.tvfullurl.Fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.adapter.MoviesAdapter
import com.creativem.tvfullurl.databinding.FragmentPedidosBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class EditarPeliculaFragment : Fragment() {
    private lateinit var binding: FragmentPedidosBinding
    private val databaseRef = FirebaseDatabase.getInstance().reference.child("movies")
    private val rootDatabaseRef = FirebaseDatabase.getInstance().reference
    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()

    // 🟢 variables de control para identificar nuevas solicitudes en tiempo real sin duplicados
    private val solicitudesConocidas = mutableSetOf<String>()
    private var esPrimeraCarga = true

    // 🟢 Launcher para solicitar permisos de notificación en Android 13+ de forma segura
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar el canal de alertas y validar permisos del dispositivo
        crearCanalNotificaciones()
        validarPermisosNotificacion()

        iniciarRecycler()
        escucharPeliculasEnTiempoReal()

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                moviesAdapter.filter(newText.orEmpty())
                return true
            }
        })
    }

    // 🟢 Crear canal de notificaciones con importancia alta para mostrar banners interactivos
    private fun crearCanalNotificaciones() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Nuevos Pedidos Admin"
            val descriptionText = "Notificaciones de nuevos pedidos en tiempo real"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel("CANAL_ADMIN_PEDIDOS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = requireContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 🟢 Solicitar permisos de envío de notificaciones
    private fun validarPermisosNotificacion() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 🟢 Disparar la notificación con PendingIntent interactivo para abrir la aplicación al tocarla
    @SuppressLint("MissingPermission")
    private fun mostrarNotificacionAdmin(tituloPelicula: String) {
        val intent = Intent(requireContext(), requireActivity()::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flagsPendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            requireContext(),
            0,
            intent,
            flagsPendingIntent
        )

        val builder = androidx.core.app.NotificationCompat.Builder(requireContext(), "CANAL_ADMIN_PEDIDOS")
            .setSmallIcon(R.drawable.baseline_people_alt_24) // Asegúrate de que el icono exista en tus recursos drawable
            .setContentTitle("🔔 ¡Nuevo Pedido Recibido!")
            .setContentText("Se ha solicitado la película: '$tituloPelicula'")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            with(androidx.core.app.NotificationManagerCompat.from(requireContext())) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        requireContext(),
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
                ) {
                    notify(System.currentTimeMillis().toInt(), builder.build())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NOTIFICACION_ADMIN", "Error al lanzar la notificación: ${e.message}")
        }
    }

    private fun escucharPeliculasEnTiempoReal() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaDesglosada = mutableListOf<Movie>()
                val solicitudesCargaActual = mutableListOf<Pair<String, String>>()

                for (child in snapshot.children) {
                    val originalMovie = child.getValue(Movie::class.java)
                    originalMovie?.let { movie ->
                        movie.id = child.key ?: ""

                        val solicitudesNode = child.child("solicitudes")
                        if (solicitudesNode.exists() && solicitudesNode.hasChildren()) {
                            // DESGLOSE: Si la película tiene múltiples solicitudes, creamos una tarjeta para cada una
                            for (solicitudChild in solicitudesNode.children) {
                                val solId = solicitudChild.child("id").getValue(String::class.java) ?: solicitudChild.key ?: ""
                                val solEmail = solicitudChild.child("email").getValue(String::class.java) ?: ""
                                val solName = solicitudChild.child("userId").getValue(String::class.java) ?: ""
                                val solTime = solicitudChild.child("timestamp").getValue(Long::class.java) ?: 0L

                                solicitudesCargaActual.add(Pair(solId, movie.title ?: "Sin título"))

                                // Obtenemos una nueva instancia para cada solicitante
                                val movieCopy = child.getValue(Movie::class.java)!!
                                movieCopy.id = child.key ?: ""
                                movieCopy.email = solEmail
                                movieCopy.userId = solName // Nombre del solicitante
                                movieCopy.activeRequestId = solId
                                movieCopy.requestTimestamp = solTime

                                listaDesglosada.add(movieCopy)
                            }
                        } else {
                            // Si la película no tiene solicitudes, se añade una sola vez vacía
                            movie.email = ""
                            movie.userId = ""
                            movie.activeRequestId = ""
                            movie.requestTimestamp = 0L
                            listaDesglosada.add(movie)
                        }
                    }
                }

                // 🟢 IDENTIFICACIÓN DE NUEVOS PEDIDOS EN TIEMPO REAL:
                if (!esPrimeraCarga) {
                    val nuevasSolicitudes = solicitudesCargaActual.filter { it.first !in solicitudesConocidas }
                    for (nueva in nuevasSolicitudes) {
                        mostrarNotificacionAdmin(nueva.second)
                    }
                } else {
                    esPrimeraCarga = false
                }

                // Actualizamos las solicitudes conocidas para la próxima lectura
                solicitudesConocidas.clear()
                solicitudesConocidas.addAll(solicitudesCargaActual.map { it.first })

                // ORDENACIÓN GLOBAL:
                // Cambia la sección de ORDENACIÓN GLOBAL por esta:
                val listaOrdenada = listaDesglosada.sortedWith(
                    compareByDescending<Movie> { it.createdAt as? Long ?: 0L } // 🟢 Prioridad 1: Lo más reciente/recién editado va primero
                        .thenByDescending { it.requestTimestamp > 0 }          // Prioridad 2: Si coinciden, los que tengan solicitudes
                        .thenBy { if (it.requestTimestamp > 0) it.requestTimestamp else Long.MAX_VALUE }
                )

                moviesAdapter.updateMovieList(listaOrdenada)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun iniciarRecycler() {
        moviesAdapter = MoviesAdapter(movieList,
            onDeleteClick = { id -> deleteMovie(id) },
            onEditClick = { movie -> editMovie(movie) },
            onAssignClick = { movie -> asignarPeliculaAUsuario(movie) },
            isEditable = true
        )
        binding.recyclerViewPedidos.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPedidos.adapter = moviesAdapter
    }

    private fun editMovie(movie: Movie) {
        val bundle = Bundle().apply { putString("movieId", movie.id) }
        findNavController().navigate(R.id.action_editarPeliculaFragment_to_nuevaPeliculaFragment, bundle)
    }

    private fun asignarPeliculaAUsuario(movie: Movie) {
        if (!movie.email.isNullOrBlank()) {
            val usuarioKey = movie.email.replace(".", "_").replace("@", "_")
            val usuarioSeleccion = UsuarioSeleccion(usuarioKey, movie.userId, movie.email)
            mostrarDialogoConfirmacion(usuarioSeleccion, movie)
        } else {
            rootDatabaseRef.child("usuarios").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val listaUsuarios = mutableListOf<UsuarioSeleccion>()

                    for (child in snapshot.children) {
                        val key = child.key ?: continue

                        val nombre = child.child("nombre").getValue(String::class.java)
                            ?: child.child("name").getValue(String::class.java)
                            ?: "Usuario sin nombre"

                        val correo = child.child("correo").getValue(String::class.java)
                            ?: child.child("email").getValue(String::class.java)
                            ?: key.replace("_", ".")

                        listaUsuarios.add(UsuarioSeleccion(key, nombre, correo))
                    }

                    if (listaUsuarios.isNotEmpty()) {
                        mostrarDialogoSeleccion(listaUsuarios, movie)
                    } else {
                        Toast.makeText(requireContext(), "No se encontraron usuarios registrados", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error al obtener usuarios: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun mostrarDialogoSeleccion(usuarios: List<UsuarioSeleccion>, movie: Movie) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Seleccione el destinatario:")

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_selection, null)
        val searchUserEdit: EditText = dialogView.findViewById(R.id.searchUserEdit)
        val recyclerView: androidx.recyclerview.widget.RecyclerView = dialogView.findViewById(R.id.usersRecyclerView)

        val listaOriginal = usuarios
        val listaFiltrada = mutableListOf<UsuarioSeleccion>().apply { addAll(usuarios) }

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        val dialog = builder.setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .create()

        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {

            inner class UserViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
                val nameTxt: TextView = view.findViewById(R.id.userNameTxt)
                val emailTxt: TextView = view.findViewById(R.id.userEmailTxt)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_selection, parent, false)
                return UserViewHolder(view)
            }

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val user = listaFiltrada[position]
                val userHolder = holder as UserViewHolder
                userHolder.nameTxt.text = user.nombre
                userHolder.emailTxt.text = user.correo

                userHolder.itemView.setOnClickListener {
                    dialog.dismiss()
                    mostrarDialogoConfirmacion(user, movie)
                }
            }

            override fun getItemCount(): Int = listaFiltrada.size
        }

        recyclerView.adapter = adapter

        searchUserEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase().trim()
                listaFiltrada.clear()

                if (query.isEmpty()) {
                    listaFiltrada.addAll(listaOriginal)
                } else {
                    val filtrados = listaOriginal.filter {
                        it.nombre.lowercase().contains(query) || it.correo.lowercase().contains(query)
                    }
                    listaFiltrada.addAll(filtrados)
                }

                adapter.notifyDataSetChanged()
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        dialog.show()

        dialog.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.width = (resources.displayMetrics.widthPixels * 0.95).toInt()
            layoutParams.height = (resources.displayMetrics.heightPixels * 0.85).toInt()
            window.attributes = layoutParams
        }
    }

    private fun obtenerAnioPelicula(movie: Movie): String {
        return try {
            val fields = movie.javaClass.declaredFields

            val campoAnio = fields.firstOrNull { it.name.equals("year", ignoreCase = true) }
            if (campoAnio != null) {
                campoAnio.isAccessible = true
                val valorAnio = campoAnio.get(movie)?.toString()
                if (!valorAnio.isNullOrEmpty()) return valorAnio
            }

            val campoFecha = fields.firstOrNull {
                it.name.equals("releaseDate", ignoreCase = true) ||
                        it.name.equals("release_date", ignoreCase = true) ||
                        it.name.equals("fechaLanzamiento", ignoreCase = true)
            }
            if (campoFecha != null) {
                campoFecha.isAccessible = true
                val valorFecha = campoFecha.get(movie)?.toString()
                if (!valorFecha.isNullOrEmpty() && valorFecha.length >= 4) {
                    return valorFecha.substring(0, 4)
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun guardarAlquilerEnUsuario(usuarioKey: String, nombrePeliculaFormateado: String, minutos: Int, movieId: String, activeRequestId: String) {
        // Usamos el servidor de Firebase para la fecha exacta sincronizada
        val timestampServidor = com.google.firebase.database.ServerValue.TIMESTAMP

        val datosAlquiler = mapOf(
            "countdownMinutes" to minutos,
            "createdAt" to timestampServidor
        )

        rootDatabaseRef.child("usuarios")
            .child(usuarioKey)
            .child("alquileres")
            .child(nombrePeliculaFormateado)
            .setValue(datosAlquiler)
            .addOnSuccessListener {

                val updatesPelicula = mutableMapOf<String, Any?>()
                updatesPelicula["createdAt"] = timestampServidor // Sincroniza la película con la hora del servidor

                if (activeRequestId.isNotEmpty()) {
                    updatesPelicula["solicitudes/$activeRequestId"] = null

                    databaseRef.child(movieId).updateChildren(updatesPelicula)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Película asignada y tiempo iniciado", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Error al actualizar película: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    updatesPelicula["email"] = ""
                    updatesPelicula["userId"] = ""

                    databaseRef.child(movieId).updateChildren(updatesPelicula)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Película asignada correctamente", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Error al actualizar película: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al guardar alquiler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mostrarDialogoConfirmacion(usuario: UsuarioSeleccion, movie: Movie) {
        val tiempoAsignadoMinutos = 300
        val tiempoLegible = "$tiempoAsignadoMinutos minutos (${tiempoAsignadoMinutos / 60} horas)"

        val tituloOriginal = movie.title ?: "Película sin título"
        val anio = obtenerAnioPelicula(movie)

        val yaTieneAnio = tituloOriginal.trim().endsWith(")") && tituloOriginal.contains("(")

        val tituloConAnio = if (yaTieneAnio || anio.isEmpty()) {
            tituloOriginal
        } else {
            "$tituloOriginal ($anio)"
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_assignment, null)

        val txtMovieTitle: TextView = dialogView.findViewById(R.id.confirmMovieTitle)
        val txtUserName: TextView = dialogView.findViewById(R.id.confirmUserName)
        val txtUserEmail: TextView = dialogView.findViewById(R.id.confirmUserEmail)
        val txtTime: TextView = dialogView.findViewById(R.id.confirmTime)

        txtMovieTitle.text = tituloConAnio
        txtUserName.text = usuario.nombre
        txtUserEmail.text = usuario.correo
        txtTime.text = tiempoLegible

        // 1. Construimos el diálogo usando .create() en lugar de .show() directo
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Asignar al Cliente") { _, _ ->
                guardarAlquilerEnUsuario(usuario.key, tituloConAnio, tiempoAsignadoMinutos, movie.id, movie.activeRequestId)
            }
            .setNeutralButton("Rechazar y Devolver CasTV") { _, _ ->
                val costoPuntos = movie.castv ?: 10
                rechazarYDevolverPuntos(usuario.key, movie.id, movie.activeRequestId, costoPuntos)
            }
            .setNegativeButton("Cerrar", null)
            .create()

        // 2. Personalizamos los botones cuando el diálogo sea presentado en pantalla
        dialog.setOnShowListener {
            val context = requireContext()

            // Botón Positivo: "Sí, Asignar" -> Color Verde y Negrita
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.holo_green_dark))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            // Botón Neutral: "Rechazar y Devolver" -> Color Rojo y Negrita
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.holo_red_dark))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            // Botón Negativo: "No" -> Color Gris
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.darker_gray))
            }
        }

        // 3. Mostramos el diálogo ya configurado
        dialog.show()
    }

    private fun rechazarYDevolverPuntos(
        usuarioKey: String,
        movieId: String,
        activeRequestId: String,
        puntosADevolver: Int
    ) {
        // 1. Devolvemos los puntos al usuario de forma segura
        val usuarioRef = rootDatabaseRef.child("usuarios").child(usuarioKey)

        usuarioRef.child("castv").setValue(com.google.firebase.database.ServerValue.increment(puntosADevolver.toLong()))
            .addOnSuccessListener {

                // 2. Una vez devueltos los puntos, limpiamos la información de la película
                val updatesPelicula = mutableMapOf<String, Any?>()

                // Eliminamos la solicitud de la lista de espera
                if (activeRequestId.isNotEmpty()) {
                    updatesPelicula["solicitudes/$activeRequestId"] = null
                }

                // Limpiamos los datos del usuario asignado en la película
                updatesPelicula["email"] = ""
                updatesPelicula["userId"] = ""

                databaseRef.child(movieId).updateChildren(updatesPelicula)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Pedido rechazado. Puntos devueltos al usuario ($puntosADevolver CasTV).", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Puntos devueltos, pero error al limpiar película: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al devolver puntos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun deleteMovie(movieId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Confirmar Eliminación")
            .setMessage("¿Está seguro de que desea eliminar esta película de forma permanente? Esta acción no se puede deshacer y borrará también su cola de solicitudes.")
            .setPositiveButton("Sí, Eliminar") { dialog, _ ->
                databaseRef.child(movieId).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Película eliminada con éxito", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}

private data class UsuarioSeleccion(
    val key: String,
    val nombre: String,
    val correo: String
)

