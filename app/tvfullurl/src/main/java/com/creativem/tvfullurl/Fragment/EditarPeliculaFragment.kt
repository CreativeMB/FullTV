package com.creativem.tvfullurl.Fragment

import android.app.AlertDialog
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
private data class SolicitudInterna(
    val id: String = "",
    val email: String = "",
    val userId: String = "",
    val timestamp: Long = 0L
)
class EditarPeliculaFragment : Fragment() {
    private lateinit var binding: FragmentPedidosBinding
    private val databaseRef = FirebaseDatabase.getInstance().reference.child("movies")
    private val rootDatabaseRef = FirebaseDatabase.getInstance().reference
    private lateinit var moviesAdapter: MoviesAdapter
    private var movieList: MutableList<Movie> = mutableListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

    private fun escucharPeliculasEnTiempoReal() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val listaDesglosada = mutableListOf<Movie>()

                for (child in snapshot.children) {
                    val originalMovie = child.getValue(Movie::class.java)
                    originalMovie?.let { movie ->
                        movie.id = child.key ?: ""

                        val solicitudesNode = child.child("solicitudes")
                        if (solicitudesNode.exists() && solicitudesNode.hasChildren()) {
                            // 🟢 DESGLOSE: Si la película tiene múltiples solicitudes, creamos una tarjeta para cada una
                            for (solicitudChild in solicitudesNode.children) {
                                val solId = solicitudChild.child("id").getValue(String::class.java) ?: solicitudChild.key ?: ""
                                val solEmail = solicitudChild.child("email").getValue(String::class.java) ?: ""
                                val solName = solicitudChild.child("userId").getValue(String::class.java) ?: ""
                                val solTime = solicitudChild.child("timestamp").getValue(Long::class.java) ?: 0L

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

                // 🟢 ORDENACIÓN GLOBAL:
                // 1. Las solicitudes pendientes van de primeras, ordenadas de la más antigua a la más reciente (Cola de espera justa).
                // 2. Las películas sin solicitudes van al final, ordenadas por su fecha de creación (createdAt) de forma descendente.
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
            // 🟢 ACCESO DIRECTO: El usuario ya la solicitó, generamos su clave y abrimos confirmación de inmediato
            val usuarioKey = movie.email.replace(".", "_").replace("@", "_")
            val usuarioSeleccion = UsuarioSeleccion(usuarioKey, movie.userId, movie.email)
            mostrarDialogoConfirmacion(usuarioSeleccion, movie)
        } else {
            // No tiene solicitante, abrimos el buscador de usuarios para asignación manual
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

    // NUEVO: Método corregido para extraer dinámicamente el año de la película mediante inspección segura
    private fun obtenerAnioPelicula(movie: Movie): String {
        return try {
            val fields = movie.javaClass.declaredFields

            // 1. Intenta buscar un campo directo llamado "year" (año)
            val campoAnio = fields.firstOrNull { it.name.equals("year", ignoreCase = true) }
            if (campoAnio != null) {
                campoAnio.isAccessible = true
                val valorAnio = campoAnio.get(movie)?.toString()
                if (!valorAnio.isNullOrEmpty()) return valorAnio
            }

            // 2. Si no lo encuentra, busca campos comunes de fecha completa (ej: releaseDate, release_date)
            val campoFecha = fields.firstOrNull {
                it.name.equals("releaseDate", ignoreCase = true) ||
                        it.name.equals("release_date", ignoreCase = true) ||
                        it.name.equals("fechaLanzamiento", ignoreCase = true)
            }
            if (campoFecha != null) {
                campoFecha.isAccessible = true
                val valorFecha = campoFecha.get(movie)?.toString()
                if (!valorFecha.isNullOrEmpty() && valorFecha.length >= 4) {
                    return valorFecha.substring(0, 4) // Extrae los primeros 4 dígitos (ej: "2026")
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }
    private fun guardarAlquilerEnUsuario(usuarioKey: String, nombrePeliculaFormateado: String, minutos: Int, movieId: String, activeRequestId: String) {
        val datosAlquiler = mapOf(
            "countdownMinutes" to minutos,
            "createdAt" to System.currentTimeMillis()
        )

        rootDatabaseRef.child("usuarios")
            .child(usuarioKey)
            .child("alquileres")
            .child(nombrePeliculaFormateado)
            .setValue(datosAlquiler)
            .addOnSuccessListener {

                if (activeRequestId.isNotEmpty()) {
                    // 🟢 ELIMINACIÓN DE SOLICITUD ATENDIDA: Removemos solo la solicitud procesada de la cola
                    databaseRef.child(movieId).child("solicitudes").child(activeRequestId).removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Película asignada y cola de espera actualizada", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Asignada, pero error al actualizar cola: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // En caso de que no tuviera solicitudes en cola, limpiamos campos planos antiguos
                    val liberacionMap = mapOf<String, Any>(
                        "email" to "",
                        "userId" to ""
                    )
                    databaseRef.child(movieId).updateChildren(liberacionMap)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Película asignada correctamente", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    // Diálogo 2: Confirmación con Diseño Estructurado (Corregido)
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

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Sí, Asignar") { _, _ ->
                // Enviamos el ID de la película y el ID de la solicitud activa que se está resolviendo
                guardarAlquilerEnUsuario(usuario.key, tituloConAnio, tiempoAsignadoMinutos, movie.id, movie.activeRequestId)
            }
            .setNegativeButton("No", null)
            .show()
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