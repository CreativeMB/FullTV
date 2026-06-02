package com.creativem.fulltv

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.creativem.fulltv.principal.Modelo

class BannerPromosAdapter(
    private val list: List<Modelo>,
    private val onMovieFocused: (Modelo) -> Unit,
    private val onFocusLost: () -> Unit,
    private val onMovieClicked: (Modelo) -> Unit
) : RecyclerView.Adapter<BannerPromosAdapter.ViewHolder>() {

    private var posSeleccionadaActiva = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.imgMovie)
        val tvTitulo: TextView = view.findViewById(R.id.txtMovieTitle)
        val tvStatus: TextView = view.findViewById(R.id.txtStatus)
        val tvBadge: TextView = view.findViewById(R.id.txtBadge)
        val infoArea: View = view.findViewById(R.id.infoArea)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pelicula_alquilada, parent, false)

        val context = parent.context
        val density = context.resources.displayMetrics.density
        val widthInPx = (75 * density).toInt()
        val heightInPx = (110 * density).toInt()

        val params = view.layoutParams ?: ViewGroup.LayoutParams(widthInPx, heightInPx)
        params.width = widthInPx
        params.height = heightInPx
        view.layoutParams = params

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movie = list[position]

        holder.tvStatus.visibility = View.GONE
        holder.tvBadge.visibility = View.GONE
        holder.infoArea.visibility = View.GONE

        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(holder.ivPoster)

        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true

        if (position == posSeleccionadaActiva) {
            holder.itemView.animate().scaleX(1.15f).scaleY(1.15f).translationZ(15f).setDuration(250).start()
            val card = holder.itemView as? androidx.cardview.widget.CardView
            card?.setCardBackgroundColor(Color.parseColor("#C5A059"))
        } else {
            holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(200).start()
            val card = holder.itemView as? androidx.cardview.widget.CardView
            card?.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                onMovieFocused(movie)
                actualizarSeleccionActiva(position)
            } else {
                onFocusLost()
            }
        }

        holder.itemView.setOnClickListener { onMovieClicked(movie) }
    }

    override fun getItemCount(): Int = list.size

    fun actualizarSeleccionActiva(nuevaPosicion: Int) {
        val posAnterior = posSeleccionadaActiva
        posSeleccionadaActiva = nuevaPosicion
        notifyItemChanged(posAnterior)
        notifyItemChanged(posSeleccionadaActiva)
    }
}