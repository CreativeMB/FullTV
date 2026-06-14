package com.creativem.fulltv.tv

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Modelo

class TvMenuAdapter(
    private val context: Context,
    private var tvList: MutableList<Modelo>,
    private val clickListener: (Modelo) -> Unit
) : RecyclerView.Adapter<TvMenuAdapter.TvViewHolder>() {

    // Variable para saber qué canal está sonando ahora mismo
    private var currentPlayingUrl: String = ""

    fun setCurrentPlayingChannel(url: String) {
        this.currentPlayingUrl = url
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TvViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_menu_tv, parent, false)
        return TvViewHolder(view)
    }

    override fun onBindViewHolder(holder: TvViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val tvItem = tvList[position]
        holder.title.text = tvItem.title

        Glide.with(holder.imageView.context)
            .load(tvItem.imageUrl)
            .placeholder(R.drawable.icono)
            .error(R.drawable.icono)
            .into(holder.imageView)

        // Resaltar visualmente si es el canal que está sonando actualmente
        val isPlaying = tvItem.streamUrl == currentPlayingUrl
        if (isPlaying) {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.colorFocused)) // Color destacado
        } else {
            holder.title.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }

        // CONTROL DE FOCO
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.itemView.setBackgroundColor(
                if (hasFocus) ContextCompat.getColor(context, R.color.cine_dorado)
                else ContextCompat.getColor(context, R.color.colorNotSelected)
            )

        }

        holder.itemView.setOnClickListener {
            clickListener(tvItem)
        }
    }

    override fun getItemCount(): Int = tvList.size

    fun updateData(newTvList: List<Modelo>) {
        tvList.clear()
        tvList.addAll(newTvList)
        notifyDataSetChanged()
    }

    class TvViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imagenPelicula)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
    }
}