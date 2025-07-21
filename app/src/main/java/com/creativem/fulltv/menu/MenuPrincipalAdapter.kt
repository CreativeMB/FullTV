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

    var lastFocusedPosition: Int = 0  // Se actualiza desde el ViewHolder cuando recibe foco

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iconoMenu)
        val text: TextView = view.findViewById(R.id.textoMenu)

        init {
            // ✅ Asegurar que cada ítem sea enfocable
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position])
                }
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null

                val scale = if (hasFocus) 1.05f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()

                // ✅ Mostrar el texto completo solo cuando tenga foco
                text.visibility = if (hasFocus) View.VISIBLE else View.GONE

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

        // Opcional: puedes restaurar visualmente si tiene el foco
        val hasFocus = position == lastFocusedPosition
        holder.itemView.scaleX = if (hasFocus) 1.05f else 1f
        holder.itemView.scaleY = if (hasFocus) 1.05f else 1f
    }

    override fun getItemCount(): Int = items.size
}