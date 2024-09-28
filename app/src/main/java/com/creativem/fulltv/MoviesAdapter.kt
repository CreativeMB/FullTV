package com.creativem.fulltv
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.content.Context
import java.security.Timestamp

data class Movie(
    val title: String,
    val synopsis: String,
    val imageUrl: String,
    val streamUrl: String,
    val createdAt: com.google.firebase.Timestamp

)

class MovieAdapter(
    private val movieList: MutableList<Movie>, // MutableList para poder modificarla
    private val onMovieClick: (String) -> Unit // Callback para manejar clics en los items
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image)
        val movieTitle: TextView = view.findViewById(R.id.movie_title)
        val movieSynopsis: TextView = view.findViewById(R.id.movie_synopsis)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    notifyItemChanged(selectedPosition)
                    selectedPosition = position
                    notifyItemChanged(selectedPosition)
                    onMovieClick(movieList[position].streamUrl)
                }
            }

            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.colorhover))
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.movie_item, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]
        holder.movieTitle.text = movie.title
        holder.movieSynopsis.text = movie.synopsis

        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.portada)
            .error(R.drawable.portada)
            .into(holder.movieImage)

        // Aplicar el color de fondo si el elemento está seleccionado
        holder.itemView.setBackgroundColor(
            if (position == selectedPosition)
                ContextCompat.getColor(holder.itemView.context, R.color.colorSelected)
            else
                Color.TRANSPARENT
        )
    }

    override fun getItemCount(): Int {
        return movieList.size
    }

    fun addMovie(movie: Movie) {
        if (!containsMovie(movie)) {
            // Insertar en la posición correcta según la fecha de creación
            val insertIndex = movieList.indexOfFirst { it.createdAt < movie.createdAt }
            if (insertIndex == -1) {
                // Si no hay películas más antiguas, se agrega al final
                movieList.add(movie)
            } else {
                // Insertar en la posición correspondiente
                movieList.add(insertIndex, movie)
            }
            notifyDataSetChanged() // Notificar que el dataset ha cambiado
        }
    }

    fun containsMovie(movie: Movie): Boolean {
        return movieList.any { it.title == movie.title }
    }
}