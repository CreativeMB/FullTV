package com.creativem.tvfullurl.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R

class MoviesAdapter(
    private var movieList: List<Movie>,
    private val onDeleteClick: (String) -> Unit,
    private val onAssignClick: (Movie) -> Unit,
    private val onEditClick: (Movie) -> Unit,
    private val isEditable: Boolean
) : RecyclerView.Adapter<MoviesAdapter.MovieViewHolder>() {

    private var movieListFiltered: List<Movie> = movieList

    fun updateMovieList(newMovieList: List<Movie>) {
        movieList = newMovieList
        movieListFiltered = newMovieList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.item_movies_pedidos, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie: Movie = movieListFiltered[position]
        holder.bind(movie)

        // Asigna los datos a las vistas
        holder.titleTextView.text = movie.title
// Configurar visibilidad, datos del solicitante y fecha formateada
        // Modifica la sección de bindeo de datos dentro de onBindViewHolder en MoviesAdapter.kt:
        if (!movie.email.isNullOrBlank()) {
            holder.requesterInfoTextView.visibility = View.VISIBLE

            val fechaHoraFormateada = formatearFecha(movie.requestTimestamp)

            // 🟢 Agregamos etiqueta informativa de programación si existe
            val programacionTexto = if (!movie.fechaActivacion.isNullOrBlank() && movie.horaActivacion != -1) {
                val horaAmPm = if (movie.horaActivacion >= 12) "PM" else "AM"
                val hora12 = if (movie.horaActivacion % 12 == 0) 12 else movie.horaActivacion % 12
                "\n📅 Programación: ${movie.fechaActivacion} a las $hora12:00 $horaAmPm ⚠️"
            } else {
                "\n📅 Programación: Inmediato"
            }

            holder.requesterInfoTextView.text =
                "Pedido por: ${movie.userId} (${movie.email})$programacionTexto\n📝 Solicitud: $fechaHoraFormateada"
        } else {
            holder.requesterInfoTextView.visibility = View.GONE
        }
               // Configurar el botón de eliminar
        holder.deleteButton.setOnClickListener {
            onDeleteClick(movie.id ?: "")
        }

        // Configurar el nuevo botón de asignar usuario
        holder.assignButton.setOnClickListener {
            onAssignClick(movie)
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

        // 🟢 NUEVO: Referencia al TextView del solicitante
        var requesterInfoTextView: TextView = itemView.findViewById(R.id.requesterInfoTextView)

        var deleteButton: TextView = itemView.findViewById(R.id.deleteButton)
        var editButton: TextView = itemView.findViewById(R.id.editButton)
        var assignButton: TextView = itemView.findViewById(R.id.assignButton)

        fun bind(movie: Movie) {
            titleTextView.text = movie.title
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
    private fun formatearFecha(timestamp: Long): String {
        if (timestamp == 0L) return ""
        // Formato: día/mes/año hora:minuto AM/PM
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}