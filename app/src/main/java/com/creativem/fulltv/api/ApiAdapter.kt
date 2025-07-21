package com.creativem.fulltv.api

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
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import com.creativem.fulltv.principal.Main

class ApiAdapter(
    private val movieList: MutableList<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<ApiAdapter.SmallMovieViewHolder>() {

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movieimage)
        val movieTitle: TextView = view.findViewById(R.id.movietitle)
        val etiquetaValida: TextView = view.findViewById(R.id.etiqueta)

        init {
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnFocusChangeListener { v, hasFocus ->
                movieTitle.isSelected = hasFocus

                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && hasFocus) {
                    val movie = movieList[position]
                    (v.context as? Main)?.setFondoDesdeUrl(movie.imageUrl)
                }

                // Aplica animación SOLO si está enfocado
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                else
                    null

                val scale = if (hasFocus) 1.1f else 1f
                v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(150)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val movie = movieList[position]
                    if (!movie.imageUrl.isNullOrEmpty()) {
                        (view.context as? Main)?.setFondoDesdeUrl(movie.imageUrl)
                    } else {
                        (view.context as? Main)?.restaurarFondoAnimado()
                    }
                    onMovieClick(movie)
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

        holder.etiquetaValida.text = "Alquilar $50💳"
        holder.etiquetaValida.setBackgroundColor(Color.parseColor("#880E4F"))
        holder.etiquetaValida.visibility = View.VISIBLE

        holder.movieTitle.apply {
            text = movie.title
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            isFocusable = false
            isFocusableInTouchMode = false
            setHorizontallyScrolling(true)
            marqueeRepeatLimit = -1
        }

        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.icono)
            .into(holder.movieImage)

        // ❌ Ya no necesitas aplicar fondo/escala aquí. Lo hace el sistema al enfocar.
    }

    override fun getItemCount(): Int = movieList.size

    fun updateMovies(newMovies: List<Movie>) {
        val diffCallback = MovieDiffCallback(this.movieList, newMovies)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.movieList.clear()
        this.movieList.addAll(newMovies)
        diffResult.dispatchUpdatesTo(this)
    }

    fun addMovies(newMovies: List<Movie>) {
        val startPosition = movieList.size
        movieList.addAll(newMovies)
        notifyItemRangeInserted(startPosition, newMovies.size)
    }

    // 🔁 Comparador para DiffUtil
    class MovieDiffCallback(
        private val oldList: List<Movie>,
        private val newList: List<Movie>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            // Compara por ID único
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            // Compara el contenido completo
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

}