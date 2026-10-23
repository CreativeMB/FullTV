package com.creativem.fulltv.menu

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R

class MenuPrincipalVerticalAdapter(
    private val items: List<MenuPrincipalItem>,
    private var isExpanded: Boolean = false,
    private val onItemClick: (MenuPrincipalItem) -> Unit
) : RecyclerView.Adapter<MenuPrincipalVerticalAdapter.ViewHolder>() {

    var lastFocusedPosition: Int = -1

    private val colorDorado = Color.parseColor("#C5A059")
    private val colorBlanco = Color.WHITE
    private val interpolator = DecelerateInterpolator()

    // 🟢 Actualiza el estado visual sin rebotar o perder el foco del D-Pad
    fun setExpanded(expanded: Boolean, recyclerView: RecyclerView) {
        if (isExpanded != expanded) {
            isExpanded = expanded

            val childCount = recyclerView.childCount
            for (i in 0 until childCount) {
                val child = recyclerView.getChildAt(i)
                val holder = recyclerView.getChildViewHolder(child) as? ViewHolder ?: continue
                val pos = holder.bindingAdapterPosition
                val item = items.getOrNull(pos) ?: continue
                val esCasTV = item.name.contains("CasTV")

                // La visibilidad del texto depende de la expansión
                holder.textView.visibility = if (expanded) View.VISIBLE else View.GONE

                // Sincronizar estado visual
                val hasActiveFocus = holder.container.hasFocus() || (pos == lastFocusedPosition && expanded)
                updateItemVisuals(holder, isFocused = hasActiveFocus, esCasTV = esCasTV, animate = false)
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view as LinearLayout
        val contentWrapper: LinearLayout = container.getChildAt(0) as LinearLayout
        val iconView: ImageView = contentWrapper.getChildAt(0) as ImageView
        val textView: TextView = contentWrapper.getChildAt(1) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        val parentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            )
            setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4))
            isFocusable = true
            isFocusableInTouchMode = true
            clipChildren = false
            clipToPadding = false
        }

        val contentWrapper = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(48)
            )
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            isDuplicateParentStateEnabled = true
            clipChildren = false
            clipToPadding = false
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val text = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(12)
                marginStart = dpToPx(12)
            }
            setTextColor(colorBlanco)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        contentWrapper.addView(icon)
        contentWrapper.addView(text)
        parentLayout.addView(contentWrapper)

        return ViewHolder(parentLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.iconView.setImageResource(item.iconResId)
        holder.textView.text = item.name

        holder.textView.visibility = if (isExpanded) View.VISIBLE else View.GONE

        val esCasTV = item.name.contains("CasTV")
        if (esCasTV) {
            holder.container.isFocusable = false
            holder.container.isFocusableInTouchMode = false
            holder.container.isClickable = false
        } else {
            holder.container.isFocusable = true
            holder.container.isFocusableInTouchMode = true
            holder.container.isClickable = true
        }

        val isCurrentlyFocused = holder.container.hasFocus() || (position == lastFocusedPosition && isExpanded)
        updateItemVisuals(holder, isFocused = isCurrentlyFocused, esCasTV = esCasTV, animate = false)

        holder.container.setOnFocusChangeListener { v, hasFocus ->
            if (esCasTV) return@setOnFocusChangeListener

            if (hasFocus) {
                lastFocusedPosition = holder.bindingAdapterPosition
            }

            updateItemVisuals(holder, isFocused = hasFocus, esCasTV = false, animate = true)
        }

        holder.container.setOnClickListener {
            if (!esCasTV) {
                onItemClick(item)
            }
        }
    }

    // 🟢 Cancela animaciones al reciclar para evitar parpadeos en navegación rápida
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.contentWrapper.animate().cancel()
    }

    // 🟢 Método centralizado para aplicar estilos, animaciones y elevación
    private fun updateItemVisuals(
        holder: ViewHolder,
        isFocused: Boolean,
        esCasTV: Boolean,
        animate: Boolean
    ) {
        holder.textView.isSelected = isFocused

        if (esCasTV) {
            holder.textView.setTextColor(colorDorado)
            holder.contentWrapper.background = null
            holder.container.translationZ = 0f
            applyScale(holder.contentWrapper, 1.0f, animate)
            return
        }

        if (isFocused) {
            holder.textView.setTextColor(colorDorado)
            holder.contentWrapper.background = ContextCompat.getDrawable(
                holder.itemView.context,
                R.drawable.card_focused_background
            )
            // Eleva el ítem para que no quede detrás de los otros al agrandarse
            holder.container.translationZ = 8f
            applyScale(holder.contentWrapper, 1.1f, animate)
        } else {
            holder.textView.setTextColor(colorBlanco)
            holder.contentWrapper.background = null
            holder.container.translationZ = 0f
            applyScale(holder.contentWrapper, 1.0f, animate)
        }
    }

    private fun applyScale(view: View, targetScale: Float, animate: Boolean) {
        if (animate) {
            view.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(150)
                .setInterpolator(interpolator)
                .start()
        } else {
            view.animate().cancel()
            view.scaleX = targetScale
            view.scaleY = targetScale
        }
    }

    override fun getItemCount(): Int = items.size
}