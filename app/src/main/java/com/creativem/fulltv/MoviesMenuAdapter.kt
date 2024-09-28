package com.creativem.fulltv
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// Define the MoviesMenuAdapter similar to MovieAdapter but with smaller items
class MoviesMenuAdapter(
    private val movieList: MutableList<Movie>, // Lista mutable para agregar nuevos elementos
    private val onMovieClick: (String) -> Unit // Callback para manejar clics en los items
) : RecyclerView.Adapter<MoviesMenuAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image_small) // Aquí usas el layout pequeño
        val movieTitle: TextView = view.findViewById(R.id.movie_title_small)

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
                    view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.colorhover2))
                } else {
                    view.setBackgroundColor(android.graphics.Color.BLACK)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmallMovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.movie_menu_item, parent, false)
        return SmallMovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmallMovieViewHolder, position: Int) {
        val movie = movieList[position]
        holder.movieTitle.text = movie.title

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
                android.graphics.Color.BLACK
        )
    }

    override fun getItemCount(): Int {
        return movieList.size
    }

    // Función para agregar una nueva película
    fun addMovie(movie: Movie) {
        if (!containsMovie(movie)) {
            movieList.add(movie)
            notifyItemInserted(movieList.size - 1) // Notificar que se ha añadido un elemento nuevo
        }
    }

    // Verificar si la película ya está en la lista
    fun containsMovie(movie: Movie): Boolean {
        return movieList.any { it.title == movie.title }
    }
}