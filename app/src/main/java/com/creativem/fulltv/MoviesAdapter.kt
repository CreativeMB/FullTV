package com.creativem.fulltv
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class Movie(val title: String, val imageUrl: String,  val streamUrls: List<String>)

class MovieAdapter(
    private val movieList: List<Movie>,
    private val itemClick: (List<String>) -> Unit // Cambiar a List<String> para manejar múltiples URLs
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image)
        val movieTitle: TextView = view.findViewById(R.id.movie_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.movie_item, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]

        // Setear el título de la película
        holder.movieTitle.text = movie.title

        // Cargar la imagen de la película usando Glide
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.icono)  // Imagen de marcador de posición
            .into(holder.movieImage)

        // Manejar el clic en el elemento
        holder.itemView.setOnClickListener {
            itemClick(movie.streamUrls) // Llamar al callback con la lista de URLs de stream
        }
    }

    override fun getItemCount(): Int {
        return movieList.size
    }
}