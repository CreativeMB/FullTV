package com.creativem.fulltv

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
import com.creativem.fulltv.peliculas.Validacioneslista
import com.creativem.fulltv.principal.Movie

import java.util.concurrent.TimeUnit

class MoviesAdapter(
    private val movieList: List<Movie>,
    private val onItemClick: (Movie) -> Unit,
    private val onFocusChange: (Movie) -> Unit
) : RecyclerView.Adapter<MoviesAdapter.MovieViewHolder>() {

    // Mapa para guardar los timers activos y cancelarlos cuando la tarjeta se recicle
    private val timers = mutableMapOf<Int, CountDownTimer>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val movie = movieList[position]

        // --- Datos Básicos ---
        holder.txtTitle.text = movie.title
        holder.txtTitle.isSelected = true // Para que el marquee (texto rodante) funcione

        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .placeholder(R.drawable.pelifondo)
            .into(holder.imgMovie)

        // --- Lógica de Tiempos y Estados (Lo que tenía el CardPresenter) ---
        val createdAtMillis = movie.createdAt
        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - createdAtMillis

        // Cancelar timer anterior si existe para esta posición
        timers[position]?.cancel()

        // Simulación de Validacioneslista (Asegúrate que esta clase sea accesible)
        val esValida = Validacioneslista.yaCargado() &&
                Validacioneslista.obtenerPeliculasValidas().any { it.streamUrl == movie.streamUrl }

        if (movie.countdownMinutes <= 0 || timeElapsed >= countdownDurationMillis) {
            // PELÍCULA ABIERTA O PARA ALQUILAR
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
            // PELÍCULA EN CUENTA REGRESIVA
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
                    holder.txtStatus.text = "00:00:00"
                    holder.infoArea.setBackgroundColor(Color.parseColor("#3E2723"))
                }
            }.start()
            timers[position] = timer
        }

        // --- Eventos de Clic y Foco ---
        holder.itemView.setOnClickListener { onItemClick(movie) }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                onFocusChange(movie)
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }
        }
    }

    override fun getItemCount(): Int = movieList.size

    override fun onViewRecycled(holder: MovieViewHolder) {
        super.onViewRecycled(holder)
        // CRÍTICO: Detener el timer cuando la tarjeta sale de pantalla
        timers[holder.adapterPosition]?.cancel()
        timers.remove(holder.adapterPosition)
    }

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMovie: ImageView = view.findViewById(R.id.imgMovie)
        val txtTitle: TextView = view.findViewById(R.id.txtMovieTitle)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus) // Equivale a contentText
        val txtBadge: TextView = view.findViewById(R.id.txtBadge)   // Equivale a etiquetaValida
        val infoArea: View = view.findViewById(R.id.infoArea)       // El contenedor del texto
    }
}