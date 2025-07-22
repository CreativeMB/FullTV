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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmallMovieViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_peliculas_validas, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return SmallMovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmallMovieViewHolder, position: Int) {
        val movie = movieList[position]

        holder.etiquetaValida.text = "Alquilar $50💳"
        holder.etiquetaValida.setBackgroundColor(Color.parseColor("#880E4F"))
        holder.etiquetaValida.visibility = View.VISIBLE

        holder.movieTitle.apply {
            text = movie.title
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
            setHorizontallyScrolling(true)
        }

        // Imagen
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.icono)
            .into(holder.movieImage)

        // Eventos de foco y clic
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            holder.movieTitle.isSelected = hasFocus
            holder.movieTitle.setTextColor(if (hasFocus) Color.YELLOW else Color.WHITE)

            if (hasFocus) {
                (view.context as? Main)?.setFondoDesdeUrl(movie.imageUrl)
                view.background = ContextCompat.getDrawable(view.context, R.drawable.card_focused_background)
            } else {
                view.background = null
            }

            val scale = if (hasFocus) 1.1f else 1f
            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        holder.itemView.setOnClickListener {
            if (!movie.imageUrl.isNullOrEmpty()) {
                (holder.itemView.context as? Main)?.setFondoDesdeUrl(movie.imageUrl)
            } else {
                (holder.itemView.context as? Main)?.restaurarFondoAnimado()
            }
            onMovieClick(movie)
        }
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

    class MovieDiffCallback(
        private val oldList: List<Movie>,
        private val newList: List<Movie>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
