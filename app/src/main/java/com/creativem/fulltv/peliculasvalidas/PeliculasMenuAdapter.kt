package com.creativem.fulltv.peliculasvalidas

import android.graphics.Color
import android.graphics.Typeface
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
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.api.ApiPeliculaActivity // Asegúrate de importar esto si lo usas

class PeliculasMenuAdapter(
    private val movieList: MutableList<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<PeliculasMenuAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image_small)
        val movieTitle: TextView = view.findViewById(R.id.movie_title_small)
        val cardContainer: View = view // El LinearLayout principal del item

        init {
            // Permitir que el item sea enfocable
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnFocusChangeListener { v, hasFocus ->
                // 1. Animación de Escala (Zoom) - Vital en TV
                if (hasFocus) {
                    v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
                    v.elevation = 10f
                    movieTitle.visibility = View.VISIBLE // Asegurar que se vea
                    movieTitle.isSelected = true // Activar Marquee
                    movieTitle.setTypeface(null, Typeface.BOLD)
                    movieTitle.setTextColor(Color.YELLOW) // Color resaltado al enfocar
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    v.elevation = 0f
                    movieTitle.isSelected = false // Apagar Marquee
                    movieTitle.setTypeface(null, Typeface.NORMAL)
                    movieTitle.setTextColor(Color.WHITE)
                }

                // 2. Color de fondo del borde/contenedor
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null
            }

            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val movie = movieList[position]

                    // Actualizar posición seleccionada visualmente
                    val oldPos = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(oldPos)
                    notifyItemChanged(selectedPosition)

                    onMovieClick(movie)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmallMovieViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_peliculas_validas, parent, false)
        return SmallMovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmallMovieViewHolder, position: Int) {
        val movie = movieList[position]

        // Configuración de Títulos
        holder.movieTitle.apply {
            text = movie.title
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSingleLine = true
            isSelected = true

        }

        // Carga de Imagen Optimizada
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .centerCrop() // Para que todas las miniaturas tengan el mismo aspecto
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.icono)
            .into(holder.movieImage)

        // Resaltar si es la película que se está reproduciendo actualmente
        if (position == selectedPosition) {
            holder.cardContainer.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.card_focused_background)
        }
    }

    override fun getItemCount(): Int = movieList.size

    fun updateMovies(newMovies: List<Movie>) {
        movieList.clear()
        // Ya no filtramos aquí por string, confiamos en que vienen validadas del objeto Validacioneslista
        movieList.addAll(newMovies)
        notifyDataSetChanged()
    }
}