package com.creativem.fulltv.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R

class MenuPrincipalAdapter(
    private val items: List<MenuPrincipalItem>,
    private val onItemClick: (MenuPrincipalItem) -> Unit
) : RecyclerView.Adapter<MenuPrincipalAdapter.MenuViewHolder>() {

    var lastFocusedPosition: Int = 0  // Se puede usar desde el Fragmento para restaurar foco

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconoMenu)
        val text: TextView = view.findViewById(R.id.textoMenu)

        init {
            view.setOnClickListener {
                val item = items[bindingAdapterPosition]
                onItemClick(item)
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // Fondo y escala
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null

                val layoutParams = v.layoutParams
                if (hasFocus) {
                    layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT // Expande al tamaño del contenido (título)
                } else {
                    layoutParams.width = v.context.resources.getDimensionPixelSize(R.dimen.menu_item_width_collapsed)
                }
                v.layoutParams = layoutParams

                // Mostrar texto solo cuando tiene focus
                text.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                text.isSelected = hasFocus

                if (hasFocus) {
                    lastFocusedPosition = bindingAdapterPosition
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

        val hasFocus = position == lastFocusedPosition
        holder.text.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
        holder.text.isSelected = hasFocus

        // Restaurar fondo y escala visual si es el último con focus
        holder.itemView.background = if (hasFocus)
            ContextCompat.getDrawable(holder.itemView.context, R.drawable.card_focused_background)
        else
            null

        val scale = if (hasFocus) 1.1f else 1f
        holder.itemView.scaleX = scale
        holder.itemView.scaleY = scale
    }

    override fun getItemCount(): Int = items.size
}