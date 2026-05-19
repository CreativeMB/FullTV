package com.creativem.tvfullurl

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SugerenciaAdapter(
    private var pelis: List<TmdbMovie>,
    private val clickListener: (TmdbMovie) -> Unit
) : RecyclerView.Adapter<SugerenciaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val peli = pelis[position]
        holder.text.text = "${peli.title} (${peli.release_date?.take(4) ?: "N/A"})"
        holder.itemView.setOnClickListener { clickListener(peli) }
    }

    override fun getItemCount() = pelis.size

    fun updateData(newList: List<TmdbMovie>) {
        pelis = newList
        notifyDataSetChanged()
    }
}