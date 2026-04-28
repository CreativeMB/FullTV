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
                // Color de fondo al enfocar (Paridad con el principal)
                v.setBackgroundColor(
                    if (hasFocus)
                        ContextCompat.getColor(v.context, R.color.colorhover2)
                    else
                        Color.TRANSPARENT
                )

                // Animación de escala 1.08f (Paridad con el principal)
                val scale = if (hasFocus) 1.08f else 1.0f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()

                title.isSelected = hasFocus // Activa Marquee
                title.setTextColor(Color.YELLOW)
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