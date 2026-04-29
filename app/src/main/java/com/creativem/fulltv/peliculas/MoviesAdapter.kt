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

    // ... (Mantén el resto de la clase igual hasta el onBindViewHolder)

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movieList[position]

        // 1. Reset visual
        holder.txtStatus.text = ""
        holder.txtTitle.text = movie.title
        holder.txtTitle.isSelected = false

        // 2. Carga de imagen ( Glide )
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .apply(glideOptions)
            .placeholder(holder.imgMovie.drawable)
            .into(holder.imgMovie)

        // 3. Timers
        configurarTiempos(holder, movie)

        // 4. GESTIÓN DE FOCO Y ZOOM (Versión Robusta)
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            // Usamos as? para evitar crashes si el root no fuera CardView
            val card = view as? androidx.cardview.widget.CardView
            val currentPos = holder.bindingAdapterPosition

            if (hasFocus && currentPos != RecyclerView.NO_POSITION) {
                onFocusChange(movie)

                // --- 1. CENTRAR ITEM (Con comprobación de seguridad) ---
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

                    // BLOQUEO DE FOCO
                    parentView.requestChildFocus(view, view)
                }

                // --- 2. GESTIÓN DE CAPAS SEGURA ---
                view.bringToFront()
                parentView?.requestLayout()
                (parentView as? View)?.invalidate()

                // --- 3. BRILLO Y ZOOM ---
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    view.foreground = null
                }
                view.alpha = 1.0f

                view.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .translationZ(35f)
                    .setDuration(250)
                    .start()

                // --- 4. COLORES ---
                card?.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(view.context, R.color.colorhover2))
                card?.cardElevation = 20f
                holder.txtTitle.isSelected = true
                holder.txtTitle.setTextColor(Color.YELLOW)

            } else if (!hasFocus) {
                // RESET AL PERDER FOCO
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(200)
                    .start()

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