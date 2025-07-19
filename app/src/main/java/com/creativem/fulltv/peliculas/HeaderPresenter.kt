package com.creativem.fulltv.peliculas

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HeaderPresenter : Presenter() {

    private var usuariosListener: ValueEventListener? = null

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_encabezado_peliculas, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val context = viewHolder.view.context

        val textFecha = viewHolder.view.findViewById<TextView>(R.id.textfecha)
        val textHora = viewHolder.view.findViewById<TextView>(R.id.textHora)
        val textUsuario = viewHolder.view.findViewById<TextView>(R.id.textUsuario)
        val userOnline = viewHolder.view.findViewById<TextView>(R.id.useronline)
        val userOff = viewHolder.view.findViewById<TextView>(R.id.useroff)
        val textCastv = viewHolder.view.findViewById<TextView>(R.id.textCastv)
        val txtActualizacion = viewHolder.view.findViewById<TextView>(R.id.txtActualizacion)
        val imagenUser = viewHolder.view.findViewById<ImageView>(R.id.imagenuser)

        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val realtimeDb = FirebaseDatabase.getInstance().reference

        val usuario = auth.currentUser
        val usuarioId = usuario?.uid

        textFecha.text = obtenerFechaActual()
        textHora.text = obtenerHoraActual()

        if (usuarioId == null) {
            textUsuario.text = "Invitado"
            textCastv.text = "🎬 Películas: 0 | ⭐ Castv: 0"
            userOnline.text = "ON-0"
            userOff.text = "OFF-0"
            imagenUser.setImageResource(R.drawable.icono)
            return
        }

        // Obtener el correo del usuario autenticado
        val usuarioEmail = FirebaseAuth.getInstance().currentUser?.email

        if (!usuarioEmail.isNullOrBlank()) {
            // Obtener datos del usuario desde el correo
            CastvHelper.obtenerDatosUsuario(
                email = usuarioEmail,
                onSuccess = { nombre, correo, castv, enlinea ->
                    textUsuario.text = "\uD83E\uDDD1 $nombre" + if (enlinea) " 🟢" else " 🔴"

                    // Obtener cantidad de películas desde Firestore
                    firestore.collection("movies")
                        .get()
                        .addOnSuccessListener { result ->
                            val cantidadPeliculas = result.size()
                            textCastv.text = "🎬 Películas: $cantidadPeliculas | ⭐ Castv: $castv"
                        }
                        .addOnFailureListener {
                            textCastv.text = "🎬 Películas: 0 | ⭐ Castv: $castv"
                        }
                },
                onFailure = {
                    textUsuario.text = "Usuario desconocido"
                    textCastv.text = "🎬 Películas: 0 | ⭐ Castv: 0"
                }
            )
        } else {
            // No se pudo obtener el correo
            textUsuario.text = "Usuario no autenticado"
            textCastv.text = "🎬 Películas: 0 | ⭐ Castv: 0"
        }


        // Foto del usuario
        val photoUrl = usuario.photoUrl
        if (photoUrl != null) {
            Glide.with(context)
                .load(photoUrl)
                .placeholder(R.drawable.icono)
                .error(R.drawable.icono)
                .centerCrop()
                .into(imagenUser)
        } else {
            imagenUser.setImageResource(R.drawable.icono)
        }

        // Listener de usuarios conectados (en tiempo real)
        usuariosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var on = 0
                var off = 0
                for (userSnapshot in snapshot.children) {
                    val conectado = userSnapshot.child("enlinea").getValue(Boolean::class.java) ?: false
                    if (conectado) on++ else off++
                }
                userOnline.text = "ON-$on"
                userOff.text = "OFF-$off"
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        realtimeDb.child("usuarios").addValueEventListener(usuariosListener!!)

        // Pedidos recientes
        firestore.collection("pedidosmovies")
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val listaPedidos = StringBuilder()
                    for (pedido in result) {
                        val nombre = pedido.getString("nombre") ?: "Desconocido"
                        val title = pedido.getString("title") ?: "Película"
                        listaPedidos.append("🎬 $nombre pidió: $title\n")
                    }

                    txtActualizacion.apply {
                        text = listaPedidos.toString().trim()
                        visibility = View.VISIBLE
                        isSelected = true

                        ObjectAnimator.ofArgb(
                            this,
                            "textColor",
                            Color.RED,
                            Color.parseColor("#FF9800"),
                            Color.YELLOW,
                            Color.GREEN,
                            Color.BLUE,
                            Color.parseColor("#4B0082"),
                            Color.parseColor("#EE82EE"),
                            Color.RED
                        ).apply {
                            duration = 4000L
                            repeatCount = ValueAnimator.INFINITE
                            repeatMode = ValueAnimator.RESTART
                            start()
                        }
                    }
                } else {
                    txtActualizacion.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                txtActualizacion.text = "Error al cargar los pedidos."
                txtActualizacion.visibility = View.VISIBLE
            }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
        usuariosListener?.let {
            FirebaseDatabase.getInstance().getReference("usuarios").removeEventListener(it)
        }
        usuariosListener = null
    }

    private fun obtenerFechaActual(): String {
        val fecha = SimpleDateFormat("EEEE dd MM yy", Locale.getDefault()).format(Date())
        return fecha.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun obtenerHoraActual(): String {
        val hora = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date())
        return hora.replace("am", "AM").replace("pm", "PM")
    }
}