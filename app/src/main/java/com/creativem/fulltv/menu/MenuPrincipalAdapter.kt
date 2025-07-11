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

    var lastFocusedPosition: Int = 0  // <- público para usar desde el fragmento

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconoMenu)
        val text: TextView = view.findViewById(R.id.textoMenu)

        init {
            view.setOnClickListener {
                val item = items[adapterPosition]
                onItemClick(item)
            }

            // Focus único
            view.setOnFocusChangeListener { v, hasFocus ->
                // Fondo + escala
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null

                val scale = if (hasFocus) 1.1f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()

                // Mostrar el texto solo con focus
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

        // Siempre oculto por defecto al bindear
        holder.text.visibility = View.INVISIBLE
        holder.text.isSelected = false
    }

    override fun getItemCount(): Int = items.size
}