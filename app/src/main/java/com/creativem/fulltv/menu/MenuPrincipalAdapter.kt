package com.creativem.fulltv.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R
import android.graphics.Color

class MenuPrincipalAdapter(
    private val items: List<MenuPrincipalItem>,
    private val onItemClick: (MenuPrincipalItem) -> Unit
) : RecyclerView.Adapter<MenuPrincipalAdapter.MenuViewHolder>() {

    var lastFocusedPosition: Int = -1

    // 🟢 COINCIDENCIA DE COLORES: Definimos el color dorado y blanco de su marca
    private val colorDorado = Color.parseColor("#C5A059")
    private val colorBlanco = Color.WHITE

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconoMenu)
        val text: TextView = view.findViewById(R.id.textoMenu)

        init {
            // El icono ahora es visible por defecto
            icon.visibility = View.VISIBLE

            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnClickListener {
                val item = items[bindingAdapterPosition]
                onItemClick(item)
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // Efecto de fondo (Borde Dorado / Selección)
                v.background = if (hasFocus) {
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                } else {
                    null
                }

                if (hasFocus) {
                    text.setTextColor(colorDorado)
                    // 🟢 CORRECCIÓN: Se eliminó icon.setColorFilter para respetar los colores originales
                    v.scaleX = 1.1f
                    v.scaleY = 1.1f
                    lastFocusedPosition = bindingAdapterPosition
                } else {
                    text.setTextColor(colorBlanco)
                    // 🟢 CORRECCIÓN: Se eliminó icon.setColorFilter para respetar los colores originales
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
        val item = items[position]
        holder.text.text = item.name

        // Hacer visible el icono y asignarle la imagen correspondiente de la lista
        holder.icon.visibility = View.VISIBLE
        holder.icon.setImageResource(item.iconResId)

        // Aplicamos el estado visual inicial para que no se pierda el rastro de la selección
        val isFocused = position == lastFocusedPosition
        holder.text.isSelected = isFocused

        if (isFocused) {
            holder.itemView.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.card_focused_background)
            holder.text.setTextColor(colorDorado)
            // 🟢 CORRECCIÓN: Se eliminó holder.icon.setColorFilter
            holder.itemView.scaleX = 1.1f
            holder.itemView.scaleY = 1.1f
        } else {
            holder.itemView.background = null
            holder.text.setTextColor(colorBlanco)
            // 🟢 CORRECCIÓN: Se eliminó holder.icon.setColorFilter
            holder.itemView.scaleX = 1.0f
            holder.itemView.scaleY = 1.0f
        }
    }

    override fun getItemCount(): Int = items.size
}