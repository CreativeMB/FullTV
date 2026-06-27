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

    var lastFocusedPosition: Int = 0

    // Color dorado CineParche y blanco definidos como constantes
    private val colorDorado = Color.parseColor("#C5A059")
    private val colorBlanco = Color.WHITE

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconoMenu)
        val text: TextView = view.findViewById(R.id.textoMenu)

        init {
            text.visibility = View.VISIBLE

            view.setOnClickListener {
                val item = items[bindingAdapterPosition]
                onItemClick(item)
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // Fondo de selección
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null

                text.isSelected = hasFocus
                if (hasFocus) {
                    // 🟢 CORRECCIÓN: Color dorado al tener el foco
                    text.setTextColor(colorDorado)
                    v.scaleX = 1.1f
                    v.scaleY = 1.1f
                    lastFocusedPosition = bindingAdapterPosition
                } else {
                    // 🟢 CORRECCIÓN: Color blanco por defecto
                    text.setTextColor(colorBlanco)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
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
        holder.icon.setImageResource(item.iconResId)
        holder.text.text = item.name

        holder.text.visibility = View.VISIBLE

        val isFocused = position == lastFocusedPosition
        holder.text.isSelected = isFocused

        if (isFocused) {
            // 🟢 CORRECCIÓN: Color dorado si fue el último elemento seleccionado
            holder.text.setTextColor(colorDorado)
            holder.itemView.scaleX = 1.1f
            holder.itemView.scaleY = 1.1f
        } else {
            // 🟢 CORRECCIÓN: Asegura que el resto de los elementos mantengan el color blanco
            holder.text.setTextColor(colorBlanco)
            holder.itemView.scaleX = 1.0f
            holder.itemView.scaleY = 1.0f
        }
    }

    override fun getItemCount(): Int = items.size
}