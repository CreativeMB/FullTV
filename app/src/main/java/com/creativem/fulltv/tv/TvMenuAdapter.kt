//package com.creativem.fulltv.tv
//
//import android.annotation.SuppressLint
//import android.content.Context
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.core.content.ContextCompat
//import androidx.recyclerview.widget.RecyclerView
//import com.bumptech.glide.Glide
//import com.creativem.fulltv.R
//import com.creativem.fulltv.principal.Modelo
//
//class TvMenuAdapter(
//    private val context: Context,
//    private var tvList: MutableList<Modelo>,
//    private val clickListener: (Modelo) -> Unit
//) : RecyclerView.Adapter<TvMenuAdapter.TvViewHolder>() {
//
//    // Variable para saber qué canal está sonando ahora mismo
//    private var currentPlayingUrl: String = ""
//
//    fun setCurrentPlayingChannel(url: String) {
//        this.currentPlayingUrl = url
//        notifyDataSetChanged()
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TvViewHolder {
//        val view = LayoutInflater.from(context).inflate(R.layout.item_menu_tv, parent, false)
//        return TvViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: TvViewHolder, position: Int) {
//        val tvItem = tvList[position]
//        holder.title.text = tvItem.title
//
//        Glide.with(holder.imageView.context)
//            .load(tvItem.imageUrl)
//            .placeholder(R.drawable.icono)
//            .error(R.drawable.icono)
//            .into(holder.imageView)
//
//        // Resaltar visualmente si es el canal que está sonando actualmente
//        val isPlaying = tvItem.streamUrl == currentPlayingUrl
//        if (isPlaying) {
//            holder.title.setTextColor(ContextCompat.getColor(context, R.color.exo_progress_color)) // Color destacado
//        } else {
//            holder.title.setTextColor(ContextCompat.getColor(context, android.R.color.white))
//        }
//
//        // Asegurar que el elemento pueda recibir foco (buena práctica en TV)
//        holder.itemView.isFocusable = true
//
//        // CONTROL DE FOCO Y CENTRADO
//        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
//            val currentPos = holder.bindingAdapterPosition
//
//            if (hasFocus) {
//                // 1. Pone el fondo dorado (tu Drawable)
//                view.background = ContextCompat.getDrawable(context, R.drawable.card_focused_background)
//
//                // 🔥 2. ACTIVA EL MOVIMIENTO DEL TEXTO (MARQUEE)
//                holder.title.isSelected = true
//
//                // 3. LÓGICA DE CENTRADO
//                if (currentPos != RecyclerView.NO_POSITION) {
//                    val parentView = view.parent
//                    if (parentView is RecyclerView) {
//                        val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(view.context) {
//                            override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
//                                // Calcula la diferencia para dejarlo en el puro centro
//                                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
//                            }
//                            override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
//                                return 100f / displayMetrics.densityDpi
//                            }
//                        }
//                        smoothScroller.targetPosition = currentPos
//                        parentView.layoutManager?.startSmoothScroll(smoothScroller)
//                        parentView.requestChildFocus(view, view)
//                    }
//
//                    // Asegurar que el ítem no quede tapado por otros
//                    view.bringToFront()
//                    (view.parent as? ViewGroup)?.requestLayout()
//                    (view.parent as? View)?.invalidate()
//                }
//
//            } else {
//                // 1. Quita el fondo dorado (vuelve a la normalidad)
//                view.background = null
//
//                // 🔥 2. DETIENE EL MOVIMIENTO DEL TEXTO Y LO DEVUELVE AL INICIO
//                holder.title.isSelected = false
//            }
//        }
//
//        holder.itemView.setOnClickListener {
//            clickListener(tvItem)
//        }
//    }
//
//    override fun getItemCount(): Int = tvList.size
//
//    fun updateData(newTvList: List<Modelo>) {
//        tvList.clear()
//        tvList.addAll(newTvList)
//        notifyDataSetChanged()
//    }
//
//    class TvViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val imageView: ImageView = itemView.findViewById(R.id.imagenPelicula)
//        val title: TextView = itemView.findViewById(R.id.tvTitle)
//    }
//}