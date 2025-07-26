package com.creativem.tvfullurl.adapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.tvfullurl.R

class PedidosAdapter(
    private var movieList: List<Movie>, // Lista de películas
    private val onDeleteClick: (String) -> Unit // Acción para eliminar un pedido (pasa el movieId)
) : RecyclerView.Adapter<PedidosAdapter.PedidoViewHolder>() {

    private var movieListFiltered: List<Movie> = movieList // Lista filtrada

    // Método para actualizar la lista de películas
    fun updateMovieList(newMovieList: List<Movie>) {
        movieList = newMovieList
        movieListFiltered = newMovieList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PedidoViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.item_pedido, parent, false)
        return PedidoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PedidoViewHolder, position: Int) {
        val movie: Movie = movieListFiltered[position]

        // Asigna los datos a las vistas
        holder.userNameTextView.text = movie.nombre.ifEmpty { "Usuario desconocido" }
        holder.titleTextView.text = movie.title.ifEmpty { "Título no disponible" }
        holder.castvTextView.text = "CasTV: ${movie.castv}"
        holder.emailTextView.text = movie.correo.ifEmpty { "Email no disponible" }
        holder.fechaTextView.text = "${movie.fecha}"

        // Maneja el clic en el email
        holder.emailTextView.setOnClickListener {
            val email = movie.correo
            if (email.isNotEmpty()) {
                // Copiar el email al portapapeles
                val clipboard = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Email", email)
                clipboard.setPrimaryClip(clip)

                // Notificar al usuario que el correo se ha copiado
                Toast.makeText(holder.itemView.context, "Correo copiado al portapapeles", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("PedidosAdapter", "Email vacío")
                Toast.makeText(holder.itemView.context, "El correo electrónico no está disponible", Toast.LENGTH_SHORT).show()
            }
        }

        // Maneja el clic en el title
        holder.titleTextView.setOnClickListener {
            val title = movie.title
            if (title.isNotEmpty()) {
                // Copiar el title al portapapeles
                val clipboard = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Title", title)
                clipboard.setPrimaryClip(clip)

                // Notificar al usuario que el título se ha copiado
                Toast.makeText(holder.itemView.context, "Título copiado al portapapeles", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("PedidosAdapter", "Title vacío")
                Toast.makeText(holder.itemView.context, "El título no está disponible", Toast.LENGTH_SHORT).show()
            }
        }

        // Maneja el clic en el botón de eliminar
        holder.deleteButton.setOnClickListener {
            onDeleteClick(movie.id) // Pasa el ID del pedido a la función deletePedido
        }
    }

    override fun getItemCount(): Int {
        return movieListFiltered.size
    }

    class PedidoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userNameTextView: TextView = itemView.findViewById(R.id.tvUserName)
        val titleTextView: TextView = itemView.findViewById(R.id.tvTitle)
        val emailTextView: TextView = itemView.findViewById(R.id.tvEmail)
        val castvTextView: TextView = itemView.findViewById(R.id.tvYear)
        val fechaTextView: TextView = itemView.findViewById(R.id.tvFecha)
        val deleteButton: TextView = itemView.findViewById(R.id.deleteButton)
    }

    // Método para filtrar las películas según el query del SearchView
    fun filter(query: String) {
        movieListFiltered = if (query.isEmpty()) {
            movieList
        } else {
            movieList.filter {
                it.nombre.contains(query, ignoreCase = true) ||
                        it.title.contains(query, ignoreCase = true) ||
                        it.castv.toString().contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
}