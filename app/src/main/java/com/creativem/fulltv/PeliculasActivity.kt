package com.creativem.tvfullurl

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.creativem.fulltv.MoviesAdapter
import com.creativem.fulltv.R
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.databinding.ActivityPeliculasBinding
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Movie
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PeliculasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPeliculasBinding
    private lateinit var movieAdapter: MoviesAdapter
    private val movieList = mutableListOf<Movie>()

    // --- Firebase & Listeners ---
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }
    private var peliculasListener: ValueEventListener? = null
    private var usuariosListener: ValueEventListener? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPeliculasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        cargarHeaderReal() // <-- Aquí llamamos a la lógica del encabezado
        escucharCambiosEnPeliculas()

        // Registro inicial del usuario si es nuevo
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            CastvHelper.nuevosusuarios(this, currentUser.displayName ?: "Usuario", currentUser.email!!)
        }
    }

    private fun cargarHeaderReal() {
        // Accedemos al include a través del binding
        val header = binding.headerLayout

        // 1. Datos de Tiempo
        header.textfecha.text = obtenerFechaActual()
        header.textHora.text = obtenerHoraActual()

        val user = auth.currentUser ?: return

        // 2. Foto de Usuario
        Glide.with(this)
            .load(user.photoUrl)
            .placeholder(R.drawable.icono)
            .error(R.drawable.icono)
            .circleCrop()
            .into(header.imagenuser)

        // 3. Datos de Usuario y Saldo (CastvHelper)
        user.email?.let { email ->
            CastvHelper.obtenerDatosUsuario(email,
                onSuccess = { nombre, _, castv, enlinea ->
                    header.textUsuario.text = "\uD83E\uDDD1 $nombre" + if (enlinea) " 🟢" else " 🔴"

                    // Conteo de películas total
                    databaseRef.child("movies").get().addOnSuccessListener { snapshot ->
                        header.textCastv.text = "🎬 Películas: ${snapshot.childrenCount} | ⭐ Castv: $castv"
                    }
                },
                onFailure = { Log.e("Header", "Error en datos usuario") }
            )
        }

        // 4. Listener ON/OFF (Usuarios en tiempo real)
        usuariosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var on = 0; var off = 0
                for (child in snapshot.children) {
                    val conectado = child.child("enlinea").getValue(Boolean::class.java) ?: false
                    if (conectado) on++ else off++
                }
                header.useronline.text = "ON-$on"
                header.useroff.text = "OFF-$off"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        databaseRef.child("usuarios").addValueEventListener(usuariosListener!!)

        // 5. Marquesina Arcoíris de Pedidos
        databaseRef.child("pedidosmovies").limitToLast(10).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val sb = StringBuilder()
                for (p in snapshot.children) {
                    val n = p.child("nombre").value ?: "Alguien"
                    val t = p.child("title").value ?: "Peli"
                    sb.append("🎬 $n pidió: $t        ")
                }
                header.txtActualizacion.apply {
                    text = sb.toString().trim()
                    visibility = View.VISIBLE
                    isSelected = true // Para activar el marquee
                    iniciarAnimacionArcoiris(this)
                }
            }
        }
    }

    private fun iniciarAnimacionArcoiris(view: View) {
        if (view is TextView) {
            ObjectAnimator.ofArgb(
                view, "textColor",
                Color.RED, Color.parseColor("#FF9800"), Color.YELLOW,
                Color.GREEN, Color.BLUE, Color.parseColor("#4B0082"),
                Color.parseColor("#EE82EE"), Color.RED
            ).apply {
                duration = 4000L
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvPeliculas.layoutManager = GridLayoutManager(this, 5)
        movieAdapter = MoviesAdapter(
            movieList,
            onItemClick = { movie -> irAlReproductor(movie) },
            onFocusChange = { movie ->
                if (!movie.imageUrl.isNullOrEmpty()) {
                    actualizarFondo(movie.imageUrl)
                }
            }
        )
        binding.rvPeliculas.adapter = movieAdapter
    }

    private fun escucharCambiosEnPeliculas() {
        peliculasListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                movieList.clear()
                for (child in snapshot.children) {
                    child.getValue(Movie::class.java)?.let {
                        it.id = child.key ?: ""
                        movieList.add(it)
                    }
                }
                movieList.sortByDescending { it.createdAt }
                movieAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        databaseRef.child("movies").addValueEventListener(peliculasListener!!)
    }

    private fun actualizarFondo(url: String) {
        Glide.with(this).load(url).centerCrop().into(binding.imgFondoCinema)
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
        startActivity(intent)
    }

    private fun obtenerFechaActual() = SimpleDateFormat("EEEE dd MM yy", Locale.getDefault()).format(Date())
    private fun obtenerHoraActual() = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date())

    override fun onDestroy() {
        super.onDestroy()
        peliculasListener?.let { databaseRef.child("movies").removeEventListener(it) }
        usuariosListener?.let { databaseRef.child("usuarios").removeEventListener(it) }
        handler.removeCallbacksAndMessages(null)
    }
}