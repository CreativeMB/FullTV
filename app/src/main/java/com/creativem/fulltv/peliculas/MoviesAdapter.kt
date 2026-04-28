package com.creativem.fulltv.peliculas

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.CountDownTimer
import android.text.TextUtils
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

    // CONFIGURACIÓN DE CARGA MAESTRA
    private val glideOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565) // 50% menos de RAM que el formato estándar
        .diskCacheStrategy(DiskCacheStrategy.ALL) // GUARDA TODO: original y redimensionada (Evita volver a descargar)
        .override(180, 270) // Forzamos un tamaño pequeño. No necesitamos 4K para una miniatura.
        .centerCrop()
        .dontAnimate() // En listas largas, las animaciones de carga ralentizan el scroll
        .error(R.drawable.pelifondo)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]

        holder.txtTitle.text = movie.title
        holder.txtTitle.isSelected = false // Solo se activa en el foco

        // CARGA SECUENCIAL E INTELIGENTE
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .apply(glideOptions)
            .thumbnail(0.1f) // Carga una versión borrosa súper rápido mientras llega la real
            .placeholder(holder.imgMovie.drawable) // Mantiene lo que ya está para evitar parpadeos
            .into(holder.imgMovie)

        // Lógica de tiempos (mantenla igual o muévela a una función externa)
        configurarTiempos(holder, movie, position)

        // FOCO Y ZOOM 1.2x
        // Dentro de onBindViewHolder, modifica el bloque del Focus:
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                onFocusChange(movie)

                // 1. TRAER AL FRENTE (Esto evita que salga "debajo")
                view.bringToFront()
                view.parent.requestLayout()
                (view.parent as View).invalidate()

                // 2. ZOOM 1.2x + Elevación para sombra
                view.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .translationZ(20f) // Eleva el ítem físicamente en el eje Z
                    .setDuration(200)
                    .start()

                holder.txtTitle.isSelected = true
                holder.txtTitle.setTextColor(Color.YELLOW)
            } else {
                // 1. VOLVER AL ESTADO NORMAL
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

    // LIBERACIÓN DE MEMORIA CRÍTICA
    override fun onViewRecycled(holder: MovieViewHolder) {
        super.onViewRecycled(holder)
        // Esto le dice a Glide: "Este ítem ya no se ve, libera esa RAM para otro"
        Glide.with(holder.itemView.context).clear(holder.imgMovie)
        timers[holder.bindingAdapterPosition]?.cancel()
        timers.remove(holder.bindingAdapterPosition)
    }

    override fun getItemCount(): Int = movieList.size

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMovie: ImageView = view.findViewById(R.id.imgMovie)
        val txtTitle: TextView = view.findViewById(R.id.txtMovieTitle)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtBadge: TextView = view.findViewById(R.id.txtBadge)
        val infoArea: View = view.findViewById(R.id.infoArea)
    }
    // Esta función va dentro de la clase MoviesAdapter, pero fuera de onBindViewHolder
    private fun configurarTiempos(holder: MovieViewHolder, movie: Movie, position: Int) {
        val createdAtMillis = movie.createdAt
        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - createdAtMillis

        // Cancelamos cualquier timer viejo que esté usando esta tarjeta reciclada
        timers[position]?.cancel()

        // Si el tiempo ya se agotó
        if (movie.countdownMinutes <= 0 || timeElapsed >= countdownDurationMillis) {
            if (movie.isValid) {
                holder.txtStatus.text = "Abierta al público"
                holder.txtBadge.text = "Gratis ✅"
                holder.infoArea.setBackgroundColor(Color.parseColor("#006064"))
            } else {
                holder.txtStatus.text = "CasTV $${movie.castv}"
                holder.txtBadge.text = "Alquilar 💳"
                holder.infoArea.setBackgroundColor(Color.parseColor("#880E4F"))
            }
            holder.txtBadge.visibility = View.VISIBLE
        }
        // Si aún hay tiempo de alquiler, arranca el contador
        else {
            val remainingTimeMillis = countdownDurationMillis - timeElapsed
            val timer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val h = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                    val m = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                    val s = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                    holder.txtStatus.text = "%02d:%02d:%02d".format(h, m, s)
                    holder.infoArea.setBackgroundColor(Color.parseColor("#001f3f"))
                    holder.txtBadge.text = "Alquilada 🎬"
                }

                override fun onFinish() {
                    // Cuando termina el tiempo, refrescamos el ítem para que cambie a "Alquilar"
                    notifyItemChanged(position)
                }
            }.start()

            // Guardamos el timer para poder cancelarlo si el usuario hace scroll rápido
            timers[position] = timer
        }
    }
}