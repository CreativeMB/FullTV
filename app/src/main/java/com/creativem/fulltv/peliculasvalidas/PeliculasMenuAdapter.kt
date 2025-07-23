package com.creativem.fulltv.peliculasvalidas

import android.graphics.Color
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
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Main
import com.creativem.fulltv.principal.Movie

class PeliculasMenuAdapter(
    private val movieList: MutableList<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<PeliculasMenuAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movieimage)
        val movieTitle: TextView = view.findViewById(R.id.movietitle)
        val etiquetaValida: TextView = view.findViewById(R.id.etiqueta)

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

                // Fondo elegante (drawable) en lugar de color plano
                v.background = if (hasFocus)
                    ContextCompat.getDrawable(view.context, R.drawable.card_focused_background)
                else
                    null

                // Efecto de escala (zoom al enfocar)
                val scale = if (hasFocus) 1.05f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
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

                    notifyItemChanged(selectedPosition)
                    selectedPosition = position
                    notifyItemChanged(selectedPosition)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmallMovieViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_peliculas_validas, parent, false).apply {
                // Margen opcional para separar ítems
                val layoutParams = ViewGroup.MarginLayoutParams(layoutParams)
                layoutParams.setMargins(8, 8, 8, 8)
                this.layoutParams = layoutParams
            }

        return SmallMovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmallMovieViewHolder, position: Int) {
        val movie = movieList[position]

        // Etiqueta
        holder.etiquetaValida.text = "Gratis ✅"
        holder.etiquetaValida.setBackgroundColor(Color.parseColor("#006064"))
        holder.etiquetaValida.visibility = View.VISIBLE

        // Título
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

        // Imagen
        CastvHelper.loadImage(holder.itemView.context, holder.movieImage, movie.imageUrl)
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