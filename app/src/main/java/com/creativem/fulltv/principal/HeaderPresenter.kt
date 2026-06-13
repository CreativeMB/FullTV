//package com.creativem.fulltv.principal
//
//import android.animation.ObjectAnimator
//import android.animation.ValueAnimator
//import android.graphics.Color
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.leanback.widget.Presenter
//import com.bumptech.glide.Glide
//import com.creativem.fulltv.R
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.database.DataSnapshot
//import com.google.firebase.database.DatabaseError
//import com.google.firebase.database.FirebaseDatabase
//import com.google.firebase.database.ValueEventListener
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//
//class HeaderPresenter : Presenter() {
//    private var noticiaListener: ValueEventListener? = null
//    private var usuariosListener: ValueEventListener? = null
//    // Referencia global a Realtime Database
//    private val databaseRef = FirebaseDatabase.getInstance().reference
//
//    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_encabezado_peliculas, parent, false)
//        return ViewHolder(view)
//    }
//
//    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
//        val context = viewHolder.view.context
//
//        val textFecha = viewHolder.view.findViewById<TextView>(R.id.textfecha)
//        val textHora = viewHolder.view.findViewById<TextView>(R.id.textHora)
//        val textUsuario = viewHolder.view.findViewById<TextView>(R.id.textUsuario)
//        val userOnline = viewHolder.view.findViewById<TextView>(R.id.useronline)
//        val userOff = viewHolder.view.findViewById<TextView>(R.id.useroff)
//        val textCastv = viewHolder.view.findViewById<TextView>(R.id.textCastv)
//        val imagenUser = viewHolder.view.findViewById<ImageView>(R.id.imagenuser)
//        val txtBanner = viewHolder.view.findViewById<TextView>(R.id.txtBanner)
//
//        val auth = FirebaseAuth.getInstance()
//        val usuario = auth.currentUser
//        val usuarioId = usuario?.uid
//
//        textFecha.text = obtenerFechaActual()
//        textHora.text = obtenerHoraActual()
//
//
//        // 1. --- LÓGICA DE LA NOTICIA (BANNER) ---
//        noticiaListener = object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                if (snapshot.exists()) {
//                    // USAMOS "banner" PORQUE ASÍ ESTÁ EN TU FIREBASE
//                    val mensaje = snapshot.child("banner").getValue(String::class.java) ?: ""
//                    val version = snapshot.child("versionapk").getValue(String::class.java) ?: ""
//
//                    txtBanner?.apply {
//                        // Aquí ya saldrá el texto largo que tienes en la DB
//                        text = " 📢 $mensaje | Versión: $version "
//                        visibility = View.VISIBLE
//                        isSelected = true // Para que empiece a rodar
//                    }
//                }
//            }
//            override fun onCancelled(error: DatabaseError) {
//                Log.e("FIREBASE", "Error al cargar noticia: ${error.message}")
//            }
//        }
//        databaseRef.child("noticia").child("us4vaaf0VPezu9vuc4ns").addValueEventListener(noticiaListener!!)
//        if (usuarioId == null) {
//            textUsuario.text = "Invitado"
//            textCastv.text = "🎬 Películas: 0 | ⭐ Castv: 0"
//            userOnline.text = "ON-0"
//            userOff.text = "OFF-0"
//            imagenUser.setImageResource(R.drawable.icono)
//            return
//        }
//
//        // --- LÓGICA DE USUARIO Y CONTEO DE PELÍCULAS ---
//        val usuarioEmail = auth.currentUser?.email
//        if (!usuarioEmail.isNullOrBlank()) {
//            CastvHelper.obtenerDatosUsuario(
//                email = usuarioEmail,
//                onSuccess = { nombre, correo, castv, enlinea ->
//                    textUsuario.text = "\uD83E\uDDD1 $nombre" + if (enlinea) " 🟢" else " 🔴"
//
//                    // NUEVA RUTA: Obtener cantidad de películas desde Realtime Database
//                    databaseRef.child("movies").get().addOnSuccessListener { snapshot ->
//                        val cantidadPeliculas = snapshot.childrenCount // childrenCount es muy eficiente
//                        textCastv.text = "🎬 Películas: $cantidadPeliculas | ⭐ Castv: $castv"
//                    }.addOnFailureListener {
//                        textCastv.text = "🎬 Películas: 0 | ⭐ Castv: $castv"
//                    }
//                },
//                onFailure = {
//                    textUsuario.text = "Usuario desconocido"
//                    textCastv.text = "🎬 Películas: 0 | ⭐ Castv: 0"
//                }
//            )
//        }
//
//        // Foto del usuario (se mantiene igual)
//        val photoUrl = usuario.photoUrl
//        if (photoUrl != null) {
//            Glide.with(context).load(photoUrl).placeholder(R.drawable.icono).error(R.drawable.icono).centerCrop().into(imagenUser)
//        } else {
//            imagenUser.setImageResource(R.drawable.icono)
//        }
//
//        // --- LISTENER DE USUARIOS CONECTADOS (Realtime Database) ---
//        usuariosListener = object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                var on = 0
//                var off = 0
//                for (userSnapshot in snapshot.children) {
//                    val conectado = userSnapshot.child("enlinea").getValue(Boolean::class.java) ?: false
//                    if (conectado) on++ else off++
//                }
//                userOnline.text = "ON-$on"
//                userOff.text = "OFF-$off"
//            }
//            override fun onCancelled(error: DatabaseError) {}
//        }
//        databaseRef.child("usuarios").addValueEventListener(usuariosListener!!)
//
//    }
//
//    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
//        // Importante: Eliminar listener para no gastar recursos
//        usuariosListener?.let {
//            databaseRef.child("usuarios").removeEventListener(it)
//        }
//        usuariosListener = null
//    }
//
//    private fun obtenerFechaActual(): String {
//        val fecha = SimpleDateFormat("EEEE dd MM yy", Locale.getDefault()).format(Date())
//        return fecha.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
//    }
//
//    private fun obtenerHoraActual(): String {
//        val hora = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date())
//        return hora.replace("am", "AM").replace("pm", "PM")
//    }
//}