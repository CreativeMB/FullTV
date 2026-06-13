package com.creativem.fulltv.tv

import android.graphics.Color
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
import com.creativem.fulltv.principal.Modelo

class ChannelsAdapter(
    private var channelList: List<Modelo>,
    private val onItemClick: (Modelo) -> Unit,
    private val onFocusChange: (Modelo) -> Unit,
    private val onLongClick: (Modelo) -> Unit // NUEVO: Callback para favoritos
) : RecyclerView.Adapter<ChannelsAdapter.ChannelViewHolder>() {

    // Cache local de IDs favoritos para pintar la estrella rápido
    private var favoriteIds: Set<String> = emptySet()

    private val glideOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.pelifondo)
        .error(R.drawable.pelifondo)
        .dontAnimate()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tv, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val canal = channelList[position]

        holder.txtTitle.text = canal.title
        holder.txtStatus.text = "GRATIS"
        holder.txtBadge.text = "TV"

        // MOSTRAR/OCULTAR ESTRELLA (imgStar debe estar en tu item_tv.xml)
        if (favoriteIds.contains(canal.id)) {
            holder.imgStar.visibility = View.VISIBLE
        } else {
            holder.imgStar.visibility = View.GONE
        }

        Glide.with(holder.itemView.context)
            .load(canal.imageUrl)
            .apply(glideOptions)
            .into(holder.imgMovie)

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                onFocusChange(canal)
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                holder.txtTitle.setTextColor(Color.CYAN)
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                holder.txtTitle.setTextColor(Color.WHITE)
            }
        }

        holder.itemView.setOnClickListener { onItemClick(canal) }

        // DETECTAR CLIC SOSTENIDO (Botón OK del control remoto)
        holder.itemView.setOnLongClickListener {
            onLongClick(canal)
            true
        }
    }

    override fun getItemCount(): Int = channelList.size

    class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMovie: ImageView = view.findViewById(R.id.imgMovie)
        val txtTitle: TextView = view.findViewById(R.id.txtMovieTitle)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtBadge: TextView = view.findViewById(R.id.txtBadge)
        val infoArea: View = view.findViewById(R.id.infoArea)
        val imgStar: ImageView = view.findViewById(R.id.imgStar) // Asegúrate de añadirlo al XML
    }

    // Actualiza tanto la lista como los IDs favoritos
    fun updateList(newList: List<Modelo>, favIds: Set<String>) {
        this.channelList = newList
        this.favoriteIds = favIds
        notifyDataSetChanged()
    }
}