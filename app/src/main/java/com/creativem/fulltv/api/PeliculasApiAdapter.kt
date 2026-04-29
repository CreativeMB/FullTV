package com.creativem.fulltv.api

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

class PeliculasApiAdapter(
    private val movieList: MutableList<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<PeliculasApiAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image_small)
        val movieTitle: TextView = view.findViewById(R.id.movie_title_small)
        val cardContainer: View = view // El LinearLayout principal del item

        init {
            // Permitir que el item sea enfocable
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            // Dentro del init del ViewHolder
            view.setOnFocusChangeListener { v, hasFocus ->
                val card = v as androidx.cardview.widget.CardView

                if (hasFocus) {
                    // 1. Zoom
                    itemView.animate()
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(200) // Un poco más de tiempo para que la animación sea elegante
                        .start()

                    // 2. Cambio de Color (Usa setCardBackgroundColor para no perder los bordes redondeados)
                    card.setCardBackgroundColor(Color.parseColor("#FFD700")) // Color Oro/Amarillo para el borde
                    card.cardElevation = 15f

                    movieTitle.setTextColor(Color.YELLOW)
                    movieTitle.isSelected = true
                } else {
                    card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()

                    // 3. Volver al color original
                    card.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
                    card.cardElevation = 4f

                    movieTitle.setTextColor(Color.WHITE)
                    movieTitle.isSelected = false
                }
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
            .inflate(R.layout.item_api_peluculas, parent, false)
        return SmallMovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmallMovieViewHolder, position: Int) {
        val movie = movieList[position]

        // 1. EXTRAER EL AÑO Y CONFIGURAR EL TÍTULO
        // Suponiendo que el título viene de la API como "Nombre (2024-05-10)"
        // o que quieres que se vea limpio: "Nombre (2024)"

        val tituloLimpio = movie.title.replace(Regex("\\(\\d{4}-\\d{2}-\\d{2}\\)"), "").trim()

        // Si tu objeto Movie tiene una propiedad para la fecha, úsala.
        // Si no, podemos intentar extraerla del string que mandamos desde la Activity.
        val yearMatch = Regex("(\\d{4})").find(movie.title)
        val año = yearMatch?.value ?: ""

        holder.movieTitle.apply {
            text = if (año.isNotEmpty()) "$tituloLimpio ($año)" else tituloLimpio
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSingleLine = true
        }

        // 2. CARGA DE IMAGEN CON fitXY (Como tú lo quieres)
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            // Quitamos centerCrop() porque tú quieres que se adapte al contenedor (fitXY)
            // Usamos .dontAnimate() para evitar parpadeos al reciclar
            .dontAnimate()
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.icono)
            .into(holder.movieImage)

        // Resaltar si es la película seleccionada
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