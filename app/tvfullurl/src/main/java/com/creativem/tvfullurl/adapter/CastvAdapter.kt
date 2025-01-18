package com.creativem.tvfullurl.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.util.Log
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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.CountDownLatch

class CastvAdapter(
    private val userList: MutableList<User>,
    private val onEditClick: (userId: String, newPoints: Int) -> Unit,
    private val onDeleteClick: (userId: String) -> Unit
) : RecyclerView.Adapter<CastvAdapter.UserViewHolder>() {

    private var filteredList: List<User> = userList

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.userName)
        val userEmail: TextView = itemView.findViewById(R.id.userEmail)
        val userPoints: EditText = itemView.findViewById(R.id.userPoints)
        val editImage: ImageView = itemView.findViewById(R.id.editImage)
        val deleteImage: ImageView = itemView.findViewById(R.id.deleteImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_castv, parent, false)
        return UserViewHolder(view)
    }

    fun filter(query: String) {
        // Filtrar la lista de usuarios por el nombre
        filteredList = if (query.isEmpty()) {
            userList
        } else {
            userList.filter { user ->
                user.nombre.contains(query, ignoreCase = true) // Filtrado por nombre
            }
        }

        // Actualizamos el RecyclerView con la lista filtrada
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = filteredList[position]

        // Obtener el estado de conexión de cada usuario desde Realtime Database
        val userId = user.id
        FirebaseDatabase.getInstance().getReference("usuarios_conectados")
            .child(userId).get().addOnSuccessListener { snapshot ->
                val isOnline = snapshot.getValue(Boolean::class.java) ?: false

                // Cambiar el color del nombre según el estado de conexión
                if (isOnline) {
                    holder.userName.setTextColor(holder.itemView.context.getColor(R.color.online_color))
                } else {
                    holder.userName.setTextColor(holder.itemView.context.getColor(R.color.offline_color))
                }
            }.addOnFailureListener {
                Log.e("PedidosAdapter", "Error al obtener el estado de conexión")
            }

        // Obtener el nombre del usuario desde Firestore
        FirebaseFirestore.getInstance().collection("users")
            .document(userId).get().addOnSuccessListener { documentSnapshot ->
                val userName = documentSnapshot.getString("nombre") ?: "Nombre no disponible"
                holder.userName.text = userName
            }.addOnFailureListener {
                Log.e("PedidosAdapter", "Error al obtener el nombre del usuario")
            }

        // Mostrar el email y puntos
        holder.userEmail.text = user.email
        holder.userPoints.setText(user.puntos.toString())

        // Maneja el clic en el email
        holder.userEmail.setOnClickListener {
            val email = user.email
            if (email.isNotEmpty()) {
                val clipboard = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Email", email)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(holder.itemView.context, "Correo copiado al portapapeles", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("PedidosAdapter", "Email vacío")
                Toast.makeText(holder.itemView.context, "El correo electrónico no está disponible", Toast.LENGTH_SHORT).show()
            }
        }

        // Acción para editar puntos (al hacer clic en la imagen de editar)
        holder.editImage.setOnClickListener {
            val newPoints = holder.userPoints.text.toString().toIntOrNull() ?: 0
            onEditClick(user.id, newPoints)
        }

        // Acción para eliminar usuario (al hacer clic en la imagen de eliminar)
        holder.deleteImage.setOnClickListener {
            onDeleteClick(user.id)
        }
    }

    override fun getItemCount(): Int = filteredList.size // Cambia userList por filteredList
}