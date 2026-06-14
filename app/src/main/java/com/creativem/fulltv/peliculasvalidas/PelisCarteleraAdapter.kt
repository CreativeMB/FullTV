package com.creativem.fulltv.peliculasvalidas

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Modelo

class PelisCarteleraAdapter(
    private val items: MutableList<Modelo> = mutableListOf(),
    private val onClick: (Modelo) -> Unit
) : RecyclerView.Adapter<PelisCarteleraAdapter.ViewHolder>() {

    fun updateMovies(newModelos: List<Modelo>) {
        this.items.clear()
        this.items.addAll(newModelos)
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.itemPoster)
        val title: TextView = view.findViewById(R.id.itemTitle)
        val badge: TextView = view.findViewById(R.id.txtBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pelis_cartelera, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movie = items[position]

        // 1. LÓGICA DE ETIQUETAS
        if (movie.isValid) {
            holder.badge.text = "ACTIVAR 💳"
            holder.badge.setBackgroundColor(Color.parseColor("#006064"))
        } else {
            holder.badge.text = "ALQUILA 💳"
            holder.badge.setBackgroundColor(Color.parseColor("#880E4F"))
        }
        holder.badge.visibility = View.VISIBLE
        holder.title.text = movie.title

        // 2. CARGA DE IMAGEN OPTIMIZADA (Sin parpadeo)
        Glide.with(holder.itemView.context)
            .load(movie.imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .thumbnail(0.1f)
            .placeholder(holder.poster.drawable)
            .error(R.drawable.pelifondo)
            .into(holder.poster)

        // --- EFECTO DE FOCO VISUAL MEJORADO ---
        holder.itemView.isFocusable = true

        // Calculamos 5dp de grosor para que se vea claramente en cualquier TV (1080p o 4K)
        val strokeWidthPx = (5 * holder.itemView.context.resources.displayMetrics.density).toInt()
        val colorDorado = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.cine_dorado)
        val focusedBorder = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setStroke(strokeWidthPx, colorDorado) // Borde calculado dinámicamente
            setColor(Color.TRANSPARENT) // Totalmente transparente por dentro para que se vea el colorhover2
            cornerRadius = 8f // Curva de las esquinas
        }

        // 3. GESTIÓN DE FOCO, ZOOM Y CENTRADO
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            val card = view as? androidx.cardview.widget.CardView
            val currentPos = holder.bindingAdapterPosition

            if (hasFocus && currentPos != RecyclerView.NO_POSITION) {

                // --- A. CENTRADO HORIZONTAL ---
                val parentRV = view.parent as? RecyclerView
                parentRV?.let { rv ->
                    val scroller = object : LinearSmoothScroller(rv.context) {
                        override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                            return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                        }
                        override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                            return 100f / displayMetrics.densityDpi
                        }
                    }
                    scroller.targetPosition = currentPos
                    rv.layoutManager?.startSmoothScroll(scroller)

                    rv.requestChildFocus(view, view)
                }

                // --- B. ZOOM Y CAPAS ---
                view.bringToFront()
                (view.parent as? View)?.requestLayout()
                (view.parent as? View)?.invalidate()

                // Aplicar el borde brillante
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    view.foreground = focusedBorder
                    view.invalidate() // Forzar a la TV a redibujar el ítem
                }
                view.alpha = 1.0f

                view.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .translationZ(35f) // Elevación para resaltar
                    .setDuration(250)
                    .start()

                // --- C. ESTILO VISUAL ---
                card?.setCardBackgroundColor(ContextCompat.getColor(view.context, R.color.colorhover2))
                card?.cardElevation = 20f
                holder.title.setTextColor(colorDorado) // Hace juego con el borde
                holder.title.isSelected = true

            } else if (!hasFocus) {
                // --- RESET AL PERDER FOCO ---
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(200)
                    .start()

                // ¡AQUÍ FALTABA ESTO EN TU CÓDIGO! (Quitar el borde al salir)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    view.foreground = null
                    view.invalidate()
                }

                card?.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
                card?.cardElevation = 6f
                holder.title.setTextColor(Color.WHITE)
                holder.title.isSelected = false
            }
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onClick(items[pos])
            }
        }
    }

    override fun getItemCount(): Int = items.size
}