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

class MenuSuperiorApiAdapter(
    private val items: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<MenuSuperiorApiAdapter.MenuViewHolder>() {

    var lastFocusedPosition: Int = -1

    // 🟢 COINCIDENCIA DE COLORES: Definimos el color dorado y blanco de su marca
    private val colorDorado = Color.parseColor("#C5A059")
    private val colorBlanco = Color.WHITE

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconoMenu)
        val text: TextView = view.findViewById(R.id.textoMenu)

        init {
            // Ocultamos el icono por código para que solo quede el texto
            icon.visibility = View.GONE

            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnClickListener {
                onItemClick(items[bindingAdapterPosition])
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // Efecto de fondo (Borde Dorado)
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null

                // Efectos visuales de color y escala unificados
                if (hasFocus) {
                    // 🟢 CORRECCIÓN: Se aplica su color dorado original en lugar de amarillo
                    text.setTextColor(colorDorado)
                    v.scaleX = 1.1f
                    v.scaleY = 1.1f
                    lastFocusedPosition = bindingAdapterPosition
                } else {
                    text.setTextColor(colorBlanco)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }

                text.isSelected = hasFocus
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_principal, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.text.text = items[position]

        // Aplicamos el estado visual inicial para que no se pierda el rastro de la selección
        val isFocused = position == lastFocusedPosition
        if (isFocused) {
            // 🟢 CORRECCIÓN: Se aplica su color dorado original en lugar de amarillo
            holder.text.setTextColor(colorDorado)
            holder.itemView.scaleX = 1.1f
            holder.itemView.scaleY = 1.1f
        } else {
            holder.text.setTextColor(colorBlanco)
            holder.itemView.scaleX = 1.0f
            holder.itemView.scaleY = 1.0f
        }
    }

    override fun getItemCount(): Int = items.size
}