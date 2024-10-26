package com.creativem.fulltv.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.creativem.fulltv.R

class MenuPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.menu_item_layout, parent, false)

        // Ajuste de tamaño dinámico
        val width = 200 // Ancho personalizado
        val height = 220 // Alto personalizado
        view.layoutParams = ViewGroup.LayoutParams(width, height)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val menuItem = item as MenuItem
        val iconView = viewHolder.view.findViewById<ImageView>(R.id.iconView)
        val nameView = viewHolder.view.findViewById<TextView>(R.id.nameView)

        // Configuración de datos del ítem del menú
        iconView.setImageDrawable(
            ContextCompat.getDrawable(viewHolder.view.context, menuItem.iconResId)
        )
        nameView.text = menuItem.name
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Limpieza de datos del ViewHolder
    }
}