package com.creativem.fulltv.peliculasvalidas

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
import com.creativem.fulltv.principal.Modelo
import java.util.concurrent.TimeUnit

class AlquileresAdapter(
    private val items: MutableList<Modelo.AlquilerItem>,
    private val onListEmpty: () -> Unit,
    private val onItemExpired: (Modelo.AlquilerItem) -> Unit,
    private val onItemClick: (Modelo.AlquilerItem) -> Unit
) : RecyclerView.Adapter<AlquileresAdapter.ViewHolder>() {

    private val timers = mutableMapOf<String, CountDownTimer>()

    private val glideOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .override(200, 300)
        .centerCrop()
        .placeholder(R.drawable.pelifondo)
        .error(R.drawable.pelifondo)
        .dontAnimate()
        .dontTransform()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].movie.id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pelicula_alquilada, parent, false)

        val context = parent.context
        val orientation = context.resources.configuration.orientation

        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            val density = context.resources.displayMetrics.density
            val widthInPx = (150 * density).toInt()
            val heightInPx = (255 * density).toInt()

            val params = view.layoutParams ?: ViewGroup.LayoutParams(widthInPx, heightInPx)
            params.width = widthInPx
            params.height = heightInPx
            view.layoutParams = params
        }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val movie = item.movie

        holder.txtTitle.text = movie.title
        holder.txtTitle.isSelected = false

        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .apply(glideOptions)
            .priority(Priority.IMMEDIATE)
            .thumbnail(0.2f)
            .into(holder.imgMovie)

        configurarContador(holder, item)

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            val card = view as? androidx.cardview.widget.CardView
            val currentPos = holder.bindingAdapterPosition

            if (hasFocus && currentPos != RecyclerView.NO_POSITION) {
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

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    private fun configurarContador(holder: ViewHolder, item: Modelo.AlquilerItem) {
        val movieKey = item.movie.title
        timers[movieKey]?.cancel()

        val durationMillis = TimeUnit.MINUTES.toMillis(item.countdownMinutes.toLong())
        val timeElapsed = System.currentTimeMillis() - item.createdAt
        val remainingTimeMillis = durationMillis - timeElapsed

        if (remainingTimeMillis <= 0) {
            eliminarItemEnTiempoReal(holder)
        } else {
            val timer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    if (holder.txtTitle.text == item.movie.title) {
                        val h = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                        val m = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                        val s = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                        // 🟢 Actualizamos directamente la etiqueta superior con el texto y formato solicitado
                        holder.txtBadge.text = String.format("DISPONIBLE POR: %02d:%02d:%02d", h, m, s)

                        val colorActivo = Color.parseColor("#004D40")
                        holder.infoArea.setBackgroundColor(colorActivo)
                        holder.txtBadge.setBackgroundColor(Color.parseColor("#E6004D40")) // Fondo verde oscuro traslúcido
                        holder.txtBadge.setTextColor(Color.parseColor("#00E676")) // Texto verde brillante
                        holder.txtBadge.visibility = View.VISIBLE
                    } else {
                        this.cancel()
                    }
                }

                override fun onFinish() {
                    eliminarItemEnTiempoReal(holder)
                }
            }.start()
            timers[movieKey] = timer
        }
    }

    private fun eliminarItemEnTiempoReal(holder: ViewHolder) {
        val currentPos = holder.bindingAdapterPosition
        if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size) {
            val itemExpirado = items[currentPos]
            val tituloExpirado = itemExpirado.movie.title

            timers[tituloExpirado]?.cancel()
            timers.remove(tituloExpirado)

            items.removeAt(currentPos)
            notifyItemRemoved(currentPos)
            notifyItemRangeChanged(currentPos, items.size)

            onItemExpired(itemExpirado)

            if (items.isEmpty()) {
                onListEmpty()
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        val movieKey = holder.txtTitle.text.toString()
        timers[movieKey]?.cancel()
        timers.remove(movieKey)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMovie: ImageView = view.findViewById(R.id.imgMovie)
        val txtTitle: TextView = view.findViewById(R.id.txtMovieTitle)
        val txtBadge: TextView = view.findViewById(R.id.txtBadge)
        val infoArea: View = view.findViewById(R.id.infoArea)
    }
}