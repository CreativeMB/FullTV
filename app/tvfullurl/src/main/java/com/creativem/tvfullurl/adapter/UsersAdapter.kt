package com.creativem.tvfullurl.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.modelo.User
class UsersAdapter(
    private val userList: MutableList<User>,
    private val onEditClick: (userId: String, newPoints: Int) -> Unit,
    private val onDeleteClick: (userId: String) -> Unit
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    private var filteredList: List<User> = userList

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.userName)
        val userEmail: TextView = itemView.findViewById(R.id.userEmail)
        val userPoints: EditText = itemView.findViewById(R.id.userPoints)
        val editImage: ImageView = itemView.findViewById(R.id.editImage)
        val deleteImage: ImageView = itemView.findViewById(R.id.deleteImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            userList
        } else {
            userList.filter { user ->
                user.nombre.contains(query, ignoreCase = true) // Asegúrate de que tienes una propiedad nombre en tu clase User
            }
        }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = filteredList[position] // Cambia userList por filteredList

        holder.userName.text = user.nombre
        holder.userEmail.text = user.email
        holder.userPoints.setText(user.puntos.toString())

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