package com.creativem.tvfullurl.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.modelo.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CastvAdapter(
    private val userList: MutableList<User>,
    private val onEditClick: (userId: String, newPoints: Int) -> Unit,
    private val onDeleteClick: (userId: String) -> Unit
) : RecyclerView.Adapter<CastvAdapter.UserViewHolder>() {

    private var filteredList: List<User> = userList

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.userName)
        val userEmail: TextView = itemView.findViewById(R.id.userEmail)
        val userFecha: TextView = itemView.findViewById(R.id.fechaCreacion)
        val userCastv: EditText = itemView.findViewById(R.id.userCastv)
        val editImage: ImageView = itemView.findViewById(R.id.editImage)
        val deleteImage: ImageView = itemView.findViewById(R.id.deleteImage)
    }
    init {
        filteredList = userList.sortedWith(compareByDescending<User> { it.isOnline }
            .thenByDescending { obtenerTimestamp(it.ultimaConexion) })
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_castv, parent, false)
        return UserViewHolder(view)
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            userList
        } else {
            userList.filter { user ->
                user.nombre.contains(query, ignoreCase = true)
            }
        }.sortedWith(compareByDescending<User> { it.isOnline }
            .thenByDescending { obtenerTimestamp(it.ultimaConexion) })

        notifyDataSetChanged()
    }



    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = filteredList[position]

        // Mostrar nombre y color según si está en línea
        holder.userName.text = user.nombre
        if (user.isOnline) {
            holder.userName.setTextColor(holder.itemView.context.getColor(R.color.online_color))
        } else {
            holder.userName.setTextColor(holder.itemView.context.getColor(R.color.offline_color))
        }

        holder.userEmail.text = user.correo

        holder.userFecha.text = parsearFecha(user.ultimaConexion)

        holder.userCastv.setText(user.castv.toString())


        // Copiar email al portapapeles
        holder.userEmail.setOnClickListener {
            val email = user.correo
            if (email.isNotEmpty()) {
                val clipboard = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Email", email)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(holder.itemView.context, "Correo copiado al portapapeles", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(holder.itemView.context, "El correo electrónico no está disponible", Toast.LENGTH_SHORT).show()
            }
        }

        // Editar puntos
        holder.editImage.setOnClickListener {
            val newPoints = holder.userCastv.text.toString().toIntOrNull() ?: 0
            onEditClick(user.userId, newPoints)
        }

        // Eliminar usuario
        holder.deleteImage.setOnClickListener {
            val context = holder.itemView.context
            android.app.AlertDialog.Builder(context)
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que deseas eliminar a ${user.nombre}?")
                .setPositiveButton("Eliminar") { _, _ ->
                    onDeleteClick(user.userId)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

    }

    override fun getItemCount(): Int = filteredList.size

    private fun parsearFecha(fecha: Any?): String {
        return when (fecha) {
            is Long -> {
                val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                sdf.format(Date(fecha))
            }
            is Double -> {
                val millis = fecha.toLong()
                val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                sdf.format(Date(millis))
            }
            is String -> fecha
            else -> "Sin fecha"
        }
    }
    private fun obtenerTimestamp(fecha: Any?): Long {
        return when (fecha) {
            is Long -> fecha
            is Double -> fecha.toLong()
            is String -> {
                // Si la fecha es una cadena, intenta parsearla
                try {
                    val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                    sdf.parse(fecha)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
            else -> 0L
        }
    }




}