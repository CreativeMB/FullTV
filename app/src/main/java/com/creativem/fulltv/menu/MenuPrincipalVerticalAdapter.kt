package com.creativem.fulltv.menu

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

    // 🟢 OPTIMIZACIÓN CLAVE: Actualiza el estado sin destruir las celdas para evitar saltos de foco
    fun setExpanded(expanded: Boolean, recyclerView: RecyclerView) {
        if (isExpanded != expanded) {
            isExpanded = expanded

            val childCount = recyclerView.childCount
            for (i in 0 until childCount) {
                val child = recyclerView.getChildAt(i)
                val holder = recyclerView.getChildViewHolder(child) as? ViewHolder
                if (holder != null) {
                    holder.textView.visibility = if (expanded) View.VISIBLE else View.GONE

                    val pos = holder.bindingAdapterPosition
                    val item = items.getOrNull(pos)
                    val isFocused = (pos == lastFocusedPosition) && expanded
                    val esCasTV = item?.name?.contains("CasTV") ?: false

                    if (isFocused && !esCasTV) {
                        holder.textView.setTextColor(colorDorado)
                        // 🟢 Sin filtros de color sobre el icono al expandir el menú
                        holder.contentWrapper.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.card_focused_background)
                        holder.contentWrapper.scaleX = 1.1f
                        holder.contentWrapper.scaleY = 1.1f
                    } else {
                        if (esCasTV) {
                            holder.textView.setTextColor(colorDorado)
                        } else {
                            holder.textView.setTextColor(colorBlanco)
                        }
                        // 🟢 Sin filtros de color sobre el icono al colapsar el menú
                        holder.contentWrapper.background = null
                        holder.contentWrapper.scaleX = 1.0f
                        holder.contentWrapper.scaleY = 1.0f
                    }
                }
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
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(48)
            )
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            isDuplicateParentStateEnabled = true
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

        // Si es el saldo de CasTV, desactivamos el foco físico de esta celda
        val esCasTV = item.name.contains("CasTV")
        if (esCasTV) {
            holder.container.isFocusable = false
            holder.container.isFocusableInTouchMode = false
            holder.container.isClickable = false
            holder.textView.setTextColor(colorDorado) // Siempre dorado y fijo
        } else {
            holder.container.isFocusable = true
            holder.container.isFocusableInTouchMode = true
            holder.container.isClickable = true
            holder.textView.setTextColor(colorBlanco)
        }

        val isFocused = (position == lastFocusedPosition) && isExpanded
        holder.textView.isSelected = isFocused

        if (isFocused && !esCasTV) {
            holder.textView.setTextColor(colorDorado)
            // 🟢 SE ELIMINARON LOS FILTROS DE COLOR (Las imágenes siempre mantienen sus colores nativos)
            holder.contentWrapper.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.card_focused_background)
            holder.contentWrapper.scaleX = 1.1f
            holder.contentWrapper.scaleY = 1.1f
        } else {
            if (!esCasTV) {
                holder.textView.setTextColor(colorBlanco)
            }
            holder.contentWrapper.background = null
            holder.contentWrapper.scaleX = 1.0f
            holder.contentWrapper.scaleY = 1.0f
        }

        holder.container.setOnFocusChangeListener { v, hasFocus ->
            if (esCasTV) return@setOnFocusChangeListener // CasTV nunca reacciona al foco

            holder.textView.isSelected = hasFocus

            if (hasFocus) {
                holder.contentWrapper.background = ContextCompat.getDrawable(v.context, R.drawable.card_focused_background)
                holder.textView.setTextColor(colorDorado)
                // 🟢 Sin filtros de color sobre el icono al enfocar de forma activa
                holder.contentWrapper.scaleX = 1.1f
                holder.contentWrapper.scaleY = 1.1f
                lastFocusedPosition = holder.bindingAdapterPosition
            } else {
                holder.contentWrapper.background = null
                holder.textView.setTextColor(colorBlanco)
                // 🟢 Sin filtros de color sobre el icono al perder el foco
                holder.contentWrapper.scaleX = 1.0f
                holder.contentWrapper.scaleY = 1.0f
            }
        }

        holder.container.setOnClickListener {
            if (!esCasTV) {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}