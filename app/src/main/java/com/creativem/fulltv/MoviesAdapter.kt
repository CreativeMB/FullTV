package com.creativem.fulltv
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class Movie(
    val title: String,
    val synopsis: String,
    val imageUrl: String,
    val streamUrl: String
)

class MovieAdapter(
    private val movieList: List<Movie>,
    private val onMovieClick: (String) -> Unit
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    inner class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image)
        val movieTitle: TextView = view.findViewById(R.id.movie_title)
        val movieSynopsis: TextView = view.findViewById(R.id.movie_synopsis)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMovieClick(movieList[position].streamUrl)
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
    }

    override fun getItemCount(): Int {
        return movieList.size
    }
}