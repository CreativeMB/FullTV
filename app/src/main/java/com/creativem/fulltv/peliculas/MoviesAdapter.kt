package com.creativem.fulltv.peliculas

import android.graphics.Color
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Movie
import java.util.concurrent.TimeUnit

class MoviesAdapter(
    private var movieList: MutableList<Movie>,
    private val onItemClick: (Movie) -> Unit,
    private val onFocusChange: (Movie) -> Unit
) : RecyclerView.Adapter<MoviesAdapter.MovieViewHolder>() {

    private val timers = mutableMapOf<String, CountDownTimer>()

    // Configuración de Glide optimizada para evitar saltos y ahorrar RAM en TV
    private val glideOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .override(200, 300)
        .centerCrop()
        .placeholder(R.drawable.pelifondo) // Placeholder estático fijo
        .error(R.drawable.pelifondo)
        .dontAnimate() // Las animaciones causan parpadeos en el reciclaje
        .dontTransform()

    init {
        // Crucial: indica que cada ítem tiene un ID único basado en su origen (Firebase ID)
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        // Retorna el hash del ID de Firebase para que el RecyclerView reconozca el ítem siempre
        return movieList[position].id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]

        // 1. Reset visual básico (sin limpiar imágenes para evitar parpadeo blanco)
        holder.txtStatus.text = ""
        holder.txtTitle.text = movie.title
        holder.txtTitle.isSelected = false

        // 2. Carga de imagen optimizada
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .apply(glideOptions)
            .priority(Priority.IMMEDIATE) // 👈 Carga inmediata
            .thumbnail(0.2f) // Muestra una versión rápida mientras carga la original
            .into(holder.imgMovie)

        // 3. Timers
        configurarTiempos(holder, movie)

        // 4. Gestión de foco y zoom
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            val card = view as? androidx.cardview.widget.CardView
            val currentPos = holder.bindingAdapterPosition

            if (hasFocus && currentPos != RecyclerView.NO_POSITION) {
                onFocusChange(movie)

                val parentView = view.parent
                if (parentView is RecyclerView) {
                    val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(view.context) {
                        override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                            return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                        }
                        override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                            return 100f / displayMetrics.densityDpi
                        }
                    }
                    smoothScroller.targetPosition = currentPos
                    parentView.layoutManager?.startSmoothScroll(smoothScroller)
                    parentView.requestChildFocus(view, view)
                }

                view.bringToFront()
                parentView?.requestLayout()
                (parentView as? View)?.invalidate()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    view.foreground = null
                }
                view.alpha = 1.0f

                view.animate().scaleX(1.2f).scaleY(1.2f).translationZ(35f).setDuration(250).start()

                card?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(view.context, R.color.colorhover2))
                card?.cardElevation = 20f
                holder.txtTitle.isSelected = true
                holder.txtTitle.setTextColor(Color.YELLOW)

            } else if (!hasFocus) {
                view.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(200).start()
                card?.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
                card?.cardElevation = 6f
                holder.txtTitle.isSelected = false
                holder.txtTitle.setTextColor(Color.WHITE)
            }
        }

        holder.itemView.setOnClickListener { onItemClick(movie) }
    }

    override fun getItemCount(): Int = movieList.size

    private fun configurarTiempos(holder: MovieViewHolder, movie: Movie) {
        val movieKey = movie.title
        timers[movieKey]?.cancel()

        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val timeElapsed = System.currentTimeMillis() - movie.createdAt
        val remainingTimeMillis = countdownDurationMillis - timeElapsed

        if (remainingTimeMillis <= 0) {
            actualizarInterfazFinal(holder, movie)
        } else {
            val timer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    if (holder.txtTitle.text == movie.title) {
                        val h = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                        val m = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                        val s = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                        holder.txtStatus.text = String.format("%02d:%02d:%02d", h, m, s)
                        holder.txtBadge.text = "Alquilada 🎬"
                        holder.infoArea.setBackgroundColor(Color.parseColor("#001f3f"))
                        holder.txtBadge.setBackgroundColor(Color.parseColor("#001f3f"))
                        holder.txtBadge.visibility = View.VISIBLE
                    } else {
                        this.cancel()
                    }
                }

                override fun onFinish() {
                    if (holder.txtTitle.text == movie.title) {
                        actualizarInterfazFinal(holder, movie)
                    }
                }
            }.start()
            timers[movieKey] = timer
        }
    }

    private fun actualizarInterfazFinal(holder: MovieViewHolder, movie: Movie) {
        holder.txtBadge.visibility = View.VISIBLE
        if (movie.isValid) {
            holder.txtStatus.text = "Abierta al público"
            holder.txtBadge.text = "Gratis ✅"
            val color = Color.parseColor("#006064")
            holder.infoArea.setBackgroundColor(color)
            holder.txtBadge.setBackgroundColor(color)
        } else {
            holder.txtStatus.text = "CasTV $${movie.castv}"
            holder.txtBadge.text = "Alquilar 💳"
            val color = Color.parseColor("#880E4F")
            holder.infoArea.setBackgroundColor(color)
            holder.txtBadge.setBackgroundColor(color)
        }
    }

    override fun onViewRecycled(holder: MovieViewHolder) {
        super.onViewRecycled(holder)
        // No limpiamos Glide aquí para evitar parpadeos en scroll rápido en TV
        val movieKey = holder.txtTitle.text.toString()
        timers[movieKey]?.cancel()
        timers.remove(movieKey)
    }

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMovie: ImageView = view.findViewById(R.id.imgMovie)
        val txtTitle: TextView = view.findViewById(R.id.txtMovieTitle)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtBadge: TextView = view.findViewById(R.id.txtBadge)
        val infoArea: View = view.findViewById(R.id.infoArea)
    }
}