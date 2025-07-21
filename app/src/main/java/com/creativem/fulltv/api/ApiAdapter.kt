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
import com.creativem.fulltv.principal.Main

class ApiAdapter(
    private val movieList: MutableList<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<ApiAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movieimage)
        val movieTitle: TextView = view.findViewById(R.id.movietitle)
        val etiquetaValida: TextView = view.findViewById(R.id.etiqueta)

        init {
            // Asegura que el itemView es enfocable
            view.isFocusable = true
            view.isFocusableInTouchMode = true

            view.setOnFocusChangeListener { v, hasFocus ->
                movieTitle.isSelected = hasFocus


                if (hasFocus) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        selectedPosition = position
                        val movie = movieList[position]
                        (v.context as? Main)?.setFondoDesdeUrl(movie.imageUrl)
                    }
                }

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

                    // 🔄 Fondo dinámico
                    if (!movie.imageUrl.isNullOrEmpty()) {
                        (view.context as? Main)?.setFondoDesdeUrl(movie.imageUrl)
                    } else {
                        (view.context as? Main)?.restaurarFondoAnimado()
                    }

                    // Ejecutar callback
                    onMovieClick(movie)

                    // Solo actualizamos posición seleccionada (sin perder foco)
                    selectedPosition = position
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
            isFocusable = false // ❗ importante
            isFocusableInTouchMode = false // ❗ importante
            setHorizontallyScrolling(true)
            marqueeRepeatLimit = -1

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
        movieList.addAll(newMovies)
        notifyDataSetChanged()
    }




}