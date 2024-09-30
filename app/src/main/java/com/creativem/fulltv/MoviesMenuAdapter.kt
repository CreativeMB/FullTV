package com.creativem.fulltv
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.Movie

class MoviesMenuAdapter(
    private val movieList: MutableList<Movie>, // Lista mutable para agregar nuevos elementos
    private val onMovieClick: (String) -> Unit // Callback para manejar clics en los items
) : RecyclerView.Adapter<MoviesMenuAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image_small)
        val movieTitle: TextView = view.findViewById(R.id.movie_title_small)

        init {
            // Manejador de clics en el item
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    notifyItemChanged(selectedPosition) // Actualiza el ítem previamente seleccionado
                    selectedPosition = position // Actualiza la nueva posición seleccionada
                    notifyItemChanged(selectedPosition)

                    // Llama al callback de clic
                    onMovieClick(movieList[position].streamUrl)
                }
            }

            // Manejador de enfoque para cambiar el color de fondo al recibir foco
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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.movie_menu_item, parent, false)
        return SmallMovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmallMovieViewHolder, position: Int) {
        val movie = movieList[position]
        holder.movieTitle.text = movie.title

        // Carga la imagen de la película usando Glide
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.portada)
            .error(R.drawable.portada)
            .into(holder.movieImage)

        // Aplicar color de fondo si el elemento está seleccionado
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

    // Función para agregar una nueva película (solo si la URL es válida)
    fun addMovie(movie: Movie) {
        if (movie.isValid && !containsMovie(movie)) { // Verifica que la película sea válida
            movieList.add(movie)
            notifyItemInserted(movieList.size - 1) // Notifica que se ha añadido un nuevo elemento
        }
    }

    // Verificar si la película ya está en la lista
    fun containsMovie(movie: Movie): Boolean {
        return movieList.any { it.title == movie.title }
    }

    // Actualiza la lista con nuevas películas
    fun updateMovies(newMovies: List<Movie>) {
        movieList.clear()
        movieList.addAll(newMovies.filter { it.isValid })
        notifyDataSetChanged()
    }
}