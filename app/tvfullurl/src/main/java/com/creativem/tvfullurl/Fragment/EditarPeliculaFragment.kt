package com.creativem.tvfullurl.Fragment

import android.R.attr.orientation
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.NotificationMonitorService
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

    private val solicitudesConocidas = mutableSetOf<String>()
    private var esPrimeraCarga = true

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            verificarYPedirExcepcionBateria()
        } else {
            Toast.makeText(requireContext(), "Las alertas de nuevos pedidos no sonarán sin este permiso", Toast.LENGTH_LONG).show()
            verificarYPedirExcepcionBateria()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root


    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        crearCanalNotificaciones()
        verificarYPedirPermisos()

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

    private fun verificarYPedirPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permisoNotif = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(requireContext(), permisoNotif) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(permisoNotif)
            } else {
                verificarYPedirExcepcionBateria()
            }
        } else {
            verificarYPedirExcepcionBateria()
        }
    }


    @SuppressLint("BatteryLife")
    private fun verificarYPedirExcepcionBateria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val context = requireContext()
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val pName = context.packageName

            if (!pm.isIgnoringBatteryOptimizations(pName)) {
                try {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$pName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("PERMISOS", "No se pudo abrir la solicitud de batería: ${e.message}")
                }
            }
        }

        arrancarServicioMonitoreo()
    }

    private fun arrancarServicioMonitoreo() {
        val serviceIntent = Intent(requireContext(), NotificationMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(requireContext(), serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
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
                            for (solicitudChild in solicitudesNode.children) {
                                val solId = solicitudChild.child("id").getValue(String::class.java) ?: solicitudChild.key ?: ""
                                val solEmail = solicitudChild.child("email").getValue(String::class.java) ?: ""
                                val solName = solicitudChild.child("userId").getValue(String::class.java) ?: ""
                                val solTime = solicitudChild.child("timestamp").getValue(Long::class.java) ?: 0L

                                // LECTURA SEGURA: Fecha en diferentes variables posibles de respaldo
                                val rawFecha = solicitudChild.child("fechaActivacion").value
                                    ?: solicitudChild.child("fecha").value
                                    ?: solicitudChild.child("fechaProgramada").value
                                    ?: solicitudChild.child("fechaProgramacion").value
                                val solFecha = rawFecha?.toString() ?: ""

                                // LECTURA SEGURA: Hora en diferentes variables y soporta texto o números
                                val rawHora = solicitudChild.child("horaActivacion").value
                                    ?: solicitudChild.child("hora").value
                                    ?: solicitudChild.child("horaProgramada").value
                                    ?: solicitudChild.child("horaProgramacion").value

                                val solHora = when (rawHora) {
                                    is Number -> rawHora.toInt()
                                    is String -> rawHora.toIntOrNull() ?: -1
                                    else -> -1
                                }

                                solicitudesCargaActual.add(Pair(solId, movie.title ?: "Sin título"))

                                val movieCopy = child.getValue(Movie::class.java)!!
                                movieCopy.id = child.key ?: ""
                                movieCopy.email = solEmail
                                movieCopy.userId = solName
                                movieCopy.activeRequestId = solId
                                movieCopy.requestTimestamp = solTime

                                movieCopy.fechaActivacion = solFecha
                                movieCopy.horaActivacion = solHora

                                listaDesglosada.add(movieCopy)
                            }
                        } else {
                            movie.email = ""
                            movie.userId = ""
                            movie.activeRequestId = ""
                            movie.requestTimestamp = 0L
                            movie.fechaActivacion = ""
                            movie.horaActivacion = -1
                            listaDesglosada.add(movie)
                        }
                    }
                }

                if (!esPrimeraCarga) {
                    val nuevasSolicitudes = solicitudesCargaActual.filter { it.first !in solicitudesConocidas }
                    for (nueva in nuevasSolicitudes) {
                    }
                } else {
                    esPrimeraCarga = false
                }

                solicitudesConocidas.clear()
                solicitudesConocidas.addAll(solicitudesCargaActual.map { it.first })

                // Ordenamos por pedidos pendientes primero, y luego por fecha de creación (createdAt)
                val listaOrdenada = listaDesglosada.sortedWith(
                    compareByDescending<Movie> { it.requestTimestamp > 0 }
                        .thenBy { if (it.requestTimestamp > 0) it.requestTimestamp else Long.MAX_VALUE }
                        .thenByDescending { it.createdAt as? Long ?: 0L }
                )

                moviesAdapter.updateMovieList(listaOrdenada)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun calcularCreatedAtProgramado(fecha: String, hora: Int): Long {
        if (fecha.isEmpty() || hora == -1) {
            return System.currentTimeMillis()
        }
        return try {
            val cleanFecha = fecha.trim()
            val separators = listOf("-", "/", ".")
            var parts = listOf<String>()

            for (sep in separators) {
                if (cleanFecha.contains(sep)) {
                    parts = cleanFecha.split(sep)
                    break
                }
            }

            if (parts.size < 3) return System.currentTimeMillis()

            val anio: Int
            val mes: Int
            val dia: Int

            if (parts[0].length == 4) {
                anio = parts[0].toInt()
                mes = parts[1].toInt() - 1
                dia = parts[2].toInt()
            } else {
                dia = parts[0].toInt()
                mes = parts[1].toInt() - 1
                anio = parts[2].toInt()
            }

            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, anio)
                set(java.util.Calendar.MONTH, mes)
                set(java.util.Calendar.DAY_OF_MONTH, dia)
                set(java.util.Calendar.HOUR_OF_DAY, hora)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            calendar.add(java.util.Calendar.HOUR, -1)
            calendar.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
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

    private fun guardarAlquilerEnUsuario(
        usuarioKey: String,
        nombrePeliculaFormateado: String,
        movieId: String,
        activeRequestId: String,
        fechaActivacion: String?,
        horaActivacion: Int
    ) {
        val timestampServidor: Any
        val minutosTotales: Int

        if (!fechaActivacion.isNullOrBlank() && horaActivacion != -1) {
            timestampServidor = calcularCreatedAtProgramado(fechaActivacion, horaActivacion)
            minutosTotales = 360
        } else {
            timestampServidor = com.google.firebase.database.ServerValue.TIMESTAMP
            minutosTotales = 300
        }

        val datosAlquiler = mapOf(
            "countdownMinutes" to minutosTotales,
            "createdAt" to timestampServidor
        )

        rootDatabaseRef.child("usuarios")
            .child(usuarioKey)
            .child("alquileres")
            .child(nombrePeliculaFormateado)
            .setValue(datosAlquiler)
            .addOnSuccessListener {
                val updatesPelicula = mutableMapOf<String, Any?>()
                // 🟢 SOLUCIÓN CLAVE: Sincronizamos la fecha de la película con el valor de timestampServidor
                // de modo que si es programado, no se fuerce a "ahora" y no rompa la activación de 24 horas.
                updatesPelicula["createdAt"] = timestampServidor

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
        val tiempoLegible = if (!movie.fechaActivacion.isNullOrBlank() && movie.horaActivacion != -1) {
            val horaAmPm = if (movie.horaActivacion >= 12) "PM" else "AM"
            val hora12 = if (movie.horaActivacion % 12 == 0) 12 else movie.horaActivacion % 12
            "Programada para: ${movie.fechaActivacion} a las $hora12:00 $horaAmPm"
        } else {
            "Inmediato: 300 minutos (5 horas)"
        }

        val tituloOriginal = movie.title ?: "Película sin título"
        val anio = obtenerAnioPelicula(movie)

        val yaTieneAnio = tituloOriginal.trim().endsWith(")") && tituloOriginal.contains("(")
        val tituloConAnio = if (yaTieneAnio || anio.isEmpty()) tituloOriginal else "$tituloOriginal ($anio)"

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_assignment, null)

        val txtMovieTitle: TextView = dialogView.findViewById(R.id.confirmMovieTitle)
        val txtUserName: TextView = dialogView.findViewById(R.id.confirmUserName)
        val txtUserEmail: TextView = dialogView.findViewById(R.id.confirmUserEmail)
        val txtTime: TextView = dialogView.findViewById(R.id.confirmTime)

        txtMovieTitle.text = tituloConAnio
        txtUserName.text = usuario.nombre
        txtUserEmail.text = usuario.correo
        txtTime.text = tiempoLegible

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Asignar al Cliente") { _, _ ->
                guardarAlquilerEnUsuario(
                    usuario.key,
                    tituloConAnio,
                    movie.id,
                    movie.activeRequestId,
                    movie.fechaActivacion,
                    movie.horaActivacion
                )
            }
            .setNeutralButton("Rechazar y Devolver CasTV") { _, _ ->
                val costoPuntos = movie.castv ?: 10
                rechazarYDevolverPuntos(usuario.key, movie.id, movie.activeRequestId, costoPuntos)
            }
            .setNegativeButton("Cerrar", null)
            .create()

        dialog.setOnShowListener {
            val context = requireContext()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.holo_green_dark))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.holo_red_dark))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.darker_gray))
            }
        }

        dialog.show()
    }

    private fun rechazarYDevolverPuntos(
        usuarioKey: String,
        movieId: String,
        activeRequestId: String,
        puntosADevolver: Int
    ) {
        val usuarioRef = rootDatabaseRef.child("usuarios").child(usuarioKey)

        usuarioRef.child("castv").setValue(com.google.firebase.database.ServerValue.increment(puntosADevolver.toLong()))
            .addOnSuccessListener {
                val updatesPelicula = mutableMapOf<String, Any?>()

                if (activeRequestId.isNotEmpty()) {
                    updatesPelicula["solicitudes/$activeRequestId"] = null
                }

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
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}

private data class UsuarioSeleccion(
    val key: String,
    val nombre: String,
    val correo: String
)