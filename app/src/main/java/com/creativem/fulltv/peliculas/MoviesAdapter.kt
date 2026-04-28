package com.creativem.fulltv.peliculas

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Movie
import java.util.concurrent.TimeUnit

class MoviesAdapter(
    private val movieList: List<Movie>,
    private val onItemClick: (Movie) -> Unit,
    private val onFocusChange: (Movie) -> Unit
) : RecyclerView.Adapter<MoviesAdapter.MovieViewHolder>() {

    private val timers = mutableMapOf<Int, CountDownTimer>()

    // 1. Configuramos opciones globales para ahorrar memoria
    private val glideOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565) // Consume 50% menos RAM
        .diskCacheStrategy(DiskCacheStrategy.ALL) // Guarda imagen original y redimensionada
        .override(240, 360) // Tamaño fijo de la tarjeta (ajusta según tu diseño)
        .centerCrop()
        .placeholder(R.drawable.pelifondo)
        .error(R.drawable.pelifondo)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val movie = movieList[position]

        holder.txtTitle.text = movie.title
        holder.txtTitle.isSelected = true

        // 2. Carga optimizada de imagen
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .apply(glideOptions)
            .thumbnail(0.2f) // Carga una versión al 20% de calidad primero
            .transition(DrawableTransitionOptions.withCrossFade()) // Aparece con un fundido suave
            .into(holder.imgMovie)

        // --- Lógica de Tiempos y Estados ---
        val createdAtMillis = movie.createdAt
        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - createdAtMillis

        timers[position]?.cancel()

        val esValida = movie.isValid

        if (movie.countdownMinutes <= 0 || timeElapsed >= countdownDurationMillis) {
            if (esValida) {
                holder.txtStatus.text = "Abierta al público"
                holder.txtBadge.text = "Gratis ✅"
                holder.infoArea.setBackgroundColor(Color.parseColor("#006064"))
                holder.txtBadge.setBackgroundColor(Color.parseColor("#006064"))
            } else {
                holder.txtStatus.text = "CasTV $${movie.castv}"
                holder.txtBadge.text = "Alquilar 💳"
                holder.infoArea.setBackgroundColor(Color.parseColor("#880E4F"))
                holder.txtBadge.setBackgroundColor(Color.parseColor("#880E4F"))
            }
            holder.txtBadge.visibility = View.VISIBLE
        } else {
            val remainingTimeMillis = countdownDurationMillis - timeElapsed
            val timer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val h = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                    val m = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                    val s = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    holder.txtStatus.text = "%02d:%02d:%02d".format(h, m, s)

                    holder.infoArea.setBackgroundColor(Color.parseColor("#001f3f"))
                    holder.txtBadge.text = "Alquilada 🎬"
                    holder.txtBadge.setBackgroundColor(Color.parseColor("#001f3f"))
                    holder.txtBadge.visibility = View.VISIBLE
                }

                override fun onFinish() {
                    notifyItemChanged(position)
                }
            }.start()
            timers[position] = timer
        }

        holder.itemView.setOnClickListener { onItemClick(movie) }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                onFocusChange(movie)
                view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }
    }

    override fun getItemCount(): Int = movieList.size

    // 3. Muy importante: Liberar recursos de Glide cuando la tarjeta no se ve
    override fun onViewRecycled(holder: MovieViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context).clear(holder.imgMovie)
        timers[holder.bindingAdapterPosition]?.cancel()
        timers.remove(holder.bindingAdapterPosition)
    }

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMovie: ImageView = view.findViewById(R.id.imgMovie)
        val txtTitle: TextView = view.findViewById(R.id.txtMovieTitle)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtBadge: TextView = view.findViewById(R.id.txtBadge)
        val infoArea: View = view.findViewById(R.id.infoArea)
    }
}