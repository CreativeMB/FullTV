package com.creativem.fulltv.api

import android.graphics.Color
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Movie

class PeliculasApiAdapter(
    private val movieList: MutableList<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<PeliculasApiAdapter.SmallMovieViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class SmallMovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val movieImage: ImageView = view.findViewById(R.id.movie_image_small)
        val movieTitle: TextView = view.findViewById(R.id.movie_title_small)
        // El itemView ya es el CardView en este caso
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmallMovieViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_api_peluculas, parent, false)
        return SmallMovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmallMovieViewHolder, position: Int) {
        val movie = movieList[position]

        // 1. CONFIGURACIÓN DEL TÍTULO Y AÑO
        val tituloLimpio = movie.title.replace(Regex("\\(\\d{4}-\\d{2}-\\d{2}\\)"), "").trim()
        val yearMatch = Regex("(\\d{4})").find(movie.title)
        val año = yearMatch?.value ?: ""

        holder.movieTitle.apply {
            text = if (año.isNotEmpty()) "$tituloLimpio ($año)" else tituloLimpio
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSingleLine = true
            isSelected = false // Se activa en el focus
        }

        // 2. CARGA DE IMAGEN (fitXY)
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .dontAnimate()
            .placeholder(R.drawable.pelifondo)
            .error(R.drawable.icono)
            .into(holder.movieImage)

        // 3. GESTIÓN DE FOCO, ZOOM Y CENTRADO HORIZONTAL
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            val card = view as? androidx.cardview.widget.CardView
            val currentPos = holder.bindingAdapterPosition

            if (hasFocus && currentPos != RecyclerView.NO_POSITION) {

                // --- A. CENTRADO HORIZONTAL ---
                val parentRV = view.parent as? RecyclerView
                parentRV?.let { rv ->
                    val scroller = object : LinearSmoothScroller(rv.context) {
                        override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                            // Centrar: (Centro del RV) - (Centro del Item)
                            return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                        }
                        override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                            return 100f / displayMetrics.densityDpi
                        }
                    }
                    scroller.targetPosition = currentPos
                    rv.layoutManager?.startSmoothScroll(scroller)

                    // Impedir que el foco salte fuera de la lista
                    rv.requestChildFocus(view, view)
                }

                // --- B. ZOOM Y CAPAS ---
                view.bringToFront()
                (view.parent as? View)?.requestLayout()
                (view.parent as? View)?.invalidate()

                // Brillo máximo (Sin atenuado)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    view.foreground = null
                }
                view.alpha = 1.0f

                view.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .translationZ(30f)
                    .setDuration(200)
                    .start()

                // --- C. COLORES ---
                card?.setCardBackgroundColor(Color.parseColor("#FFD700")) // Oro
                card?.cardElevation = 20f
                holder.movieTitle.setTextColor(Color.YELLOW)
                holder.movieTitle.isSelected = true

            } else if (!hasFocus) {
                // --- RESET AL PERDER FOCO ---
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(200)
                    .start()

                card?.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
                card?.cardElevation = 4f
                holder.movieTitle.setTextColor(Color.WHITE)
                holder.movieTitle.isSelected = false
            }
        }

        // 4. CLICK PARA SELECCIONAR
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                selectedPosition = pos
                notifyDataSetChanged() // Refresca para el resaltado
                onMovieClick(movieList[pos])
            }
        }
    }

    override fun getItemCount(): Int = movieList.size

    fun updateMovies(newMovies: List<Movie>) {
        movieList.clear()
        movieList.addAll(newMovies)
        notifyDataSetChanged()
    }
}