package com.creativem.fulltv.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R
import android.graphics.Color
import android.util.Log
import android.view.KeyEvent

class MenuSuperiorAdapter(
    private val items: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<MenuSuperiorAdapter.MenuViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_peliculasapi, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return MenuViewHolder(view)
    }

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.text_menu)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position])
                }
            }

            view.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        Log.d("MenuSuperiorAdapter", "Presionado item: ${items[position]}")
                        onItemClick(items[position])
                        return@setOnKeyListener true
                    }
                }
                false
            }


            view.setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else null

                val scale = if (hasFocus) 1.05f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
            }
        }
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.textView.text = items[position]
        holder.textView.setTextColor(Color.WHITE)
    }

    override fun getItemCount(): Int = items.size
}