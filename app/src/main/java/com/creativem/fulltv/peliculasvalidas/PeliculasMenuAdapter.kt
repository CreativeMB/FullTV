package com.creativem.fulltv.peliculasvalidas
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Movie
import android.graphics.Color
import com.creativem.fulltv.principal.Main

class PeliculasMenuAdapter(
    private val movieList: MutableList<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<PeliculasMenuAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image_small)
        val movieTitle: TextView = view.findViewById(R.id.movie_title_small)

        init {
            view.setOnFocusChangeListener { v, hasFocus ->
                movieTitle.isSelected = hasFocus

                if (hasFocus) {
                    val movie = movieList[bindingAdapterPosition]
                    if (!movie.imageUrl.isNullOrEmpty()) {
                        (v.context as? Main)?.setFondoDesdeUrl(movie.imageUrl)
                    } else {
                        (v.context as? Main)?.restaurarFondoAnimado()
                    }
                }

                v.setBackgroundColor(
                    if (hasFocus)
                        ContextCompat.getColor(view.context, R.color.colorhover2)
                    else
                        ContextCompat.getColor(view.context, R.color.colorNotSelected)
                )
            }

            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val movie = movieList[position]

                    // 🔄 Fondo dinámico
                    if (!movie.imageUrl.isNullOrEmpty()) {
                        (view.context as? Main)?.setFondoDesdeUrl(movie.imageUrl)
                    } else {
                        (view.context as? Main)?.restaurarFondoAnimado()
                    }

                    // 🔁 Ejecutar callback
                    onMovieClick(movie)

                    // 🔁 Marcar como seleccionado visualmente
                    notifyItemChanged(selectedPosition)
                    selectedPosition = position
                    notifyItemChanged(selectedPosition)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmallMovieViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_peliculas_validas, parent, false)
        return SmallMovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmallMovieViewHolder, position: Int) {
        val movie = movieList[position]

        holder.movieTitle.apply {
            text = movie.title
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            setHorizontallyScrolling(true)
        }

        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.icono)
            .into(holder.movieImage)
    }

    override fun getItemCount(): Int = movieList.size

    fun updateMovies(newMovies: List<Movie>) {
        movieList.clear()
        val validMovies = newMovies.filter { isUrlValid(it.streamUrl) }
        movieList.addAll(validMovies)
        notifyDataSetChanged()
    }

    private fun isUrlValid(url: String): Boolean {
        return url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))
    }
}