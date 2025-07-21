package com.creativem.fulltv.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R
import android.graphics.Color

class MenuSuperiorAdapter(
    private val items: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<MenuSuperiorAdapter.MenuViewHolder>() {

    private var selectedPosition = 0

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.text_menu)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val selectedItem = items[position]
                    notifyItemChanged(selectedPosition)
                    selectedPosition = position
                    notifyItemChanged(selectedPosition)
                    onItemClick(selectedItem)
                }
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // Fondo con drawable elegante
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null

                // Efecto zoom animado
                val scale = if (hasFocus) 1.05f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_peliculasapi, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.textView.text = items[position]
        holder.textView.setTextColor(Color.WHITE)
        holder.itemView.setBackgroundColor(
            if (position == selectedPosition)
                ContextCompat.getColor(holder.itemView.context, R.color.colorSelected)
            else
                ContextCompat.getColor(holder.itemView.context, R.color.colorNotSelected)
        )
    }

    override fun getItemCount(): Int = items.size
}
