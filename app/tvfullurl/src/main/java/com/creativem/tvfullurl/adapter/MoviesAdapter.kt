package com.creativem.tvfullurl.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R

class MoviesAdapter(
    private var movieList: List<Movie>, // Cambié a var para permitir la actualización
    private val onDeleteClick: (String) -> Unit,
    private val onEditClick: (Movie) -> Unit,
    private val isEditable: Boolean
) : RecyclerView.Adapter<MoviesAdapter.MovieViewHolder>() {

    // La lista filtrada ahora se inicializa con la lista completa
    private var movieListFiltered: List<Movie> = movieList

    // Método para actualizar la lista de películas
    fun updateMovieList(newMovieList: List<Movie>) {
        movieList = newMovieList
        movieListFiltered = newMovieList // Asegúrate de que la lista filtrada esté actualizada
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.itemmovies, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie: Movie = movieListFiltered[position]
        holder.bind(movie)

        // Asigna los datos a las vistas
        holder.titleTextView.text = movie.title
        holder.yearTextView.text = movie.year
        holder.userNameTextView.text = movie.userName // Mostrar el nombre del usuario

        // Configurar el botón de eliminar
        holder.deleteButton.setOnClickListener {
            onDeleteClick(movie.id ?: "")
        }

        // Configurar el botón de editar si es editable
        if (isEditable) {
            holder.editButton.setOnClickListener {
                onEditClick.invoke(movie)
            }
            holder.editButton.visibility = View.VISIBLE
        } else {
            holder.editButton.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return movieListFiltered.size
    }

    class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        var yearTextView: TextView = itemView.findViewById(R.id.yearTextView)
        var userNameTextView: TextView = itemView.findViewById(R.id.userNameTextView)
        var deleteButton: TextView = itemView.findViewById(R.id.deleteButton)
        var editButton: TextView = itemView.findViewById(R.id.editButton)

        fun bind(movie: Movie) {
            titleTextView.text = movie.title
            yearTextView.text = movie.year
            userNameTextView.text = movie.userName // Mostrar el nombre del usuario
        }
    }

    // Método para filtrar las películas según el query del SearchView
    fun filter(query: String) {
        movieListFiltered = if (query.isEmpty()) {
            movieList
        } else {
            movieList.filter {
                it.title.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
}