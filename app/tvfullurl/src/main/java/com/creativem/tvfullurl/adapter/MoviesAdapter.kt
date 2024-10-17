package com.creativem.tvfullurl.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R

class MoviesAdapter(
    private val movieList: List<Movie>,
    private val onDeleteClick: (String) -> Unit, // Lambda para manejar la eliminación
    private val onEditClick: (Movie) -> Unit, // Lambda para manejar la edición
    private val isEditable: Boolean // Indica si el adaptador debe manejar la edición
) : RecyclerView.Adapter<MoviesAdapter.MovieViewHolder>() {

    // Lista filtrada que inicialmente es igual a la lista completa
    private var movieListFiltered: List<Movie> = movieList

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

        // Configurar el botón de eliminar
        holder.deleteButton.setOnClickListener {
            onDeleteClick(movie.id ?: "") // Llamar a la función para eliminar
        }

        // Configurar el botón de editar si es editable
        if (isEditable) {
            holder.editButton.setOnClickListener {
                onEditClick.invoke(movie) // Llamar a la función para editar
            }
            holder.editButton.visibility = View.VISIBLE // Mostrar botón de editar
        } else {
            holder.editButton.visibility = View.GONE // Ocultar botón de editar
        }
    }

    override fun getItemCount(): Int {
        return movieListFiltered.size
    }

    // Clase ViewHolder para manejar las vistas del RecyclerView
    class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var titleTextView: TextView = itemView.findViewById(R.id.titleTextView) // TextView para el título
        var yearTextView: TextView = itemView.findViewById(R.id.yearTextView) // TextView para el año
        var deleteButton: TextView = itemView.findViewById(R.id.deleteButton) // Botón para eliminar
        var editButton: TextView = itemView.findViewById(R.id.editButton) // Botón para editar

        fun bind(movie: Movie) {
            // Aquí puedes personalizar cómo deseas mostrar cada película en el RecyclerView
            titleTextView.text = movie.title
            yearTextView.text = movie.year
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
        notifyDataSetChanged() // Notificar al adaptador de los cambios
    }
}