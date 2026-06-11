package com.creativem.tvfullurl.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R
import java.text.Normalizer

class MoviesAdapter(
    private var movieList: List<Movie>, // Copia de respaldo con todos los elementos
    private val onDeleteClick: (String) -> Unit,
    private val onAssignClick: (Movie) -> Unit,
    private val onEditClick: (Movie) -> Unit,
    private val isEditable: Boolean
) : RecyclerView.Adapter<MoviesAdapter.MovieViewHolder>() {

    // Lista que se dibuja activamente en el RecyclerView
    private var movieListFiltered: List<Movie> = movieList

    // Sincroniza ambas listas cuando Firebase actualiza los datos en tiempo real
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

        if (!movie.email.isNullOrBlank()) {
            holder.requesterInfoTextView.visibility = View.VISIBLE

            val fechaHoraFormateada = formatearFecha(movie.requestTimestamp)

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

        holder.deleteButton.setOnClickListener {
            onDeleteClick(movie.id ?: "")
        }

        holder.assignButton.setOnClickListener {
            onAssignClick(movie)
        }

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

    class MovieViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        var titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        var requesterInfoTextView: TextView = itemView.findViewById(R.id.requesterInfoTextView)
        var deleteButton: TextView = itemView.findViewById(R.id.deleteButton)
        var editButton: TextView = itemView.findViewById(R.id.editButton)
        var assignButton: TextView = itemView.findViewById(R.id.assignButton)

        fun bind(movie: Movie) {
            titleTextView.text = movie.title
        }
    }

    // 🟢 Filtrado inteligente por coincidencias de texto
    fun filter(query: String) {
        val cleanQuery = query.normalizeForSearch()

        movieListFiltered = if (cleanQuery.isEmpty()) {
            movieList // Si no hay búsqueda, restauramos toda la lista original
        } else {
            // Buscamos dentro de la lista original (movieList) y asignamos a la filtrada
            movieList.filter { movie ->
                val titleClean = (movie.title ?: "").normalizeForSearch()
                val originalTitleClean = (movie.originalTitle ?: "").normalizeForSearch()

                titleClean.contains(cleanQuery) || originalTitleClean.contains(cleanQuery)
            }
        }
        notifyDataSetChanged() // Notifica el cambio al RecyclerView
    }

    private fun formatearFecha(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun String.normalizeForSearch(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .trim()
    }
}