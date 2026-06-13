package com.creativem.tvfullurl

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.cineflexurl.modelo.tv

class ChannelAdapter(
    private var channelList: List<tv>,
    private val onDeleteClick: (tv) -> Unit, // Recibe el objeto completo
    private val onItemClick: (tv) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.textViewTitle)
        val deleteButton: Button = view.findViewById(R.id.btnDelete)
        val imageView: ImageView = view.findViewById(R.id.imageViewChannel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channelList[position]
        holder.titleTextView.text = channel.title

        // Cargar imagen con Glide
        if (!channel.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(channel.imageUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }

        // Clic en la fila para reproducir
        holder.itemView.setOnClickListener { onItemClick(channel) }

        // Clic en eliminar (pasa el objeto completo al Fragment para el AlertDialog)
        holder.deleteButton.setOnClickListener {
            onDeleteClick(channel) // CORREGIDO: ahora pasa 'channel' y no 'channel.id'
        }
    }

    // Método para actualizar la lista (usado por el buscador y Firebase)
    fun updateList(newList: List<tv>) {
        this.channelList = newList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = channelList.size
}