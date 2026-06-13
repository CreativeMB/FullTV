package com.creativem.fulltv.principal

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class HeaderManager(private val rootView: View) { // Recibe la vista directamente

    private val databaseRef = FirebaseDatabase.getInstance().reference
    private var noticiaListener: ValueEventListener? = null
    private var usuariosListener: ValueEventListener? = null

    // Referencias a los componentes de la vista
    private val textFecha = rootView.findViewById<TextView>(R.id.textfecha)
    private val textHora = rootView.findViewById<TextView>(R.id.textHora)
    private val textUsuario = rootView.findViewById<TextView>(R.id.textUsuario)
    private val userOnline = rootView.findViewById<TextView>(R.id.useronline)
    private val userOff = rootView.findViewById<TextView>(R.id.useroff)
    private val textCastv = rootView.findViewById<TextView>(R.id.textCastv)
    private val imagenUser = rootView.findViewById<ImageView>(R.id.imagenuser)
    private val txtBanner = rootView.findViewById<TextView>(R.id.txtBanner)

    init {
        cargarDatos()
    }

    private fun cargarDatos() {
        val auth = FirebaseAuth.getInstance()
        val usuario = auth.currentUser

        textFecha.text = obtenerFechaActual()
        textHora.text = obtenerHoraActual()

        // 1. Noticia (Banner)
        noticiaListener = databaseRef.child("noticia").child("us4vaaf0VPezu9vuc4ns")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val mensaje = snapshot.child("banner").getValue(String::class.java) ?: ""
                    val version = snapshot.child("versionapk").getValue(String::class.java) ?: ""
                    txtBanner?.text = " 📢 $mensaje | Versión: $version "
                    txtBanner?.isSelected = true
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // 2. Usuario
        if (usuario == null) {
            textUsuario.text = "Invitado"
            imagenUser.setImageResource(R.drawable.icono)
        } else {
            // Cargar datos de usuario
            val usuarioEmail = usuario.email ?: ""
            CastvHelper.obtenerDatosUsuario(usuarioEmail, { nombre, _, castv, enlinea ->
                textUsuario.text = "\uD83E\uDDD1 $nombre" + if (enlinea) " 🟢" else " 🔴"
                databaseRef.child("movies").get().addOnSuccessListener { snapshot ->
                    textCastv.text = "🎬 Películas: ${snapshot.childrenCount} | ⭐ Castv: $castv"
                }
            }, {})

            // Foto
            usuario.photoUrl?.let {
                Glide.with(rootView.context).load(it).placeholder(R.drawable.icono).into(imagenUser)
            }
        }

        // 3. Usuarios online
        usuariosListener = databaseRef.child("usuarios").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var on = 0; var off = 0
                snapshot.children.forEach {
                    if (it.child("enlinea").getValue(Boolean::class.java) == true) on++ else off++
                }
                userOnline.text = "ON-$on"
                userOff.text = "OFF-$off"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Método para limpiar memoria desde la Activity/Fragment
    fun destroy() {
        noticiaListener?.let { databaseRef.removeEventListener(it) }
        usuariosListener?.let { databaseRef.removeEventListener(it) }
    }

    private fun obtenerFechaActual() = SimpleDateFormat("EEEE dd MM yy", Locale.getDefault()).format(Date())
    private fun obtenerHoraActual() = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date())
}