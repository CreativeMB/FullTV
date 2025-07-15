package com.creativem.fulltv.api

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R

class PelisCarteleraAdapter(
    private val items: List<TmdbMovie>,
    private val onClick: (TmdbMovie) -> Unit
) : RecyclerView.Adapter<PelisCarteleraAdapter.ViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.itemPoster)
        val title: TextView = view.findViewById(R.id.itemTitle)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && selectedPosition != position) {
                    notifyItemChanged(selectedPosition)
                    selectedPosition = position
                    notifyItemChanged(selectedPosition)
                    onClick(items[position])
                }
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // Foco visual
                v.setBackgroundColor(
                    if (hasFocus)
                        ContextCompat.getColor(v.context, R.color.colorhover2)
                    else
                        ContextCompat.getColor(v.context, R.color.colorNotSelected)
                )

                // Escala
                v.scaleX = if (hasFocus) 1.05f else 1f
                v.scaleY = if (hasFocus) 1.05f else 1f

                // Activar marquee
                title.isSelected = hasFocus
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pelis_cartelera, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movie = items[position]
        holder.title.apply {
            text = movie.title
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            setHorizontallyScrolling(true)
        }

        Glide.with(holder.itemView.context)
            .load("https://image.tmdb.org/t/p/w500${movie.poster_path}")
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.icono)
            .into(holder.poster)

        // Fondo según si está seleccionado
        holder.itemView.setBackgroundColor(
            if (position == selectedPosition)
                ContextCompat.getColor(holder.itemView.context, R.color.colorSelected)
            else
                ContextCompat.getColor(holder.itemView.context, R.color.colorNotSelected)
        )
    }

    override fun getItemCount(): Int = items.size
}