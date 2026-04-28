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

    // Mapa para gestionar los timers por título (llave única)
    private val timers = mutableMapOf<String, CountDownTimer>()

    private val glideOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .override(200, 300)
        .centerCrop()
        .dontAnimate()
        .error(R.drawable.pelifondo)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]

        // 1. Reset visual para evitar basura de reciclaje
        holder.txtStatus.text = ""
        holder.txtTitle.text = movie.title
        holder.txtTitle.isSelected = false

        // 2. Carga de imagen optimizada
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .apply(glideOptions)
            .placeholder(holder.imgMovie.drawable)
            .into(holder.imgMovie)

        // 3. Iniciar lógica de tiempos
        configurarTiempos(holder, movie)

        // 4. Zoom 1.2x y gestión de Foco (Eje Z corregido)
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                onFocusChange(movie)

                // Traer al frente para que el zoom no quede detrás de otros items
                view.bringToFront()
                view.parent.requestLayout()
                (view.parent as View).invalidate()

                view.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .translationZ(30f) // Elevación física
                    .setDuration(200)
                    .start()

                holder.txtTitle.isSelected = true
                holder.txtTitle.setTextColor(Color.YELLOW)
            } else {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(200)
                    .start()

                holder.txtTitle.isSelected = false
                holder.txtTitle.setTextColor(Color.WHITE)
            }
        }

        holder.itemView.setOnClickListener { onItemClick(movie) }
    }

    override fun getItemCount(): Int = movieList.size

    private fun configurarTiempos(holder: MovieViewHolder, movie: Movie) {
        val movieKey = movie.title // Usamos el título como ID único del timer

        // Cancelar timer anterior de este item si existe
        timers[movieKey]?.cancel()

        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val timeElapsed = System.currentTimeMillis() - movie.createdAt
        val remainingTimeMillis = countdownDurationMillis - timeElapsed

        val colorVerde = Color.parseColor("#006064")
        val colorRojo = Color.parseColor("#880E4F")
        val colorAzul = Color.parseColor("#001f3f")

        // Caso 1: Tiempo agotado o película ya válida permanentemente
        if (remainingTimeMillis <= 0) {
            holder.txtBadge.visibility = View.VISIBLE
            if (movie.isValid) {
                holder.txtStatus.text = "Abierta al público"
                holder.txtBadge.text = "Gratis ✅"
                holder.infoArea.setBackgroundColor(colorVerde)
                holder.txtBadge.setBackgroundColor(colorVerde)
            } else {
                holder.txtStatus.text = "CasTV $${movie.castv}"
                holder.txtBadge.text = "Alquilar 💳"
                holder.infoArea.setBackgroundColor(colorRojo)
                holder.txtBadge.setBackgroundColor(colorRojo)
            }
        }
        // Caso 2: Contador activo (Compartida)
        else {
            val timer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    // Validar que el holder aún muestra esta película (evita errores de scroll)
                    if (holder.txtTitle.text == movie.title) {
                        val h = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                        val m = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                        val s = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                        holder.txtStatus.text = String.format("%02d:%02d:%02d", h, m, s)
                        holder.txtBadge.text = "Alquilada 🎬"

                        holder.infoArea.setBackgroundColor(colorAzul)
                        holder.txtBadge.setBackgroundColor(colorAzul)
                        holder.txtBadge.visibility = View.VISIBLE
                    } else {
                        this.cancel() // Matar timer si el holder se recicló para otra peli
                    }
                }

                override fun onFinish() {
                    if (holder.txtTitle.text == movie.title) {
                        notifyItemChanged(holder.bindingAdapterPosition)
                    }
                }
            }.start()

            timers[movieKey] = timer
        }
    }

    override fun onViewRecycled(holder: MovieViewHolder) {
        super.onViewRecycled(holder)
        // Liberar Glide pero NO cancelamos el timer aquí para que siga en segundo plano
        Glide.with(holder.itemView.context).clear(holder.imgMovie)
    }

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMovie: ImageView = view.findViewById(R.id.imgMovie)
        val txtTitle: TextView = view.findViewById(R.id.txtMovieTitle)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtBadge: TextView = view.findViewById(R.id.txtBadge)
        val infoArea: View = view.findViewById(R.id.infoArea)
    }
}