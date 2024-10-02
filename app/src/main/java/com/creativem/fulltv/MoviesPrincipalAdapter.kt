//package com.creativem.fulltv
//import android.graphics.Color
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.core.content.ContextCompat
//import androidx.recyclerview.widget.RecyclerView
//import com.bumptech.glide.Glide
//import com.creativem.fulltv.ui.data.Movie
//
//
//class MovieAdapter(
//    private val movieList: MutableList<Movie>,
//    private val onMovieClick: (String) -> Unit
//) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {
//
//    private var selectedPosition = RecyclerView.NO_POSITION
//
//    // ViewHolder para cada elemento de la lista
//    inner class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val movieImage: ImageView = view.findViewById(R.id.movie_image)
//        val movieTitle: TextView = view.findViewById(R.id.movie_title)
//        val movieSynopsis: TextView = view.findViewById(R.id.movie_synopsis)
//
//        init {
//            view.setOnClickListener {
//                val position = bindingAdapterPosition
//                if (position != RecyclerView.NO_POSITION) {
//                    // Deselecciona el item anterior
//                    notifyItemChanged(selectedPosition)
//                    selectedPosition = position
//                    // Selecciona el nuevo item
//                    notifyItemChanged(selectedPosition)
//                    onMovieClick(movieList[position].streamUrl)
//                }
//            }
//
//            view.setOnFocusChangeListener { _, hasFocus ->
//                if (hasFocus) {
//                    view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.colorhover))
//                } else {
//                    view.setBackgroundColor(Color.TRANSPARENT)
//                }
//            }
//        }
//    }
//
//    // Crea nuevas vistas (invocado por el LayoutManager)
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
//        val view = LayoutInflater.from(parent.context).inflate(R.layout.movies_principal_item, parent, false)
//        return MovieViewHolder(view)
//    }
//
//    // Asocia datos de la lista a las vistas del ViewHolder
//    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
//        val movie = movieList[position]
//        holder.movieTitle.text = movie.title
//        holder.movieSynopsis.text = movie.synopsis
//
//        // Cargar la imagen con Glide
//        Glide.with(holder.itemView.context)
//            .load(movie.imageUrl)
//            .placeholder(R.drawable.portada)
//            .error(R.drawable.portada)
//            .into(holder.movieImage)
//
//        // Cambia el color de fondo según si la película es válida o inválida
//        if (movie.isValid) {
//            holder.itemView.setBackgroundColor(
//                if (position == selectedPosition)
//                    ContextCompat.getColor(holder.itemView.context, R.color.colorSelected)
//                else
//                    Color.TRANSPARENT
//            )
//        } else {
//            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorPrimary)) // Color para películas inválidas
//        }
//    }
//
//    // Retorna el tamaño de la lista de películas
//    override fun getItemCount(): Int {
//        return movieList.size
//    }
//
//    // Función para añadir nuevas películas y notificar al adaptador
//    fun addMovies(movies: List<Movie>) {
//        this.movieList.addAll(movies)
//        notifyDataSetChanged()
//    }
//}