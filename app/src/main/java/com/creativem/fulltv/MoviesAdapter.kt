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
data class Movie(
    val title: String,
    val synopsis: String,
    val imageUrl: String,
    val streamUrl: String
)

class MovieAdapter(
    private val movieList: List<Movie>,
    private val onMovieClick: (String) -> Unit,
    private val context: Context // Usar android.content.Context
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

            // Establecer el foco en el elemento cuando se selecciona
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Cambiar el estilo del elemento al tener foco (por ejemplo, agregar un fondo)
                    view.setBackgroundColor(ContextCompat.getColor(context, R.color.colorhover))
                } else {
                    // Restaurar el estilo del elemento al perder foco
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.movie_item, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]
        holder.movieTitle.text = movie.title
        holder.movieSynopsis.text = movie.synopsis

        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.icono) // Reemplaza con tu placeholder
            .error(R.drawable.icono)       // Reemplaza con tu imagen de error
            .into(holder.movieImage)

        // Cambiar el fondo del item seleccionado
        if (position == selectedPosition) {
            // Aquí cambiamos el color del ítem seleccionado
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.colorSelected))
        } else {
            // Aquí establecemos el color por defecto (puedes cambiarlo si prefieres otro color)
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun getItemCount(): Int {
        return movieList.size
    }
}