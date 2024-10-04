package com.creativem.fulltv
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.ui.data.Movie

class MoviesMenuAdapter(
    private val movieList: MutableList<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<MoviesMenuAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image_small)
        val movieTitle: TextView = view.findViewById(R.id.movie_title_small)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && selectedPosition != position) {
                    notifyItemChanged(selectedPosition) // Actualiza el ítem previamente seleccionado
                    selectedPosition = position
                    notifyItemChanged(selectedPosition) // Actualiza la nueva posición seleccionada

                    onMovieClick(movieList[position]) // Llama al callback con el objeto Movie
                }
            }

            view.setOnFocusChangeListener { _, hasFocus ->
                view.setBackgroundColor(
                    if (hasFocus) ContextCompat.getColor(view.context, R.color.colorhover2)
                    else ContextCompat.getColor(view.context, R.color.colorNotSelected)
                )
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

        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.icono)
            .error(R.drawable.icono)
            .into(holder.movieImage)

        holder.itemView.setBackgroundColor(
            if (position == selectedPosition)
                ContextCompat.getColor(holder.itemView.context, R.color.colorSelected)
            else
                ContextCompat.getColor(holder.itemView.context, R.color.colorNotSelected)
        )
    }

    override fun getItemCount(): Int = movieList.size

    fun updateMovies(newMovies: List<Movie>) {
        movieList.clear()
        movieList.addAll(newMovies.filter { it.isValid })
        notifyDataSetChanged()
    }
}
