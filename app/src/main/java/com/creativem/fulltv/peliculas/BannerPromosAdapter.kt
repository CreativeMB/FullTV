package com.creativem.fulltv.peliculas

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.principal.Modelo

class BannerPromosAdapter(
    private val list: List<Modelo>,
    private val onMovieFocused: (Modelo) -> Unit,
    private val onMovieClicked: (Modelo) -> Unit
) : RecyclerView.Adapter<BannerPromosAdapter.ViewHolder>() {

    private var selectedPosition = 0

    class ViewHolder(val container: FrameLayout, val lineIndicator: View) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val density = context.resources.displayMetrics.density

        val lineWidth = (24 * density).toInt()
        val lineHeight = (6 * density).toInt()

        val container = FrameLayout(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            clipChildren = false
            clipToPadding = false

            layoutParams = RecyclerView.LayoutParams(
                (36 * density).toInt(),
                (24 * density).toInt()
            )
        }

        val lineView = View(context).apply {
            isFocusable = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 3 * density
                setColor(Color.parseColor("#33FFFFFF"))
            }
            layoutParams = FrameLayout.LayoutParams(lineWidth, lineHeight).apply {
                gravity = Gravity.CENTER
            }
        }

        container.addView(lineView)
        return ViewHolder(container, lineView)
    }

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val movie = list[position]
        val root = holder.container
        val line = holder.lineIndicator
        val shapeDrawable = line.background as? GradientDrawable

        line.animate().cancel()

        val hasFocus = root.hasFocus()
        val isSelected = position == selectedPosition

        // 🔥 CONTROL DE ESTADOS LÓGICOS PERFECTO
        when {
            hasFocus -> {
                // Si el control remoto está físicamente aquí arriba
                line.scaleX = 1.6f
                line.scaleY = 1.3f
                shapeDrawable?.setColor(Color.parseColor("#FFB300")) // Dorado de Foco Activo
            }
            isSelected -> {
                // Si es la peli actual pero el control remoto está abajo en las películas
                line.scaleX = 1.0f
                line.scaleY = 1.0f
                shapeDrawable?.setColor(Color.parseColor("#FFFFFF")) // Blanco Sólido (No compite con las pelis)
            }
            else -> {
                // Estado apagado estándar
                line.scaleX = 1.0f
                line.scaleY = 1.0f
                shapeDrawable?.setColor(Color.parseColor("#33FFFFFF")) // Translúcido
            }
        }

        // ESCUCHADOR DE FOCO DINÁMICO
        root.setOnFocusChangeListener { _, focused ->
            val currentDrawable = line.background as? GradientDrawable
            if (focused) {
                val oldPosition = selectedPosition
                selectedPosition = position

                // El elemento seleccionado se agranda y se vuelve dorado al instante
                line.animate().scaleX(1.6f).scaleY(1.3f).setDuration(200).start()
                currentDrawable?.setColor(Color.parseColor("#FFB300"))

                // Al viejo le quitamos el estado de forma inmediata
                if (oldPosition != position) {
                    notifyItemChanged(oldPosition)
                }

                onMovieFocused(movie)
            } else {
                // 🔥 LA SOLUCIÓN CUANDO EL FOCO SE VA AL OTRO RECYCLERVIEW:
                // Si este elemento pierde el foco pero sigue siendo el seleccionado actual,
                // lo encogemos suavemente y lo pasamos a Blanco para liberar el diseño visual.
                if (position == selectedPosition) {
                    line.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    currentDrawable?.setColor(Color.parseColor("#FFFFFF"))
                } else {
                    line.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    currentDrawable?.setColor(Color.parseColor("#33FFFFFF"))
                }
            }
        }

        root.setOnClickListener { onMovieClicked(movie) }
    }

    override fun getItemCount(): Int = list.size

    fun updateSelectedPosition(newPosition: Int, recyclerView: RecyclerView? = null) {
        if (newPosition in list.indices && newPosition != selectedPosition) {
            val oldPosition = selectedPosition
            selectedPosition = newPosition

            notifyItemChanged(oldPosition)
            notifyItemChanged(newPosition)

            if (recyclerView?.hasFocus() == false) {
                recyclerView.smoothScrollToPosition(newPosition)
            }
        }
    }
}