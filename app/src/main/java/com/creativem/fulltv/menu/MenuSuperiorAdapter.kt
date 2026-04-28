package com.creativem.fulltv.menu

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R

class MenuSuperiorAdapter(
    private val items: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<MenuSuperiorAdapter.MenuViewHolder>() {

    private var selectedPosition = 0

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.text_menu)

        init {
            // Permitir que el item sea enfocable para control remoto
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val prevSelected = selectedPosition
                    selectedPosition = position

                    // Notificar cambios para actualizar colores de "Seleccionado"
                    notifyItemChanged(prevSelected)
                    notifyItemChanged(selectedPosition)

                    onItemClick(items[position])
                }
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // EFECTO DE FOCO (Cuando pasas con el control remoto)
                if (hasFocus) {
                    v.setBackgroundColor(ContextCompat.getColor(v.context, R.color.colorhover2))
                    v.scaleX = 1.1f
                    v.scaleY = 1.1f
                    textView.setTextColor(Color.YELLOW) // Color de texto al tener foco
                } else {
                    // Cuando pierde el foco, volvemos al estado normal o seleccionado
                    actualizarEstadoVisual(v, textView, bindingAdapterPosition == selectedPosition)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
        }
    }

    // Función auxiliar para no repetir código de colores
    private fun actualizarEstadoVisual(view: View, textView: TextView, isSelected: Boolean) {
        if (isSelected) {
            view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.colorSelected))
            textView.setTextColor(Color.WHITE)
            textView.paint.isFakeBoldText = true // Negrita si está seleccionado
        } else {
            view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.colorNotSelected))
            textView.setTextColor(Color.LTGRAY) // Gris claro si no está seleccionado
            textView.paint.isFakeBoldText = false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_peliculasapi, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.textView.text = items[position]

        // Aplicar estado visual inicial
        actualizarEstadoVisual(holder.itemView, holder.textView, position == selectedPosition)
    }

    override fun getItemCount(): Int = items.size
}