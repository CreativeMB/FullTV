package com.creativem.fulltv.peliculasvalidas

import android.graphics.Color
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
import com.creativem.fulltv.principal.Movie // Asegúrate de que esta ruta sea la correcta

class PelisCarteleraAdapter(
    private var items: MutableList<Movie>, // Cambio a Movie
    private val onClick: (Movie) -> Unit    // Cambio a Movie
) : RecyclerView.Adapter<PelisCarteleraAdapter.ViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    // Esta función actualizará la lista en cualquier Activity
    fun updateMovies(newMovies: List<Movie>) {
        this.items.clear()
        this.items.addAll(newMovies)
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.itemPoster)
        val title: TextView = view.findViewById(R.id.itemTitle)
        val badge: TextView = view.findViewById(R.id.txtBadge)

        init {
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(items[position])
                }
            }

            view.setOnFocusChangeListener { v, hasFocus ->
                // Casteamos a CardView para usar sus funciones especiales
                val card = v as androidx.cardview.widget.CardView

                if (hasFocus) {
                    // --- AL ENFOCAR ---
                    card.animate().scaleX(1.10f).scaleY(1.10f).setDuration(200).start()

                    // Usar la función específica de CardView
                    card.setCardBackgroundColor(ContextCompat.getColor(v.context, R.color.colorhover2))
                    card.cardElevation = 15f

                    title.setTextColor(Color.YELLOW)
                    title.isSelected = true
                    v.z = 10f
                } else {
                    // --- AL PERDER FOCO (LIMPIEZA TOTAL) ---
                    card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()

                    // Volver al color de fondo original del XML (#1A1A1A)
                    card.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
                    card.cardElevation = 6f

                    title.setTextColor(Color.WHITE)
                    title.isSelected = false
                    v.z = 0f
                }
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

        // --- Lógica de Etiquetas (Gratis / Alquilar) ---
        if (movie.isValid) {
            holder.badge.text = "Gratis ✅"
            holder.badge.setBackgroundColor(Color.parseColor("#006064"))
        } else {
            holder.badge.text = "Alquilar 💳"
            holder.badge.setBackgroundColor(Color.parseColor("#880E4F"))
        }
        holder.badge.visibility = View.VISIBLE

        holder.title.text = movie.title

        // --- Carga de Imagen Optimizada ---
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            // 1. Prioridad alta para que cargue antes que nada
            .priority(com.bumptech.glide.Priority.HIGH)
            // 2. Usar la imagen que ya está en caché del carrusel anterior
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            // 3. Quitar el placeholder o usar una miniatura muy pequeña mientras carga
            .thumbnail(0.1f)
            // 4. No limpiar el ImageView si la URL es la misma (evita el parpadeo del icono)
            .placeholder(holder.poster.drawable)
            .error(R.drawable.pelifondo)
            .into(holder.poster)
    }

    override fun getItemCount(): Int = items.size
}