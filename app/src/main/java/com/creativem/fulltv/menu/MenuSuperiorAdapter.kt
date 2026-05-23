package com.creativem.fulltv.menu

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R

class MenuSuperiorAdapter(
    private val items: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<MenuSuperiorAdapter.MenuViewHolder>() {

    var lastFocusedPosition: Int = 0

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Usamos los mismos ID del layout del menú principal
        val icon: ImageView = view.findViewById(R.id.iconoMenu)
        val text: TextView = view.findViewById(R.id.textoMenu)

        init {
            // OCULTAMOS EL ICONO por código para que solo quede el texto
            icon.visibility = View.GONE

            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnClickListener {
                onItemClick(items[bindingAdapterPosition])
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // 1. Efecto de fondo (Borde Dorado)
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null

                // 2. Efectos visuales (Color y Escala)
                if (hasFocus) {
                    text.setTextColor(Color.YELLOW)
                    v.scaleX = 1.1f
                    v.scaleY = 1.1f
                    lastFocusedPosition = bindingAdapterPosition
                } else {
                    text.setTextColor(Color.WHITE)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }

                text.isSelected = hasFocus
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        // Usamos el MISMO layout para que el tamaño y fuente sean iguales
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_principal, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.text.text = items[position]

        // Aplicamos el estado visual inicial para que no se pierda el rastro
        val isFocused = position == lastFocusedPosition
        if (isFocused) {
            holder.text.setTextColor(Color.YELLOW)
            holder.itemView.scaleX = 1.1f
            holder.itemView.scaleY = 1.1f
        } else {
            holder.text.setTextColor(Color.WHITE)
            holder.itemView.scaleX = 1.0f
            holder.itemView.scaleY = 1.0f
        }
    }

    override fun getItemCount(): Int = items.size
}