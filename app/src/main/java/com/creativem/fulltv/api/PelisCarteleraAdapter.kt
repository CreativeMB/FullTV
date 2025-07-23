package com.creativem.fulltv.api

import android.graphics.Color
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.CastvHelper

class PelisCarteleraAdapter(
    private val items: List<TmdbMovie>,
    private val onClick: (TmdbMovie) -> Unit
) : RecyclerView.Adapter<PelisCarteleraAdapter.ViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.itemPoster)
        val title: TextView = view.findViewById(R.id.itemTitle)
        val etiquetaValida: TextView = view.findViewById(R.id.etiqueta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pelis_cartelera, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movie = items[position]

        // Configuración de la etiqueta
        holder.etiquetaValida.text = "Alquilar $50💳"
        holder.etiquetaValida.setBackgroundColor(Color.parseColor("#880E4F"))
        holder.etiquetaValida.visibility = View.VISIBLE

        // Configuración del título
        holder.title.apply {
            text = movie.title
            textSize = 16f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSingleLine = true
            isFocusable = false
            isFocusableInTouchMode = false
            setHorizontallyScrolling(true)
            isSelected = holder.itemView.isFocused // para marquee
        }
        val imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
        CastvHelper.loadImage(holder.itemView.context, holder.poster, imageUrl)

        // Manejo de enfoque
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            holder.title.isSelected = hasFocus
            holder.title.setTextColor(if (hasFocus) Color.YELLOW else Color.WHITE)

            view.background = if (hasFocus)
                ContextCompat.getDrawable(view.context, R.drawable.card_focused_background)
            else
                null

            val scale = if (hasFocus) 1.1f else 1f
            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Manejo de clic
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && selectedPosition != pos) {
                notifyItemChanged(selectedPosition)
                selectedPosition = pos
                notifyItemChanged(selectedPosition)
                onClick(items[pos])
            }
        }
    }

    override fun getItemCount(): Int = items.size
}