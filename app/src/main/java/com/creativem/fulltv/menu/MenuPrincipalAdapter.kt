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

    var lastFocusedPosition: Int = 0

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconoMenu)
        val text: TextView = view.findViewById(R.id.textoMenu)

        init {
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position])
                }
            }
            view.setOnFocusChangeListener { v, hasFocus ->
                // Cambiar fondo si tiene foco
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.focusmenu)
                else
                    null

                // Escala que se aplicará
                val scale = if (hasFocus) 1.5f else 1f

                // 🔍 Animar el ícono
                icon.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(200)
                    .start()

                // 🔤 Animar el texto
                text.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(200)
                    .start()

                // Mostrar u ocultar el texto según foco (opcional)
                text.visibility = if (hasFocus) View.VISIBLE else View.GONE

                // Guardar posición enfocada
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


    }

    override fun getItemCount(): Int = items.size
}